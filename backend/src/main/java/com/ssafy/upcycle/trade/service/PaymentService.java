package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.PaymentReadyResponse;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.payment.KakaoPayProperties;
import com.ssafy.upcycle.trade.payment.PaymentApproval;
import com.ssafy.upcycle.trade.payment.PaymentApproveCommand;
import com.ssafy.upcycle.trade.payment.PaymentCancelCommand;
import com.ssafy.upcycle.trade.payment.PaymentGateway;
import com.ssafy.upcycle.trade.payment.PaymentReady;
import com.ssafy.upcycle.trade.payment.PaymentReadyCommand;
import com.ssafy.upcycle.trade.repository.ListingLockRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 준비·승인·취소.
 *
 * <p><b>결제는 전적으로 오프체인이다.</b> 블록체인에는 금액이 기록되지 않는다 —
 * {@code Marketplace.sol} 의 {@code Order} 구조체에 금액 필드가 없고 {@code createListing} 에
 * 가격 인자가 없다. 체인만 봐서는 대금이 오갔는지 알 수 없고, 그 사실을 아는 곳은 이 테이블뿐이다.
 *
 * <p>카카오페이는 <b>2단계</b>다.
 *
 * <pre>
 *   1) ready    서버가 PG 에 준비를 요청해 tid 와 결제창 주소를 받는다   (돈 안 움직임)
 *   2) 사용자가 결제창에서 인증 → pg_token 을 들고 복귀 URL 로 돌아온다
 *   3) approve  서버가 그 토큰으로 승인을 마친다                        (실제 청구)
 * </pre>
 *
 * <p><b>2단계에서 사용자가 이탈할 수 있다.</b> 그 거래는 {@code CREATED} 로 남고 판매 수량은
 * 묶여 있다. {@link #cancel} 로 정리해야 하며, 화면이 그 경로를 반드시 불러야 한다.
 *
 * <p><b>PG 가 붙었는지 여기서는 모른다.</b> 키가 없으면 {@code MockPaymentGateway} 가 대신
 * 붙지만 금액 대조·상태 전이·수량 복구는 두 경우 모두 이 클래스가 똑같이 실행한다. Mock 에서만
 * 통과하는 경로가 생기면 실연동 때 그게 터진다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final MaterialTradeRepository tradeRepository;

    /** 배치가 남기는 취소 사유. 사람이 적은 사유와 구분되어야 문의 때 원인을 바로 안다. */
    private static final String EXPIRED_REASON = "결제 기한이 지나 자동 취소되었습니다.";
    private final ListingLockRepository listingLockRepository;
    private final MaterialRepository materialRepository;
    private final TradeResponseAssembler tradeResponseAssembler;
    private final PaymentGateway paymentGateway;
    private final KakaoPayProperties kakaoPayProperties;
    private final Clock clock;
    private final MaterialEventRecorder eventRecorder;
    private final TradeNotificationService businessNotifications;

    /**
     * 결제를 준비하고 사용자를 보낼 결제창 주소를 돌려준다. <b>구매자만 할 수 있다.</b>
     *
     * <p>받은 {@code tid} 를 거래에 저장한다. 사용자가 결제창에서 돌아왔을 때 서버가 그 값을
     * 알고 있어야 승인을 요청할 수 있다.
     *
     * <p>이미 결제된 거래를 다시 준비하는 것은 막는다 — 중복 청구로 이어지는 입구다.
     *
     * <p>결제창을 열었다가 닫고 다시 여는 것은 허용한다. 새 tid 가 이전 것을 덮어쓰고, 승인되지
     * 않은 이전 tid 는 PG 쪽에서 만료된다.
     */
    @Transactional
    public PaymentReadyResponse ready(Long buyerId, Long tradeId) {
        MaterialTrade trade = findTrade(tradeId);
        requireBuyer(trade, buyerId);
        requireStatus(trade, TradeStatus.CREATED);

        PaymentReady ready = paymentGateway.ready(new PaymentReadyCommand(
                trade.getTradeCode(),
                String.valueOf(trade.getBuyerId()),
                itemNameOf(trade),
                trade.getTotalPrice(),
                kakaoPayProperties.returnUrlFor(trade.getId())));

        trade.preparePayment(ready.tid());
        return new PaymentReadyResponse(
                trade.getId(),
                ready.tid(),
                ready.redirectUrl(),
                trade.getTotalPrice(),
                paymentGateway.isLive());
    }

    /**
     * 결제창에서 돌아온 뒤 승인한다. <b>이 시점에 실제로 청구된다.</b>
     *
     * <p><b>승인 금액을 거래 금액과 대조한다.</b> PG 응답을 그대로 믿고 기록하면 금액이 어긋난
     * 승인이 정상 결제로 남는다. 대조에 실패하면 예외를 던져 트랜잭션을 롤백한다 — 다만
     * <b>이미 청구는 일어난 뒤</b>라 환불이 필요하다. 실제로 이 경로를 타면 사람이 개입해야
     * 하므로 상태를 바꾸지 않고 실패로 남기는 편이 낫다.
     *
     * <p>{@code CREATED} 가 아닌 거래는 거부한다. 중복 승인을 막는 지점이고, 결제창에서
     * 뒤로 가기로 복귀 URL 을 다시 열면 실제로 두 번 들어온다.
     */
    @Transactional
    public TradeResponse approve(Long buyerId, Long tradeId, String pgToken) {
        MaterialTrade trade = findTrade(tradeId);
        requireBuyer(trade, buyerId);
        requireStatus(trade, TradeStatus.CREATED);

        String tid = trade.getPaymentReference();
        if (tid == null || tid.isBlank()) {
            throw new BusinessException(TradeErrorCode.PAYMENT_NOT_PREPARED);
        }

        PaymentApproval approval = paymentGateway.approve(new PaymentApproveCommand(
                tid,
                trade.getTradeCode(),
                String.valueOf(trade.getBuyerId()),
                pgToken,
                trade.getTotalPrice()));

        if (approval.approvedAmount() != trade.getTotalPrice()) {
            throw new BusinessException(TradeErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 실제로 청구한 게이트웨이를 기록한다. 구매 요청이 보낸 결제 수단은 희망일 뿐이고,
        // 어느 쪽이 붙었는지는 설정이 정한다 (PaymentGatewayConfig).
        trade.markPaid(
                approval.tid(),
                approval.approvedAmount(),
                approval.approvedAt(),
                paymentGateway.isLive() ? PaymentMethod.KAKAO_PAY : PaymentMethod.MOCK);
        eventRecorder.record(
                trade.getMaterialId(), MaterialEventType.TRADE_PAID, buyerId,
                MaterialEventReferenceType.MATERIAL_TRADE, trade.getId(), null,
                trade.getQuantity(), "소재 거래 결제 승인",
                MaterialEventRecorder.key(
                        MaterialEventType.TRADE_PAID, trade.getMaterialId(), trade.getId()));
        businessNotifications.paid(trade);
        return tradeResponseAssembler.of(trade);
    }

    /**
     * 거래를 취소하고 판매 수량을 되돌린다. <b>당사자 양쪽 모두 할 수 있고 발송 전까지만이다.</b>
     *
     * <p>구매자의 취소는 결제창 이탈이나 변심이고, 판매자의 취소는 재고 문제 등으로 못 보내는
     * 경우다. 물건이 나간 뒤의 되돌리기는 분쟁 절차다.
     *
     * <p><b>순서가 중요하다.</b> 환불을 먼저 요청하고 그 다음에 거래 상태를 바꾼다. 환불이
     * 실패하면 예외가 트랜잭션을 롤백해 거래는 {@code PAID} 로 남는다 —
     * <b>돈은 그대로인데 거래만 취소된 상태가 가장 나쁘다.</b>
     *
     * <p><b>수량 복구가 이 메서드의 핵심이다.</b> 거래 생성 시점에 판매 글의 잔여 수량이
     * 줄었으므로 취소하면 그만큼 다시 팔 수 있어야 한다. 빠뜨리면 결제창을 닫은 사용자마다
     * 판매 수량이 영구히 사라진다.
     *
     * <p><b>잠금은 풀지 않는다.</b> 되돌린 수량은 다시 판매 중이 되므로 계속 묶여 있어야
     * 한다 — 여기서 풀면 판매 글에 올라와 있는 수량을 가공이나 생산에도 쓸 수 있게 된다
     * (정책 6.4). 잠금이 {@code RELEASED} 되는 것은 판매 글 자체를 내릴 때뿐이다(정책 7.3).
     *
     * @param reason 사람이 적은 취소 사유. 결제창 이탈처럼 사유가 없는 경로도 있어 선택이다
     */
    @Transactional
    public TradeResponse cancel(Long userId, Long tradeId, String reason) {
        MaterialTrade trade = findTrade(tradeId);
        if (!trade.getSellerId().equals(userId) && !trade.getBuyerId().equals(userId)) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_PARTICIPANT);
        }
        if (trade.getStatus() != TradeStatus.CREATED && trade.getStatus() != TradeStatus.PAID) {
            throw new BusinessException(TradeErrorCode.PAYMENT_STATE_INVALID);
        }

        refundIfPaid(trade);
        trade.cancel(reason, LocalDateTime.now(clock));
        restoreListingQuantity(trade);

        eventRecorder.record(
                trade.getMaterialId(), MaterialEventType.TRADE_CANCELED, userId,
                MaterialEventReferenceType.MATERIAL_TRADE, trade.getId(), null,
                trade.getQuantity(), reason,
                MaterialEventRecorder.key(
                        MaterialEventType.TRADE_CANCELED, trade.getMaterialId(), trade.getId()));
        businessNotifications.canceled(trade, userId);

        return tradeResponseAssembler.of(trade);
    }

    /**
     * 결제 마감을 넘긴 거래를 정리한다. <b>배치 전용 진입점이다.</b>
     *
     * <p>{@link #cancel} 을 그대로 쓸 수 없다. 그쪽은 당사자인지 검사하는데 배치에는
     * 사람이 없고, 시스템 계정을 지어내면 이력의 "누가 취소했는가" 가 거짓이 된다.
     *
     * <p><b>행을 잠그고 마감을 다시 확인한다.</b> 배치가 목록을 읽은 뒤 사용자가 그 사이에
     * 결제를 마쳤을 수 있다. 상태만 보면 그 결제를 취소해 버린다.
     *
     * <p>환불은 하지 않는다 — {@code CREATED} 는 청구가 없는 상태이고, 마감을 넘긴 거래는
     * 정의상 결제되지 않은 것이다.
     *
     * <p><b>체인의 예약은 여기서 못 푼다.</b> {@code cancelOrder} 가 당사자 서명을 요구해
     * 서버가 대신할 수 없다. 서버 수량만 돌아오고 체인에는 예약이 남으므로, 구매자가
     * 거래 상세에서 직접 해제해야 한다.
     */
    @Transactional
    public void expire(Long tradeId) {
        MaterialTrade trade = tradeRepository.findByIdForUpdate(tradeId)
                .filter(found -> found.isPaymentExpiredAt(LocalDateTime.now(clock)))
                .orElse(null);
        if (trade == null) {
            return;
        }

        trade.cancel(EXPIRED_REASON, LocalDateTime.now(clock));
        restoreListingQuantity(trade);

        // 이력의 주체는 구매자다. 그가 결제를 포기해서 생긴 취소이고, 시스템 계정을 만들면
        // 나중에 이력을 읽는 사람이 없는 사용자를 찾게 된다.
        eventRecorder.record(
                trade.getMaterialId(), MaterialEventType.TRADE_CANCELED, trade.getBuyerId(),
                MaterialEventReferenceType.MATERIAL_TRADE, trade.getId(), null,
                trade.getQuantity(), EXPIRED_REASON,
                MaterialEventRecorder.key(
                        MaterialEventType.TRADE_CANCELED, trade.getMaterialId(), trade.getId()));
        businessNotifications.canceled(trade, trade.getBuyerId());
    }

    /**
     * 승인된 결제만 환불한다.
     *
     * <p>{@code CREATED} 는 준비만 했거나 결제창을 닫은 상태라 청구가 없다. tid 가 있어도
     * 환불을 요청하면 PG 가 거절한다.
     */
    private void refundIfPaid(MaterialTrade trade) {
        if (trade.getStatus() != TradeStatus.PAID) {
            return;
        }
        paymentGateway.cancel(
                new PaymentCancelCommand(trade.getPaymentReference(), trade.getPaymentAmount()));
    }

    /**
     * 취소된 수량을 판매 글에 되돌린다.
     *
     * <p>판매 글이 이미 취소된 경우는 건너뛴다 — 이미 닫힌 글의 잔여 수량을 늘리면 팔 수 없는
     * 수량이 표시된다.
     *
     * <p>행 잠금으로 읽는 이유는 복구도 차감과 같은 경합 대상이기 때문이다. 한쪽이 취소로
     * 되돌리는 사이 다른 쪽이 구매로 차감하면 두 갱신 중 하나가 사라진다.
     */
    private void restoreListingQuantity(MaterialTrade trade) {
        Optional<MaterialListing> listing =
                listingLockRepository.findByIdForUpdate(trade.getListingId());
        listing.filter(row -> row.getStatus() != ListingStatus.CANCELED)
                .ifPresent(row -> row.restoreAvailable(trade.getQuantity()));
    }

    /**
     * 결제창에 보일 상품명.
     *
     * <p>소재 이름을 쓴다. 거래 코드만 보여주면 사용자가 결제창에서 무엇을 사는지 알 수 없다.
     * 소재를 못 찾으면 거래 코드로 대신한다 — 이름 하나 때문에 결제를 막을 이유가 없다.
     */
    private String itemNameOf(MaterialTrade trade) {
        return materialRepository
                .findById(trade.getMaterialId())
                .map(Material::getName)
                .orElseGet(trade::getTradeCode);
    }

    private void requireBuyer(MaterialTrade trade, Long buyerId) {
        if (!trade.getBuyerId().equals(buyerId)) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_PARTICIPANT);
        }
    }

    private void requireStatus(MaterialTrade trade, TradeStatus expected) {
        if (trade.getStatus() != expected) {
            throw new BusinessException(TradeErrorCode.PAYMENT_STATE_INVALID);
        }
    }

    private MaterialTrade findTrade(Long tradeId) {
        return tradeRepository
                .findById(tradeId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_NOT_FOUND));
    }
}
