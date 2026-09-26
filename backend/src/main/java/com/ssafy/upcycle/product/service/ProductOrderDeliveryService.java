package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.dto.ProductOrderShipRequest;
import com.ssafy.upcycle.product.dto.ProductOrderShipResponse;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.entity.ProductShipping;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 제품 주문의 발송과 수령 확인.
 *
 * <pre>
 *   PAID ──(브랜드 발송)──▶ SHIPPED ──(소비자 수령 확인)──▶ DELIVERED
 * </pre>
 *
 * <p><b>두 단계를 서로 다른 사람이 누른다.</b> 소재 거래와 같은 2-Track 이고 이유도 같다 —
 * 보냈다는 사실만으로는 받았다는 보장이 없다. 브랜드의 발송 처리로 주문이 완료되게 하면,
 * 물건이 오지 않은 주문도 완료로 남는다.
 *
 * <p><b>다만 소재 거래와 달리 서명도 온체인 기록도 없다.</b> 소비자는 지갑을 쓰지 않으므로
 * 수령 확인 행위 자체가 유일한 근거다.
 *
 * <p><b>개체 상태는 여기서 바뀌지 않는다.</b> 결제 승인 시점에 이미 {@code SOLD} 다.
 * 발송과 수령은 그 물건이 어디까지 갔는지를 적을 뿐, 팔렸는지 여부를 다시 정하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProductOrderDeliveryService {

    private final ProductOrderRepository orders;
    private final ProductShippingRepository shippings;
    private final ProductOrderQueryService queryService;
    private final Clock clock;

    /**
     * 브랜드가 제품을 발송한다. 2-Track 중 보낸 측의 기록이다.
     *
     * <p>주문 취소가 막히는 지점이기도 하다 — {@code ProductOrder#isCancelable} 이
     * {@code SHIPPED} 를 받지 않는다. 물건이 나갔는데 주문만 없애면 그 개체가 어디에도 속하지
     * 않게 된다.
     */
    @Transactional
    public ProductOrderShipResponse ship(
            Long brandId, Long orderId, ProductOrderShipRequest request) {
        ProductOrder order = findOrder(orderId);
        requireSellingBrand(order, brandId);
        requireShippable(order);
        requireCarrierWithTrackingNumber(request);

        ProductShipping shipping = shippings.findByProductOrderId(orderId)
                .orElseThrow(() -> new BusinessException(
                        ProductErrorCode.PRODUCT_ORDER_SHIPPING_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        order.ship(now);
        shipping.registerShipment(request.carrier(), request.trackingNumber(), now);

        return ProductOrderShipResponse.of(order, shipping);
    }

    /**
     * 소비자가 수령을 확인한다. <b>주문이 끝나는 지점이다.</b>
     *
     * <p>구매자 본인만 할 수 있다. 브랜드가 대신 눌러 완료시킬 수 있으면 2-Track 을 둔 의미가
     * 없어진다 — 받았다고 말할 수 있는 사람은 받은 사람뿐이다.
     */
    @Transactional
    public ProductOrderResponse confirmReceipt(Long buyerId, Long orderId) {
        ProductOrder order = findOrder(orderId);
        requireBuyer(order, buyerId);
        if (order.getStatus() != ProductOrderStatus.SHIPPED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_NOT_DELIVERABLE);
        }

        order.confirmDelivery(LocalDateTime.now(clock));
        return queryService.findMyOrder(buyerId, orderId);
    }

    /**
     * 그 주문을 판 브랜드인가.
     *
     * <p>역할 클레임이 아니라 주문이 가진 {@code brand_id} 로 판정한다. BRAND 라는 것만으로는
     * 부족하다 — 다른 브랜드의 주문을 발송 처리할 수 있게 된다.
     */
    private void requireSellingBrand(ProductOrder order, Long brandId) {
        if (!order.getBrandId().equals(brandId)) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_NOT_SELLER);
        }
    }

    /** 남의 주문은 "없음" 으로 답한다 — 403 은 그 주문이 존재한다는 사실을 흘린다. */
    private void requireBuyer(ProductOrder order, Long buyerId) {
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_NOT_FOUND);
        }
    }

    /**
     * 상태를 엔티티에 맡기지 않고 여기서 먼저 본다. 엔티티는 {@code IllegalStateException} 을
     * 던지는데 그대로 두면 500 이 나간다. 엔티티 쪽 검사는 최후 방어선으로 남긴다.
     */
    private void requireShippable(ProductOrder order) {
        if (order.getStatus() == ProductOrderStatus.SHIPPED
                || order.getStatus() == ProductOrderStatus.DELIVERED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_ALREADY_SHIPPED);
        }
        if (order.getStatus() != ProductOrderStatus.PAID) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_NOT_SHIPPABLE);
        }
    }

    /** 택배사 없는 운송장 번호는 조회할 수 없어 기록해도 쓸모가 없다. */
    private void requireCarrierWithTrackingNumber(ProductOrderShipRequest request) {
        if (StringUtils.hasText(request.trackingNumber())
                && !StringUtils.hasText(request.carrier())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_TRACKING_WITHOUT_CARRIER);
        }
    }

    private ProductOrder findOrder(Long orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_ORDER_NOT_FOUND));
    }
}
