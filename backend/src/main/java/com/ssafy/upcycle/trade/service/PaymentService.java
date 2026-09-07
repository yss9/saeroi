package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.PaymentConfirmRequest;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.ListingSearchRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 성공·취소 처리.
 *
 * <p><b>결제는 전적으로 오프체인이다.</b> 블록체인에는 금액이 기록되지 않는다 —
 * {@code Marketplace.sol} 의 {@code Order} 구조체에 금액 필드가 없고 {@code createListing} 에
 * 가격 인자가 없다. 그래서 체인만 봐서는 대금이 오갔는지 알 수 없고, 그 사실을 아는 곳은
 * 이 테이블뿐이다.
 *
 * <p><b>지금은 PG 를 직접 부르지 않는다.</b> 클라이언트가 승인 결과를 알려주는 구조이며,
 * 그래서 {@link #confirm} 이 금액을 반드시 대조한다. KakaoPay 를 붙이면 이 클래스가
 * PG 서버에 직접 승인을 요청하고 {@link PaymentConfirmRequest} 는 {@code pgToken} 하나로
 * 줄어든다 — 바뀌는 것은 이 클래스 안쪽이고, 컨트롤러와 화면의 흐름은 그대로다.
 *
 * <p>정책 v3 는 결제 수단을 KakaoPay 하나로 정했지만 스키마의 ENUM 은
 * {@code ('MOCK', 'KAKAO_PAY')} 다. {@code MOCK} 은 연동 전 시연 경로이며, 승인 번호를
 * 남기는 규칙은 두 수단이 같다 — 그래야 "결제를 거치지 않은 거래" 와 구분된다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final MaterialTradeRepository tradeRepository;
    private final ListingSearchRepository listingSearchRepository;
    private final TradeResponseAssembler tradeResponseAssembler;
    private final Clock clock;

    /**
     * 결제 승인을 기록한다. <b>구매자만 할 수 있다.</b>
     *
     * <p>승인 금액을 거래 금액과 대조한다. 이 검사가 없으면 클라이언트가 1 원을 승인했다고
     * 알려도 거래가 {@code PAID} 가 된다. PG 를 서버에서 직접 부르게 되면 이 대조는
     * "PG 가 알려준 금액"과의 비교로 바뀌지만, 검사 자체는 그대로 남는다.
     *
     * <p>{@code CREATED} 가 아닌 거래는 거부한다 — 이미 결제된 거래에 다시 승인이 오는 것은
     * 중복 결제이고, 배송이 시작된 뒤의 승인은 순서가 어긋난 것이다. 엔티티의
     * {@code markPaid} 도 같은 검사를 하지만, 여기서 먼저 걸러 사용자에게 409 로 답한다
     * (엔티티가 던지는 {@code IllegalStateException} 은 500 이 된다).
     */
    @Transactional
    public TradeResponse confirm(Long buyerId, Long tradeId, PaymentConfirmRequest request) {
        MaterialTrade trade = findTrade(tradeId);
        if (!trade.getBuyerId().equals(buyerId)) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_PARTICIPANT);
        }
        if (trade.getStatus() != TradeStatus.CREATED) {
            throw new BusinessException(TradeErrorCode.PAYMENT_STATE_INVALID);
        }
        if (request.paymentAmount() != trade.getTotalPrice()) {
            throw new BusinessException(TradeErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        trade.markPaid(
                request.paymentReference(), request.paymentAmount(), LocalDateTime.now(clock));
        return tradeResponseAssembler.of(trade);
    }

    /**
     * 거래를 취소하고 판매 수량을 되돌린다. <b>당사자 양쪽 모두 할 수 있다.</b>
     *
     * <p>구매자의 취소는 결제창 이탈이나 변심이고, 판매자의 취소는 재고 문제 등으로 못 보내는
     * 경우다. 둘 다 발송 전까지만 가능하다 — 물건이 나간 뒤의 되돌리기는 분쟁 절차다.
     *
     * <p><b>수량 복구가 이 메서드의 핵심이다.</b> 거래 생성 시점에 판매 글의 잔여 수량이
     * 줄었으므로, 취소하면 그만큼 다시 팔 수 있어야 한다. 이것을 빠뜨리면 결제창을 닫은
     * 사용자마다 판매 수량이 영구히 사라진다.
     *
     * <p>판매 글을 행 잠금으로 읽는 이유는 복구도 차감과 같은 경합 대상이기 때문이다 —
     * 한쪽이 취소로 되돌리는 사이 다른 쪽이 구매로 차감하면 두 갱신 중 하나가 사라진다.
     *
     * <p>{@code PAID} 였던 거래의 환불은 여기서 하지 않는다. PG 쪽 처리이고, 지금은
     * 상태만 남긴다 — 실제 환불 연동이 붙을 자리를 주석으로 표시해 둔다.
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

        // TODO(결제 API 연동): status 가 PAID 였다면 여기서 PG 환불을 요청한다.
        //  실패하면 트랜잭션을 롤백해 거래를 CANCELED 로 두지 않는다 — 돈은 그대로인데
        //  거래만 취소된 상태가 가장 나쁘다.
        trade.cancel();
        restoreListingQuantity(trade);

        return tradeResponseAssembler.of(trade);
    }

    /**
     * 취소된 수량을 판매 글에 되돌린다.
     *
     * <p>판매 글이 이미 취소된 경우는 건너뛴다. 판매자가 글을 내린 뒤(진행 중 거래가 없어야
     * 가능하므로 드물지만) 남아 있던 거래가 취소되는 경로다 — 이미 닫힌 글의 잔여 수량을
     * 늘리면 팔 수 없는 수량이 표시된다.
     *
     * <p>{@code SOLD_OUT} 이었던 글은 {@code restoreAvailable} 이 다시 {@code ON_SALE} 로
     * 되돌린다. 잔여가 생겼으니 실제로 다시 팔 수 있는 상태다.
     */
    private void restoreListingQuantity(MaterialTrade trade) {
        Optional<MaterialListing> listing =
                listingSearchRepository.findByIdForUpdate(trade.getListingId());
        listing.filter(row -> row.getStatus() != ListingStatus.CANCELED)
                .ifPresent(row -> row.restoreAvailable(trade.getQuantity()));
    }

    private MaterialTrade findTrade(Long tradeId) {
        return tradeRepository
                .findById(tradeId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_NOT_FOUND));
    }
}
