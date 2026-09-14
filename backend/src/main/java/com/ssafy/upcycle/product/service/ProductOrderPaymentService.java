package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductOrderPaymentReadyResponse;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.entity.CanceledProductOrder;
import com.ssafy.upcycle.product.entity.CanceledProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.repository.CanceledProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.CanceledProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.payment.KakaoPayProperties;
import com.ssafy.upcycle.trade.payment.PaymentApproval;
import com.ssafy.upcycle.trade.payment.PaymentApproveCommand;
import com.ssafy.upcycle.trade.payment.PaymentCancelCommand;
import com.ssafy.upcycle.trade.payment.PaymentGateway;
import com.ssafy.upcycle.trade.payment.PaymentReady;
import com.ssafy.upcycle.trade.payment.PaymentReadyCommand;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 주문의 결제 준비·승인·취소.
 *
 * <p>소재 거래의 {@code PaymentService} 와 같은 구조다. 카카오페이가 <b>2단계</b>이기 때문이고,
 * 그 사실은 도메인과 무관하다.
 *
 * <pre>
 *   1) ready    서버가 PG 에 준비를 요청해 tid 와 결제창 주소를 받는다   (돈 안 움직임)
 *   2) 사용자가 결제창에서 인증 → pg_token 을 들고 복귀 URL 로 돌아온다
 *   3) approve  서버가 그 토큰으로 승인을 마친다                        (실제 청구)
 * </pre>
 *
 * <p><b>2단계에서 사용자가 이탈할 수 있다.</b> 그 주문은 {@code CREATED} 로 남고 개체는
 * {@code RESERVED} 로 묶인다. 화면이 {@link #cancel} 을 불러 정리해야 하고, 그러지 못한
 * 경우(탭을 그냥 닫는 등)는 {@code ProductOrderExpiryWorker} 가 결제 마감 뒤에 정리한다.
 * 제품 개체는 한 점씩이라 한 건이 묶이면 그 제품이 바로 품절로 보이므로, 소재 거래보다
 * 이 정리가 더 급하다.
 *
 * <p><b>결제 에러 코드는 {@code TradeErrorCode} 의 것을 그대로 쓴다.</b> 결제 준비·승인
 * 실패는 도메인마다 다른 개념이 아니고, 화면도 같은 코드로 같은 안내를 띄운다. 같은 뜻의
 * 코드를 제품용으로 하나 더 만들면 프론트가 두 벌을 다뤄야 한다 — {@code PaymentMethod} 를
 * 두 테이블이 공유하는 것과 같은 판단이다({@code ProductOrder} 문서 참고).
 *
 * <p><b>PG 가 붙었는지 여기서는 모른다.</b> 키가 없으면 {@code MockPaymentGateway} 가 대신
 * 붙지만 금액 대조·상태 전이·재고 복구는 두 경우 모두 똑같이 실행한다. Mock 에서만 통과하는
 * 경로가 생기면 실연동 때 그게 터진다.
 */
@Service
@RequiredArgsConstructor
public class ProductOrderPaymentService {

    private final ProductOrderRepository orders;
    private final ProductOrderItemRepository orderItems;
    private final ProductShippingRepository shippings;
    private final ProductItemRepository items;
    private final ProductModelRepository models;
    private final CanceledProductOrderRepository canceledOrders;
    private final CanceledProductOrderItemRepository canceledOrderItems;
    private final ProductOrderQueryService queryService;
    private final PaymentGateway paymentGateway;
    private final KakaoPayProperties kakaoPayProperties;
    private final Clock clock;

    /**
     * 결제를 준비하고 사용자를 보낼 결제창 주소를 돌려준다. <b>구매자만 할 수 있다.</b>
     *
     * <p>받은 {@code tid} 를 주문에 저장한다. 사용자가 결제창에서 돌아왔을 때 서버가 그 값을
     * 알고 있어야 승인을 요청할 수 있다.
     *
     * <p>결제창을 열었다가 닫고 다시 여는 것은 허용한다. 새 tid 가 이전 것을 덮어쓰고,
     * 승인되지 않은 이전 tid 는 PG 쪽에서 만료된다.
     */
    @Transactional
    public ProductOrderPaymentReadyResponse ready(Long buyerId, Long orderId) {
        ProductOrder order = requireOwnOrder(buyerId, orderId);
        requireStatus(order, ProductOrderStatus.CREATED);

        PaymentReady ready = paymentGateway.ready(new PaymentReadyCommand(
                order.getOrderCode(),
                String.valueOf(order.getBuyerId()),
                itemNameOf(order),
                order.getTotalPrice(),
                kakaoPayProperties.productOrderReturnUrlFor(order.getId())));

        order.preparePayment(ready.tid());
        return new ProductOrderPaymentReadyResponse(
                order.getId(),
                ready.tid(),
                ready.redirectUrl(),
                order.getTotalPrice(),
                paymentGateway.isLive());
    }

    /**
     * 결제창에서 돌아온 뒤 승인한다. <b>이 시점에 실제로 청구된다.</b>
     *
     * <p><b>승인 금액을 주문 금액과 대조한다.</b> PG 응답을 그대로 믿고 기록하면 금액이 어긋난
     * 승인이 정상 결제로 남는다. 대조에 실패하면 예외를 던져 트랜잭션을 롤백한다 — 다만
     * <b>이미 청구는 일어난 뒤</b>라 환불이 필요하다. 실제로 이 경로를 타면 사람이 개입해야
     * 하므로 상태를 바꾸지 않고 실패로 남기는 편이 낫다.
     *
     * <p>{@code CREATED} 가 아닌 주문은 거부한다. 중복 승인을 막는 지점이고, 결제창에서
     * 뒤로 가기로 복귀 URL 을 다시 열면 실제로 두 번 들어온다.
     *
     * <p>개체는 여기서 팔린다. 결제 전까지는 {@code RESERVED} 로 잡혀만 있다.
     */
    @Transactional
    public ProductOrderResponse approve(Long buyerId, Long orderId, String pgToken) {
        ProductOrder order = requireOwnOrder(buyerId, orderId);
        requireStatus(order, ProductOrderStatus.CREATED);

        String tid = order.getPaymentReference();
        if (tid == null || tid.isBlank()) {
            throw new BusinessException(TradeErrorCode.PAYMENT_NOT_PREPARED);
        }

        PaymentApproval approval = paymentGateway.approve(new PaymentApproveCommand(
                tid,
                order.getOrderCode(),
                String.valueOf(order.getBuyerId()),
                pgToken,
                order.getTotalPrice()));

        if (approval.approvedAmount() != order.getTotalPrice()) {
            throw new BusinessException(TradeErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        order.markPaid(
                approval.tid(),
                approval.approvedAmount(),
                approval.approvedAt(),
                paymentGateway.isLive() ? PaymentMethod.KAKAO_PAY : PaymentMethod.MOCK);
        itemsOf(order).forEach(ProductItem::markSold);

        return queryService.findMyOrder(buyerId, order.getId());
    }

    /**
     * 주문을 취소하고 개체를 재고로 되돌린다. <b>구매자만, 발송 전까지만이다.</b>
     *
     * <p><b>순서가 중요하다.</b> 환불을 먼저 요청하고 그다음 주문을 지운다. 환불이 실패하면
     * 예외가 트랜잭션을 롤백해 주문은 {@code PAID} 로 남는다 — <b>돈은 그대로인데 주문만
     * 사라진 상태가 가장 나쁘다.</b>
     *
     * <p><b>상태를 바꾸는 대신 행을 지우고 이력 테이블로 옮긴다.</b> 소재 거래와 다른 점이고
     * 이유는 {@code product_order_item.product_item_id} 의 UNIQUE 다 — 취소된 개체를 다시
     * 팔려면 그 행이 사라져야 한다({@code CanceledProductOrder} 문서 참고).
     */
    @Transactional
    public void cancel(Long buyerId, Long orderId, String reason) {
        ProductOrder order = orders.findByIdForUpdate(orderId)
                .filter(found -> found.getBuyerId().equals(buyerId))
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_ORDER_NOT_FOUND));
        if (!order.isCancelable()) {
            throw new BusinessException(TradeErrorCode.PAYMENT_STATE_INVALID);
        }

        refundIfPaid(order);
        archiveAndDelete(order, reason);
    }

    /**
     * 결제 마감을 넘긴 미결제 주문을 정리한다. {@code ProductOrderExpiryWorker} 가 부른다.
     *
     * <p>사람의 취소와 같은 일을 하지만 구매자 검사가 없다 — 부르는 쪽이 사용자가 아니다.
     * 환불도 하지 않는다: {@code CREATED} 는 승인 전이라 청구 자체가 없다.
     *
     * <p>이미 결제됐거나 사라진 주문은 조용히 넘어간다. 배치가 목록을 읽은 뒤 승인이 들어올
     * 수 있고, 그때 예외를 던지면 정상 결제를 오류로 남긴다.
     */
    @Transactional
    public void expire(Long orderId) {
        orders.findByIdForUpdate(orderId)
                .filter(order -> order.isPaymentExpiredAt(LocalDateTime.now(clock)))
                .ifPresent(order -> archiveAndDelete(order, null));
    }

    /**
     * 주문을 이력으로 옮기고 원본을 지운다. 개체는 재고로 돌아간다.
     *
     * <p>지우는 순서가 FK 를 따른다: 배송지·항목이 주문을 참조하므로 그쪽을 먼저 지운다.
     */
    private void archiveAndDelete(ProductOrder order, String reason) {
        List<ProductOrderItem> orderItemRows = orderItems.findAllByProductOrderId(order.getId());

        CanceledProductOrder archived = canceledOrders.save(
                CanceledProductOrder.from(order, reason, LocalDateTime.now(clock)));
        orderItemRows.forEach(item ->
                canceledOrderItems.save(CanceledProductOrderItem.from(archived.getId(), item)));

        // 잡아 둔 개체를 재고로 되돌린다. 빠뜨리면 취소할 때마다 제품이 한 점씩 사라진다.
        // 결제까지 끝난 주문이면 개체가 SOLD 라, RESERVED 만 받는 release() 로는 부족하다.
        items.findAllById(orderItemRows.stream().map(ProductOrderItem::getProductItemId).toList())
                .forEach(ProductItem::returnToStock);

        shippings.deleteByProductOrderId(order.getId());
        orderItems.deleteAllByProductOrderId(order.getId());
        orders.delete(order);
        orders.flush();
    }

    /**
     * 승인된 결제만 환불한다.
     *
     * <p>{@code CREATED} 는 준비만 했거나 결제창을 닫은 상태라 청구가 없다. tid 가 있어도
     * 환불을 요청하면 PG 가 거절한다.
     */
    private void refundIfPaid(ProductOrder order) {
        if (order.getStatus() != ProductOrderStatus.PAID) {
            return;
        }
        paymentGateway.cancel(
                new PaymentCancelCommand(order.getPaymentReference(), order.getPaymentAmount()));
    }

    /**
     * 결제창에 보일 상품명.
     *
     * <p>제품 이름을 쓴다. 주문번호만 보여주면 사용자가 결제창에서 무엇을 사는지 알 수 없다.
     * 제품을 못 찾으면 주문번호로 대신한다 — 이름 하나 때문에 결제를 막을 이유가 없다.
     */
    private String itemNameOf(ProductOrder order) {
        return orderItems.findAllByProductOrderId(order.getId()).stream()
                .findFirst()
                .flatMap(item -> models.findById(item.getProductModelId()))
                .map(ProductModel::getName)
                .orElseGet(order::getOrderCode);
    }

    private List<ProductItem> itemsOf(ProductOrder order) {
        return items.findAllById(orderItems.findAllByProductOrderId(order.getId()).stream()
                .map(ProductOrderItem::getProductItemId)
                .toList());
    }

    /** 남의 주문을 가리키면 "없음" 으로 답한다 — 403 은 그 주문이 존재한다는 사실을 흘린다. */
    private ProductOrder requireOwnOrder(Long buyerId, Long orderId) {
        return orders.findById(orderId)
                .filter(order -> order.getBuyerId().equals(buyerId))
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_ORDER_NOT_FOUND));
    }

    private void requireStatus(ProductOrder order, ProductOrderStatus expected) {
        if (order.getStatus() != expected) {
            throw new BusinessException(TradeErrorCode.PAYMENT_STATE_INVALID);
        }
    }
}
