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
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자의 제품 주문 생성.
 *
 * <p><b>결제 단계가 아직 없다.</b> 제품 주문에는 결제 엔드포인트가 하나도 없어서
 * {@code CREATED} 로 만들면 그 주문을 {@code PAID} 로 옮길 방법이 없다 — 마이페이지에
 * "결제 대기" 로 영원히 남고 {@code journeyUrl} 도 계속 {@code null} 이다. 그래서 결제를
 * 건너뛰고 {@code MOCK} 으로 승인된 것처럼 기록한다. 결제가 붙을 때 이 서비스에서
 * {@link ProductOrder#markPaid} 호출만 떼어 내면 된다.
 *
 * <p>{@code payment_reference} 는 비워 둔다. PG 승인 tid 를 담는 칸인데 PG 를 거치지 않았으므로
 * 넣을 값이 없다 — 가짜 값을 넣으면 나중에 정산에서 대조할 수 없는 행이 된다.
 * {@code payment_method} 가 {@code MOCK} 인 것이 "PG 를 안 거쳤다" 는 표시다.
 *
 * <p><b>소재 거래와 달리 소유권 이전도, 온체인 기록도 없다.</b> 소재는 생산 확정 시점에 이미
 * 소비됐고 소비자는 지갑을 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProductOrderCommandService {

    private final ProductOrderRepository productOrderRepository;
    private final ProductOrderItemRepository productOrderItemRepository;
    private final ProductShippingRepository productShippingRepository;
    private final ProductItemRepository productItemRepository;
    private final ProductModelRepository productModelRepository;
    private final ConsumerAddressRepository consumerAddressRepository;
    private final UserRepository userRepository;
    private final ProductOrderCodeGenerator orderCodeGenerator;
    private final ProductOrderQueryService productOrderQueryService;

    @Transactional
    public ProductOrderResponse place(Long buyerId, ProductOrderCreateRequest request) {
        requireActiveConsumer(buyerId);
        ProductModel model = requirePurchasableModel(request.productModelId());
        ConsumerAddress address = requireOwnAddress(buyerId, request.addressId());
        ProductItem item = takeFromStock(model.getId());

        LocalDateTime now = LocalDateTime.now();
        ProductOrder order = productOrderRepository.saveAndFlush(ProductOrder.place(
                orderCodeGenerator.placeholder(),
                buyerId,
                model.getBrandId(),
                model.getPrice(),
                PaymentMethod.MOCK,
                null));
        order.assignCode(orderCodeGenerator.of(order.getId()));
        order.markPaid(null, model.getPrice(), now);

        // 개체는 잡았다가 곧바로 팔린다. 결제가 붙으면 그 사이에 결제 대기 구간이 생긴다.
        item.reserve();
        item.markSold();

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

    /**
     * 본인 배송지인지 확인한다.
     *
     * <p>남의 배송지를 가리키면 "없음" 으로 답한다. 403 은 "그 id 는 존재하고 남의 것" 이라는
     * 사실을 흘리고, 그 값은 남의 집 주소다.
     */
    private ConsumerAddress requireOwnAddress(Long buyerId, Long addressId) {
        return consumerAddressRepository.findById(addressId)
                .filter(address -> address.isOwnedBy(buyerId))
                .orElseThrow(() -> new BusinessException(
                        ProductErrorCode.PRODUCT_ORDER_ADDRESS_NOT_FOUND));
    }

    /** 조회와 같은 기준으로 판정한다 — 토큰의 역할 클레임이 아니라 DB 의 현재 역할이다. */
    private User requireActiveConsumer(Long buyerId) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(
                        ProductErrorCode.PRODUCT_ORDER_BUYER_NOT_FOUND));
        if (!buyer.isActive()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_BUYER_SUSPENDED);
        }
        if (buyer.getRole() != UserRole.CONSUMER) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_ROLE_NOT_ALLOWED);
        }
        return buyer;
    }
}
