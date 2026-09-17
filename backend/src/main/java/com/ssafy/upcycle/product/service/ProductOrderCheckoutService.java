package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.OutOfStockLine;
import com.ssafy.upcycle.product.dto.ProductOrderGroupResponse;
import com.ssafy.upcycle.product.dto.ProductOrderLine;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderGroup;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductShipping;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderGroupRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여러 상품을 한 번에 주문한다. 브랜드 수만큼 주문이 만들어지고 결제는 묶음 하나가 받는다.
 *
 * <h2>브랜드마다 주문을 나눈다</h2>
 *
 * <p>브랜드가 자기 주문만 관리하는 기존 구조를 그대로 두기 위해서다 — 배송도 취소도 정산도
 * 브랜드 단위이고, 브랜드 주문 목록은 지금 모양 그대로 동작해야 한다. 대신 소비자는 결제를
 * 한 번만 한다({@link ProductOrderGroup}).
 *
 * <h2>하나라도 모자라면 전부 실패시킨다</h2>
 *
 * <p>되는 것만 주문하면 사용자가 기대한 것과 다른 금액이 결제되고 그건 분쟁이 된다. 결제
 * 전에 걸러지므로 환불도 필요 없다. 어떤 상품이 모자랐는지는 예외에 실어 보낸다.
 *
 * <p><b>되돌리는 코드를 따로 쓰지 않는다.</b> 전부 한 트랜잭션이라 롤백이 잠금과
 * {@code RESERVED} 전환을 함께 되돌린다. 중간까지 잡아 둔 개체를 손으로 풀어 주는 코드는
 * 그 자체가 빠뜨리기 쉬운 자리다.
 *
 * <h2>담기는 예약이 아니고, 주문이 예약이다</h2>
 *
 * <p>여기서 개체를 {@code RESERVED} 로 잡고 주문은 {@code CREATED} 로 남는다. 파는 것은
 * 결제 승인 시점이다 — 단일 주문과 같은 2단계다.
 *
 * <h2>지금은 입구가 없다</h2>
 *
 * <p>컨트롤러가 붙지 않았다. 장바구니에서 이 경로로 들어오는 입구와 묶음 결제는 다음
 * 작업이며, 그때까지 사용자에게 보이는 것은 아무것도 바뀌지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProductOrderCheckoutService {

    /**
     * 결제창을 열어 둘 수 있는 시간. 단일 주문과 같은 값이다.
     *
     * <p>짧으면 결제 중인 사용자의 주문이 사라지고, 길면 팔 수 있는 제품이 그만큼 오래
     * 묶인다. 묶음은 여러 상품을 한꺼번에 묶으므로 이 시간이 지날 때의 영향이 더 크다.
     */
    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);

    /** 한 번에 주문할 수 있는 상품 종류. 장바구니 상한과 같다. */
    private static final int MAX_LINES = 50;

    /** 한 상품의 최대 수량. 장바구니 상한과 같다. */
    private static final int MAX_QUANTITY = 99;

    private final ProductOrderGroupRepository orderGroups;
    private final ProductOrderRepository orders;
    private final ProductOrderItemRepository orderItems;
    private final ProductShippingRepository shippings;
    private final ProductItemRepository items;
    private final ProductModelRepository models;
    private final ProductOrderBuyerPolicy buyerPolicy;
    private final ProductOrderCodeGenerator codeGenerator;
    private final ProductOrderQueryService queryService;
    private final Clock clock;

    /**
     * 여러 줄을 한 번에 주문한다.
     *
     * @param lines 상품과 수량. 같은 상품이 두 줄로 오면 합쳐서 다룬다
     */
    @Transactional
    public ProductOrderGroupResponse placeAll(
            Long buyerId, List<ProductOrderLine> lines, Long addressId) {
        buyerPolicy.requireActiveConsumer(buyerId);
        ConsumerAddress address = buyerPolicy.requireOwnAddress(buyerId, addressId);
        Map<Long, Integer> quantities = normalize(lines);

        // 잡은 개체를 상품별로 들고 있다가 브랜드별로 다시 묶는다.
        Map<Long, List<ProductItem>> reserved = reserveAll(quantities);

        Map<Long, ProductModel> modelsById = new LinkedHashMap<>();
        for (ProductModel model : models.findAllById(quantities.keySet())) {
            modelsById.put(model.getId(), model);
        }

        long groupTotal = 0L;
        Map<Long, List<Long>> modelIdsByBrand = new LinkedHashMap<>();
        for (Long modelId : quantities.keySet()) {
            ProductModel model = modelsById.get(modelId);
            modelIdsByBrand
                    .computeIfAbsent(model.getBrandId(), brand -> new ArrayList<>())
                    .add(modelId);
            groupTotal += model.getPrice() * quantities.get(modelId);
        }

        ProductOrderGroup group = orderGroups.saveAndFlush(ProductOrderGroup.open(
                codeGenerator.placeholder(),
                buyerId,
                groupTotal,
                // 희망 수단일 뿐이다. 실제로 청구한 게이트웨이는 승인 시점에 다시 기록한다.
                PaymentMethod.KAKAO_PAY,
                LocalDateTime.now(clock).plus(PAYMENT_WINDOW)));
        group.assignCode(codeGenerator.ofGroup(group.getId()));

        List<Long> createdOrderIds = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : modelIdsByBrand.entrySet()) {
            createdOrderIds.add(placeBrandOrder(
                    buyerId, group, entry.getKey(), entry.getValue(),
                    modelsById, quantities, reserved, address));
        }

        List<ProductOrderResponse> orderResponses = createdOrderIds.stream()
                .map(orderId -> queryService.findMyOrder(buyerId, orderId))
                .toList();
        return new ProductOrderGroupResponse(
                group.getId(),
                group.getGroupCode(),
                group.getTotalPrice(),
                group.getCreatedAt(),
                orderResponses);
    }

    /** 브랜드 하나의 주문을 만든다. 금액은 그 브랜드 상품들의 합이다. */
    private Long placeBrandOrder(
            Long buyerId,
            ProductOrderGroup group,
            Long brandId,
            List<Long> modelIds,
            Map<Long, ProductModel> modelsById,
            Map<Long, Integer> quantities,
            Map<Long, List<ProductItem>> reserved,
            ConsumerAddress address) {
        long orderTotal = 0L;
        for (Long modelId : modelIds) {
            orderTotal += modelsById.get(modelId).getPrice() * quantities.get(modelId);
        }

        ProductOrder order = orders.saveAndFlush(ProductOrder.place(
                codeGenerator.placeholder(),
                buyerId,
                brandId,
                orderTotal,
                PaymentMethod.KAKAO_PAY,
                group.getPaymentDeadline()));
        order.assignCode(codeGenerator.of(order.getId()));
        order.assignOrderGroup(group.getId());

        for (Long modelId : modelIds) {
            ProductModel model = modelsById.get(modelId);
            for (ProductItem item : reserved.get(modelId)) {
                // 결제 전까지 다른 사람이 같은 개체를 못 사게 잡아 둔다. 파는 것은 승인 시점이다.
                item.reserve();
                orderItems.save(ProductOrderItem.of(
                        order.getId(), item.getId(), model.getId(), model.getPrice()));
            }
        }

        shippings.save(ProductShipping.of(
                order.getId(),
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getZipCode(),
                address.getAddress(),
                address.getAddressDetail()));
        return order.getId();
    }

    /**
     * 요청한 수량만큼 개체를 잡는다. 하나라도 모자라면 전부 실패한다.
     *
     * <h2>⚠️ 상품 id 오름차순으로 잠근다 — 순서를 바꾸지 말 것</h2>
     *
     * <p>두 구매자가 같은 두 상품을 <b>서로 반대 순서로</b> 잠그면 교착한다. A 가 1번을 잡고
     * 2번을 기다리는 사이 B 가 2번을 잡고 1번을 기다리면 둘 다 영원히 못 나간다. 모든
     * 주문이 <b>같은 순서로</b> 잠그면 그 원이 만들어지지 않는다.
     *
     * <p>{@code TreeMap} 으로 키를 정렬해 둔 것이 그 보장이다. 여기를 {@code HashMap} 이나
     * 입력 순서로 바꾸면 그날부터 교착이 가능해지고, 부하가 걸릴 때만 드물게 나타나 재현하기
     * 어렵다.
     *
     * <p>재고 판정과 개체 선택을 <b>한 번의 잠긴 조회</b>로 합치는 것은 단일 주문과 같다.
     * "몇 개 남았나" 를 먼저 세고 그다음 개체를 고르면 두 질의 사이에 마지막 한 점이 팔린다.
     */
    private Map<Long, List<ProductItem>> reserveAll(Map<Long, Integer> quantities) {
        Map<Long, List<ProductItem>> reserved = new LinkedHashMap<>();
        List<OutOfStockLine> shortages = new ArrayList<>();

        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            Long modelId = line.getKey();
            int wanted = line.getValue();
            ProductModel model = requirePurchasableModel(modelId);

            List<ProductItem> available = items.lockAvailable(
                    modelId, ProductItemStatus.IN_STOCK, PageRequest.of(0, wanted));
            if (available.size() < wanted) {
                shortages.add(new OutOfStockLine(
                        modelId, model.getName(), wanted, available.size()));
                continue;
            }
            reserved.put(modelId, available);
        }

        if (!shortages.isEmpty()) {
            // 모자란 줄을 전부 모아 한 번에 알린다. 하나씩 알리면 사용자가 장바구니를
            // 고치고 다시 눌러야 하는 일이 모자란 상품 수만큼 반복된다.
            throw new BusinessException(
                    ProductErrorCode.PRODUCT_ORDER_ITEMS_OUT_OF_STOCK, shortages);
        }
        return reserved;
    }

    /**
     * 주문할 수 있는 상태인지.
     *
     * <p>{@code SOLD_OUT} 은 막지 않는다. 그 값은 재고가 0 이라는 사실의 <b>사본</b>일 뿐이고
     * 실제 판매 가능 여부는 개체를 세어 판단하기 때문이다. 단일 주문과 같은 판정이다.
     */
    private ProductModel requirePurchasableModel(Long productModelId) {
        ProductModel model = models.findById(productModelId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND));
        if (model.getStatus() == ProductModelStatus.DRAFT
                || model.getStatus() == ProductModelStatus.DISCONTINUED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_MODEL_NOT_ON_SALE);
        }
        return model;
    }

    /**
     * 줄을 상품별로 합치고 <b>상품 id 오름차순으로 정렬한다.</b>
     *
     * <p>정렬이 교착 방지의 근거다 ({@link #reserveAll} 주석). 같은 상품이 두 줄로 들어오면
     * 합친다 — 그대로 두면 같은 상품을 두 번 잠그려 하게 된다.
     */
    private Map<Long, Integer> normalize(List<ProductOrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_NO_ITEMS);
        }
        Map<Long, Integer> quantities = new java.util.TreeMap<>(Comparator.naturalOrder());
        for (ProductOrderLine line : lines) {
            if (line.productModelId() == null) {
                throw new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND);
            }
            if (line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
                throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_QUANTITY_INVALID);
            }
            quantities.merge(line.productModelId(), line.quantity(), Integer::sum);
        }
        if (quantities.size() > MAX_LINES) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_QUANTITY_INVALID);
        }
        for (Integer merged : quantities.values()) {
            if (merged > MAX_QUANTITY) {
                throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_QUANTITY_INVALID);
            }
        }
        return quantities;
    }
}
