package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductOrderGroupRef;
import com.ssafy.upcycle.product.dto.ProductOrderPaymentReadyResponse;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderGroup;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderGroupRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.payment.KakaoPayProperties;
import com.ssafy.upcycle.trade.payment.PaymentApproval;
import com.ssafy.upcycle.trade.payment.PaymentReady;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 주문의 결제 준비·승인·취소. <b>묶이지 않은 단일 주문만 다룬다.</b>
 *
 * <p>장바구니로 만든 주문은 결제의 단위가 묶음이라 여기로 오면 안 된다
 * ({@link #requireNotGrouped}). 그쪽은 {@code ProductOrderGroupPaymentService} 가 받는다.
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
    private final ProductOrderGroupRepository orderGroups;
    private final ProductOrderItemRepository orderItems;
    private final ProductItemRepository items;
    private final ProductModelRepository models;
    private final ProductOrderArchiver archiver;
    private final ProductOrderGroupPaymentService groupPaymentService;
    private final ProductOrderQueryService queryService;
    private final ProductPaymentCoordinator paymentCoordinator;
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
        requireNotGrouped(order);
        requirePayable(order);

        PaymentReady ready = paymentCoordinator.ready(
                order.getOrderCode(),
                order.getBuyerId(),
                itemNameOf(order),
                order.getTotalPrice(),
                kakaoPayProperties.productOrderReturnUrlFor(order.getId()));

        order.preparePayment(ready.tid());
        return new ProductOrderPaymentReadyResponse(
                order.getId(),
                ready.tid(),
                ready.redirectUrl(),
                order.getTotalPrice(),
                paymentCoordinator.isLive(),
                ProductPaymentDeadline.at(order.getPaymentDeadline(), clock),
                ProductPaymentDeadline.remainingSeconds(order.getPaymentDeadline(), clock));
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
        requireNotGrouped(order);
        // 준비 시점에 유효했어도 결제창에 오래 머물면 승인 때는 지나 있을 수 있다.
        requirePayable(order);

        PaymentApproval approval = paymentCoordinator.approve(
                order.getPaymentReference(),
                order.getOrderCode(),
                order.getBuyerId(),
                pgToken,
                order.getTotalPrice());

        order.markPaid(
                approval.tid(),
                approval.approvedAmount(),
                approval.approvedAt(),
                paymentCoordinator.chargedMethod());
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
        requireNotGrouped(order);
        if (!order.isCancelable()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_NOT_CANCELABLE);
        }

        refundIfPaid(order);
        archiver.archiveAndDelete(order, reason);
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
        ProductOrder order = orders.findByIdForUpdate(orderId)
                .filter(found -> found.isPaymentExpiredAt(LocalDateTime.now(clock)))
                .orElse(null);
        if (order == null) {
            return;
        }
        // 묶음 주문은 주문만 지우면 안 된다. 주문이 0건인 묶음 행이 남아 조회에 잡힌다.
        // 같은 묶음의 주문은 마감 시각이 같아 어차피 함께 만료된다.
        if (order.getOrderGroupId() != null) {
            groupPaymentService.expire(order.getOrderGroupId());
            return;
        }
        archiver.archiveAndDelete(order, null);
    }

    /**
     * 실제로 청구된 결제만 환불한다.
     *
     * <p>{@code CREATED} 는 준비만 했거나 결제창을 닫은 상태라 청구가 없다. tid 가 있어도
     * 환불을 요청하면 PG 가 거절한다.
     *
     * <p><b>{@code MOCK} 으로 결제된 주문도 건너뛴다.</b> 지금 붙어 있는 게이트웨이가 아니라
     * <b>그때 실제로 청구한 쪽</b>을 봐야 한다 — 이 둘은 다를 수 있다. 결제 키가 없던 환경에서
     * 결제한 주문({@code tid} 가 {@code MOCK-...})을 키가 생긴 뒤에 취소하면, 카카오페이에
     * 존재하지 않는 거래의 환불을 요청하게 된다. 그러면 환불이 거절되고 예외가 트랜잭션을
     * 롤백해 <b>그 주문은 영원히 취소할 수 없게 된다.</b> 되돌릴 실제 결제가 없으므로 그냥
     * 넘어가는 것이 맞다.
     */
    private void refundIfPaid(ProductOrder order) {
        if (order.getStatus() != ProductOrderStatus.PAID) {
            return;
        }
        paymentCoordinator.refund(
                order.getPaymentMethod(), order.getPaymentReference(), order.getPaymentAmount());
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

    /**
     * 묶음에 속한 주문은 이 경로로 처리하지 않는다. <b>부분 취소가 없다는 정책을 코드가
     * 강제하는 자리다.</b>
     *
     * <p>막지 않으면 결제가 묶음 단위라는 전제가 세 곳에서 동시에 무너진다.
     *
     * <ul>
     *   <li><b>취소</b> — 그 브랜드 몫만 환불되고 나머지 주문은 살아남는다. 묶음의 금액은
     *       그대로라 나중에 묶음을 취소하면 이미 환불한 금액을 다시 요청한다
     *   <li><b>준비</b> — 묶음의 결제창과 별개로 두 번째 결제창이 열린다
     *   <li><b>승인</b> — 그 주문만 팔리고 묶음은 {@code CREATED} 로 남는다. 묶음이 마감을
     *       넘기면 만료 배치가 <b>이미 결제된 그 주문까지</b> 지운다 — 만료는 승인 전을
     *       전제로 환불하지 않으므로 돈은 청구된 채 주문만 사라진다
     * </ul>
     *
     * <p><b>소유자 검사를 지난 뒤에 부른다.</b> 순서를 바꾸면 남의 주문이 묶음인지 아닌지가
     * 응답으로 새어 나간다.
     *
     * <p>갈 곳을 함께 알려 준다 — 화면이 할 일은 묶음 경로로 옮겨 가는 것 하나뿐이다.
     */
    private void requireNotGrouped(ProductOrder order) {
        Long orderGroupId = order.getOrderGroupId();
        if (orderGroupId == null) {
            return;
        }
        throw new BusinessException(
                ProductErrorCode.PRODUCT_ORDER_BELONGS_TO_GROUP,
                new ProductOrderGroupRef(
                        orderGroupId,
                        orderGroups.findById(orderGroupId)
                                .map(ProductOrderGroup::getGroupCode)
                                .orElse(null)));
    }

    /** 남의 주문을 가리키면 "없음" 으로 답한다 — 403 은 그 주문이 존재한다는 사실을 흘린다. */
    private ProductOrder requireOwnOrder(Long buyerId, Long orderId) {
        return orders.findById(orderId)
                .filter(order -> order.getBuyerId().equals(buyerId))
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_ORDER_NOT_FOUND));
    }

    /**
     * 지금 결제할 수 있는 주문인지. <b>상태와 마감을 함께 본다.</b>
     *
     * <p>마감 검사가 없으면 {@code ProductOrderExpiryWorker} 가 그 주문을 지우기 전까지는
     * 결제가 통과한다. 배치는 60초마다 돌고 밀리면 더 길어져, 마감이 실제로 지켜지는 시각이
     * 주문마다 달라진다. 가공비 결제({@code ProcessingPaymentService.requirePayable})가
     * 이미 같은 모양으로 막고 있다.
     *
     * <p><b>배치를 대신하는 것이 아니다.</b> 잡아 둔 개체를 재고로 되돌리는 일은 검사로는
     * 되지 않는다 — 배치는 그대로 두고, 이 검사는 그보다 먼저 막는다.
     */
    private void requirePayable(ProductOrder order) {
        requireStatus(order, ProductOrderStatus.CREATED);
        if (order.isPaymentExpiredAt(LocalDateTime.now(clock))) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_PAYMENT_EXPIRED);
        }
    }

    private void requireStatus(ProductOrder order, ProductOrderStatus expected) {
        if (order.getStatus() != expected) {
            throw new BusinessException(TradeErrorCode.PAYMENT_STATE_INVALID);
        }
    }
}
