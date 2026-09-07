package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.PaymentReadyResponse;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.payment.KakaoPayProperties;
import com.ssafy.upcycle.trade.payment.PaymentApproval;
import com.ssafy.upcycle.trade.payment.PaymentApproveCommand;
import com.ssafy.upcycle.trade.payment.PaymentCancelCommand;
import com.ssafy.upcycle.trade.payment.PaymentGateway;
import com.ssafy.upcycle.trade.payment.PaymentReady;
import com.ssafy.upcycle.trade.payment.PaymentReadyCommand;
import com.ssafy.upcycle.trade.repository.ListingSearchRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
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
    private final ListingSearchRepository listingSearchRepository;
    private final MaterialRepository materialRepository;
    private final TradeResponseAssembler tradeResponseAssembler;
    private final PaymentGateway paymentGateway;
    private final KakaoPayProperties kakaoPayProperties;

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

        trade.markPaid(approval.tid(), approval.approvedAmount(), approval.approvedAt());
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
     */
    @Transactional
    public TradeResponse cancel(Long userId, Long tradeId) {
        MaterialTrade trade = findTrade(tradeId);
        if (!trade.getSellerId().equals(userId) && !trade.getBuyerId().equals(userId)) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_PARTICIPANT);
        }
        if (trade.getStatus() != TradeStatus.CREATED && trade.getStatus() != TradeStatus.PAID) {
            throw new BusinessException(TradeErrorCode.PAYMENT_STATE_INVALID);
        }

        refundIfPaid(trade);
        trade.cancel();
        restoreListingQuantity(trade);

        return tradeResponseAssembler.of(trade);
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
                listingSearchRepository.findByIdForUpdate(trade.getListingId());
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
