package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductOrderGroupPaymentReadyResponse;
import com.ssafy.upcycle.product.dto.ProductOrderGroupResponse;
import com.ssafy.upcycle.product.dto.ShippedOrderLine;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderGroup;
import com.ssafy.upcycle.product.entity.ProductOrderGroupStatus;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderGroupRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.payment.KakaoPayProperties;
import com.ssafy.upcycle.trade.payment.PaymentApproval;
import com.ssafy.upcycle.trade.payment.PaymentReady;
import com.ssafy.upcycle.user.service.ConsumerCartService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 묶음의 결제 준비·승인·취소.
 *
 * <h2>결제의 단위가 묶음이다</h2>
 *
 * <p>장바구니에 브랜드가 둘 이상 담기면 주문은 브랜드 수만큼 생긴다. 그때마다 결제창이
 * 뜨면 소비자는 같은 장바구니를 사면서 카드를 세 번 꺼내게 된다. 그래서 {@code tid} 와
 * 금액을 묶음이 들고, 승인되면 <b>속한 주문도 함께</b> {@code PAID} 가 된다.
 *
 * <p>주문에도 결제 컬럼을 적는 이유는 브랜드 정산과 브랜드 주문 목록이 주문 단위로 돌기
 * 때문이다. 주문별 {@code payment_amount} 는 그 브랜드의 정산 금액이고
 * {@code payment_reference} 는 묶음이 받은 하나의 {@code tid} 다.
 *
 * <h2>부분 취소가 없다</h2>
 *
 * <p>결제가 한 번이라 부분 환불 금액을 나눌 근거가 없다. 그래서 취소는 <b>묶음 전체</b>이고,
 * 브랜드 한 곳이 발송하면 아직 발송되지 않은 다른 브랜드 주문도 취소할 수 없게 된다.
 * 그 제약은 사용자에게 보여야 하므로 어느 주문이 발송됐는지를 응답 상세에 싣는다.
 *
 * <p><b>취소를 아예 막지는 않는다.</b> 결제한 뒤 되돌릴 방법이 없는 편이 더 나쁘다.
 *
 * <p>게이트웨이를 부르고 금액을 대조하는 일은 단일 주문과 같은 {@link
 * ProductPaymentCoordinator} 가 맡는다 — 나뉘는 것은 "무엇이 결제의 단위인가" 뿐이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductOrderGroupPaymentService {

    private final ProductOrderGroupRepository groups;
    private final ProductOrderRepository orders;
    private final ProductOrderItemRepository orderItems;
    private final ProductItemRepository items;
    private final ProductModelRepository models;
    private final ProductOrderArchiver archiver;
    private final ProductOrderQueryService queryService;
    private final ProductPaymentCoordinator paymentCoordinator;
    private final ConsumerCartService cartService;
    private final KakaoPayProperties kakaoPayProperties;
    private final Clock clock;

    /**
     * 결제를 준비하고 결제창 주소를 돌려준다. <b>구매자만, 아직 청구되지 않는다.</b>
     *
     * <p>받은 {@code tid} 를 묶음에 저장한다. 사용자가 결제창에서 돌아왔을 때 서버가 그 값을
     * 알고 있어야 승인을 요청할 수 있다.
     */
    @Transactional
    public ProductOrderGroupPaymentReadyResponse ready(Long buyerId, Long orderGroupId) {
        ProductOrderGroup group = requireOwnGroup(buyerId, orderGroupId);
        requirePayable(group);

        PaymentReady ready = paymentCoordinator.ready(
                group.getGroupCode(),
                group.getBuyerId(),
                itemNameOf(group),
                group.getTotalPrice(),
                kakaoPayProperties.productOrderGroupReturnUrlFor(group.getId()));

        group.preparePayment(ready.tid());
        return new ProductOrderGroupPaymentReadyResponse(
                group.getId(),
                ready.tid(),
                ready.redirectUrl(),
                group.getTotalPrice(),
                paymentCoordinator.isLive(),
                ProductPaymentDeadline.at(group.getPaymentDeadline(), clock),
                ProductPaymentDeadline.remainingSeconds(group.getPaymentDeadline(), clock));
    }

    /**
     * 결제창에서 돌아온 뒤 승인한다. <b>이 시점에 실제로 청구된다.</b>
     *
     * <p>잠그고 읽는다. 결제창에서 뒤로 가기로 복귀 URL 을 두 번 열면 승인 요청이 실제로 두
     * 번 들어오고, 상태 검사만으로는 두 요청이 나란히 통과할 수 있다.
     *
     * <p>승인 금액과 묶음 금액의 대조는 {@link ProductPaymentCoordinator} 가 한다. 어긋나면
     * 예외가 트랜잭션을 롤백한다 — <b>이미 청구는 일어난 뒤</b>라 사람이 개입해 환불해야
     * 하지만, 금액이 맞지 않는 승인을 정상 결제로 남기는 것보다 낫다.
     */
    @Transactional
    public ProductOrderGroupResponse approve(Long buyerId, Long orderGroupId, String pgToken) {
        ProductOrderGroup group = groups.findByIdForUpdate(orderGroupId)
                .filter(found -> found.isOwnedBy(buyerId))
                .orElseThrow(() -> new BusinessException(
                        ProductErrorCode.PRODUCT_ORDER_GROUP_NOT_FOUND));
        // 준비 시점에 유효했어도 결제창에 오래 머물면 승인 때는 지나 있을 수 있다.
        requirePayable(group);

        PaymentApproval approval = paymentCoordinator.approve(
                group.getPaymentReference(),
                group.getGroupCode(),
                group.getBuyerId(),
                pgToken,
                group.getTotalPrice());
        PaymentMethod chargedWith = paymentCoordinator.chargedMethod();

        group.markPaid(
                approval.tid(), approval.approvedAmount(), approval.approvedAt(), chargedWith);

        List<ProductOrder> groupOrders = ordersOf(group);
        for (ProductOrder order : groupOrders) {
            // 주문에도 결제를 적는다. 브랜드 정산과 브랜드 주문 목록이 주문 단위로 돌기
            // 때문이고, 금액은 그 브랜드 몫이다 — 합이 승인 금액과 같다.
            order.markPaid(
                    approval.tid(),
                    order.getTotalPrice(),
                    approval.approvedAt(),
                    chargedWith);
        }
        itemsOf(groupOrders).forEach(ProductItem::markSold);

        clearOrderedCartItems(buyerId, groupOrders);
        return queryService.toGroupResponse(group);
    }

    /**
     * 주문에 들어간 장바구니 항목만 비운다.
     *
     * <p>구매 불가로 빠진 항목과 고르지 않은 항목은 남아야 한다 — 사용자가 직접 담은
     * 목록이고, 사지 못한 것까지 사라지면 무엇을 담았는지 다시 찾아야 한다.
     *
     * <p><b>여기서 실패해도 승인을 무르지 않는다.</b> 롤백하면 돈은 청구됐는데 주문이
     * {@code CREATED} 로 남는다 — 그것이 훨씬 나쁘고, 남은 장바구니 항목은 사용자가 직접
     * 지울 수 있다.
     */
    private void clearOrderedCartItems(Long buyerId, List<ProductOrder> groupOrders) {
        try {
            List<Long> orderedModelIds = orderItems
                    .findAllByProductOrderIdInOrderByIdAsc(
                            groupOrders.stream().map(ProductOrder::getId).toList())
                    .stream()
                    .map(ProductOrderItem::getProductModelId)
                    .distinct()
                    .toList();
            cartService.removeOrdered(buyerId, orderedModelIds);
        } catch (RuntimeException failed) {
            log.error("Failed to clear ordered cart items of buyer {}", buyerId, failed);
        }
    }

    /**
     * 묶음 전체를 취소하고 개체를 재고로 되돌린다. <b>구매자만, 발송 전까지만이다.</b>
     *
     * <p><b>한 건이라도 발송됐으면 전부 막는다.</b> 부분 취소가 없어서다. 어느 주문이
     * 발송됐는지 상세에 실어 화면이 사용자에게 설명할 수 있게 한다.
     *
     * <p><b>순서가 중요하다.</b> 환불을 먼저 요청하고 그다음 주문을 지운다. 환불이 실패하면
     * 예외가 트랜잭션을 롤백해 주문이 그대로 남는다 — 돈은 그대로인데 주문만 사라진 상태가
     * 가장 나쁘다. 환불은 묶음이 받은 {@code tid} 로 한 번이다.
     */
    @Transactional
    public void cancel(Long buyerId, Long orderGroupId, String reason) {
        ProductOrderGroup group = groups.findByIdForUpdate(orderGroupId)
                .filter(found -> found.isOwnedBy(buyerId))
                .orElseThrow(() -> new BusinessException(
                        ProductErrorCode.PRODUCT_ORDER_GROUP_NOT_FOUND));

        List<ProductOrder> groupOrders = ordersOf(group);
        requireEveryOrderCancelable(groupOrders);

        refundIfPaid(group);
        groupOrders.forEach(order -> archiver.archiveAndDelete(order, reason));
        groups.delete(group);
    }

    /**
     * 결제 마감을 넘긴 미결제 묶음을 정리한다. {@code ProductOrderExpiryWorker} 가 부른
     * 주문이 묶음에 속한 경우 이 경로로 넘어온다.
     *
     * <p><b>묶음 행까지 지운다.</b> 주문만 지우면 주문이 0건인 묶음이 남아 조회에 잡힌다.
     *
     * <p>사람의 취소와 달리 환불하지 않는다 — {@code CREATED} 는 승인 전이라 청구가 없다.
     * 이미 결제됐거나 사라진 묶음은 조용히 넘어간다: 배치가 목록을 읽은 뒤 승인이 들어올 수
     * 있고, 그때 예외를 던지면 정상 결제를 오류로 남긴다.
     */
    @Transactional
    public void expire(Long orderGroupId) {
        groups.findByIdForUpdate(orderGroupId)
                .filter(group -> group.isPaymentExpiredAt(LocalDateTime.now(clock)))
                .ifPresent(group -> {
                    ordersOf(group).forEach(order -> archiver.archiveAndDelete(order, null));
                    groups.delete(group);
                });
    }

    private void requireEveryOrderCancelable(List<ProductOrder> groupOrders) {
        List<ProductOrder> shipped = groupOrders.stream()
                .filter(order -> !order.isCancelable())
                .toList();
        if (shipped.isEmpty()) {
            return;
        }
        Map<Long, String> brandNames = queryService.loadBrandNames(shipped);
        throw new BusinessException(
                ProductErrorCode.PRODUCT_ORDER_GROUP_NOT_CANCELABLE,
                shipped.stream()
                        .map(order -> new ShippedOrderLine(
                                order.getId(),
                                order.getOrderCode(),
                                brandNames.getOrDefault(order.getBrandId(), "알 수 없는 브랜드")))
                        .toList());
    }

    /**
     * 실제로 청구된 결제만 환불한다. {@code MOCK} 으로 결제된 묶음은 건너뛴다 — 단일 주문과
     * 같은 이유이고 판단도 {@link ProductPaymentCoordinator} 가 한다.
     */
    private void refundIfPaid(ProductOrderGroup group) {
        if (group.getStatus() != ProductOrderGroupStatus.PAID) {
            return;
        }
        paymentCoordinator.refund(
                group.getPaymentMethod(), group.getPaymentReference(), group.getPaymentAmount());
    }

    /**
     * 결제창에 보일 이름.
     *
     * <p>묶음은 상품이 여럿이라 "토트백 외 2건" 처럼 적는다. 묶음 번호만 보여 주면 사용자가
     * 결제창에서 무엇을 사는지 알 수 없다. 이름을 못 찾으면 묶음 번호로 대신한다 — 이름
     * 하나 때문에 결제를 막을 이유가 없다.
     */
    private String itemNameOf(ProductOrderGroup group) {
        List<ProductOrderItem> rows = orderItems.findAllByProductOrderIdInOrderByIdAsc(
                ordersOf(group).stream().map(ProductOrder::getId).toList());
        long kinds = rows.stream().map(ProductOrderItem::getProductModelId).distinct().count();
        return rows.stream()
                .findFirst()
                .flatMap(row -> models.findById(row.getProductModelId()))
                .map(ProductModel::getName)
                .map(name -> kinds > 1 ? name + " 외 " + (kinds - 1) + "건" : name)
                .orElseGet(group::getGroupCode);
    }

    private List<ProductOrder> ordersOf(ProductOrderGroup group) {
        return orders.findAllByOrderGroupIdOrderByIdAsc(group.getId());
    }

    private List<ProductItem> itemsOf(List<ProductOrder> groupOrders) {
        return items.findAllById(orderItems
                .findAllByProductOrderIdInOrderByIdAsc(
                        groupOrders.stream().map(ProductOrder::getId).toList())
                .stream()
                .map(ProductOrderItem::getProductItemId)
                .toList());
    }

    /** 남의 묶음을 가리키면 "없음" 으로 답한다 — 403 은 그 묶음이 존재한다는 사실을 흘린다. */
    private ProductOrderGroup requireOwnGroup(Long buyerId, Long orderGroupId) {
        return groups.findById(orderGroupId)
                .filter(group -> group.isOwnedBy(buyerId))
                .orElseThrow(() -> new BusinessException(
                        ProductErrorCode.PRODUCT_ORDER_GROUP_NOT_FOUND));
    }

    /**
     * 지금 결제할 수 있는 묶음인지. <b>상태와 마감을 함께 본다.</b>
     *
     * <p>단일 주문과 같은 이유다 — 마감 검사가 없으면 만료 배치가 정리하기 전까지 결제가
     * 통과한다. 묶음은 여러 브랜드의 개체를 한꺼번에 잡고 있어 그 시간이 더 크게 아프다.
     *
     * <p>속한 주문의 마감을 따로 보지 않는다. 묶음과 주문이 같은 값을 공유하고
     * ({@code ProductOrderCheckoutService}), 결제의 단위는 묶음이다.
     */
    private void requirePayable(ProductOrderGroup group) {
        requireCreated(group);
        if (group.isPaymentExpiredAt(LocalDateTime.now(clock))) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ORDER_PAYMENT_EXPIRED);
        }
    }

    private void requireCreated(ProductOrderGroup group) {
        if (group.getStatus() != ProductOrderGroupStatus.CREATED) {
            throw new BusinessException(TradeErrorCode.PAYMENT_STATE_INVALID);
        }
    }
}
