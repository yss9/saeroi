package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductOrderCreateRequest;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductShipping;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자의 제품 주문 생성.
 *
 * <p><b>주문은 결제 전 상태로 만들어진다.</b> 개체는 {@code RESERVED} 로 잡아 두고
 * {@code CREATED} 로 남긴다 — 돈이 오가는 것은 {@code ProductOrderPaymentService} 가 승인을
 * 마칠 때다. 소재 거래와 같은 2단계이고 이유도 같다: 카카오페이는 결제창에서 사용자가
 * 인증해야 청구되므로 그 전에 팔린 것으로 기록할 수 없다.
 *
 * <p><b>그래서 결제 마감이 함께 찍힌다.</b> 사용자가 결제창을 닫으면 그 주문은
 * {@code CREATED} 로 남고 개체는 묶인 채다. 마감을 넘긴 주문은
 * {@code ProductOrderExpiryWorker} 가 정리하고 개체를 재고로 되돌린다. 이 정리가 없으면
 * 결제를 포기할 때마다 제품이 한 점씩 영구히 사라진다.
 *
 * <p><b>소재 거래와 달리 소유권 이전도, 온체인 기록도 없다.</b> 소재는 생산 확정 시점에 이미
 * 소비됐고 소비자는 지갑을 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProductOrderCommandService {

    /**
     * 결제창을 열어 둘 수 있는 시간. 이 시간을 넘긴 미결제 주문은 배치가 정리한다.
     *
     * <p>짧으면 결제 중인 사용자의 주문이 사라지고, 길면 팔 수 있는 제품이 그만큼 오래
     * 묶인다. 제품 개체는 한 점씩이라 한 건이 묶이면 그 제품은 바로 품절로 보인다.
     */
    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);

    private final ProductOrderRepository productOrderRepository;
    private final ProductOrderItemRepository productOrderItemRepository;
    private final ProductShippingRepository productShippingRepository;
    private final ProductItemRepository productItemRepository;
    private final ProductModelRepository productModelRepository;
    private final ProductOrderBuyerPolicy buyerPolicy;
    private final ProductOrderCodeGenerator orderCodeGenerator;
    private final ProductOrderQueryService productOrderQueryService;
    private final Clock clock;

    @Transactional
    public ProductOrderResponse place(Long buyerId, ProductOrderCreateRequest request) {
        buyerPolicy.requireActiveConsumer(buyerId);
        ProductModel model = requirePurchasableModel(request.productModelId());
        ConsumerAddress address = buyerPolicy.requireOwnAddress(buyerId, request.addressId());
        ProductItem item = takeFromStock(model.getId());

        ProductOrder order = productOrderRepository.saveAndFlush(ProductOrder.place(
                orderCodeGenerator.placeholder(),
                buyerId,
                model.getBrandId(),
                model.getPrice(),
                // 희망 수단일 뿐이다. 실제로 청구한 게이트웨이는 승인 시점에 다시 기록한다.
                PaymentMethod.KAKAO_PAY,
                LocalDateTime.now(clock).plus(PAYMENT_WINDOW)));
        order.assignCode(orderCodeGenerator.of(order.getId()));

        // 결제 전까지 다른 사람이 같은 개체를 못 사게 잡아 둔다. 파는 것은 승인 시점이다.
        item.reserve();

        productOrderItemRepository.save(ProductOrderItem.of(
                order.getId(), item.getId(), model.getId(), model.getPrice()));
        productShippingRepository.save(ProductShipping.of(
                order.getId(),
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getZipCode(),
                address.getAddress(),
                address.getAddressDetail()));

        return productOrderQueryService.findMyOrder(buyerId, order.getId());
    }

    /**
     * 재고에서 개체 하나를 잠근 채 가져온다.
     *
     * <p>재고 판정과 개체 선택을 <b>한 번의 잠긴 조회</b>로 합친 것이 핵심이다. "몇 개
     * 남았나" 를 먼저 세고 그다음 개체를 고르면 두 쿼리 사이에 마지막 한 점이 팔려 나간다.
     */
    private ProductItem takeFromStock(Long productModelId) {
        List<ProductItem> available = productItemRepository.lockAvailable(
                productModelId, ProductItemStatus.IN_STOCK, PageRequest.of(0, 1));
        if (available.isEmpty()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_OUT_OF_STOCK);
        }
        return available.get(0);
    }

    /**
     * 주문할 수 있는 상태인지.
     *
     * <p>{@code SOLD_OUT} 은 막지 않는다. 이 값은 재고가 0 이라는 사실의 <b>사본</b>일 뿐이고
     * 실제 판매 가능 여부는 언제나 개체를 세어 판단하기 때문이다({@code ProductModelStatus}
     * 문서 참고). 사본이 갱신되지 않은 사이에 들어온 정상 주문을 막으면, 재고가 있는데도
     * 살 수 없는 제품이 생긴다.
     */
    private ProductModel requirePurchasableModel(Long productModelId) {
        ProductModel model = productModelRepository.findById(productModelId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND));
        if (model.getStatus() == ProductModelStatus.DRAFT
                || model.getStatus() == ProductModelStatus.DISCONTINUED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_MODEL_NOT_ON_SALE);
        }
        return model;
    }

}
