package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.TradeReceiptResponse;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeShipping;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.repository.TradeShippingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자 수령 확인. 2-Track 의 확정 지점이며 소유권이 실제로 넘어가는 유일한 자리다.
 *
 * <p>한 트랜잭션에서 네 테이블이 함께 움직인다. 하나라도 빠지면 상태가 어긋난다.
 *
 * <pre>
 *   material_trade    수령·서명 시각 기록, SHIPPED → RECEIVED → COMPLETED
 *   trade_shipping    delivered_at 기록
 *   material_balance  판매자 차감 / 구매자 증가
 *   material_lock     거래 수량만큼 감소, 다 쓰면 CONSUMED
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class TradeReceiptService {

    private final MaterialTradeRepository materialTradeRepository;
    private final MaterialListingRepository materialListingRepository;
    private final TradeShippingRepository tradeShippingRepository;
    private final MaterialBalanceRepository materialBalanceRepository;
    private final MaterialLockRepository materialLockRepository;
    private final Clock clock;

    /**
     * 구매자가 수령을 확인하고 서명한다.
     *
     * <p>거래 행을 비관적으로 잠그고 시작한다. 상태 검사만으로는 동시에 들어온 두 요청이
     * 같은 SHIPPED 를 읽는 것을 막지 못해 보유량이 두 배로 이전될 수 있다.
     */
    @Transactional
    public TradeReceiptResponse confirmReceipt(Long buyerId, Long tradeId) {
        MaterialTrade trade = materialTradeRepository.findByIdForUpdate(tradeId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_NOT_FOUND));

        requireBuyer(trade, buyerId);
        requireReceivable(trade);

        TradeShipping shipping = tradeShippingRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_SHIPPING_NOT_FOUND));
        MaterialLock lock = activeLockOf(trade);
        MaterialBalance sellerBalance = sellerBalanceOf(trade);
        MaterialBalance buyerBalance = buyerBalanceOf(trade);

        requireEnough(lock.getAmount(), trade.getQuantity(),
                TradeErrorCode.TRADE_LOCK_AMOUNT_INSUFFICIENT);
        requireEnough(sellerBalance.getQuantity(), trade.getQuantity(),
                TradeErrorCode.SELLER_BALANCE_INSUFFICIENT);

        LocalDateTime now = LocalDateTime.now(clock);
        trade.confirmReceipt(now, now);
        shipping.markDelivered(now);
        lock.decreaseBy(trade.getQuantity(), now);
        sellerBalance.decrease(trade.getQuantity());
        buyerBalance.increase(trade.getQuantity());
        trade.complete(now);

        return TradeReceiptResponse.of(trade, sellerBalance, buyerBalance);
    }

    /** 받는 쪽은 구매자다. 판매자가 대신 확인하면 2-Track 이 성립하지 않는다. */
    private void requireBuyer(MaterialTrade trade, Long buyerId) {
        if (!trade.getBuyerId().equals(buyerId)) {
            throw new BusinessException(TradeErrorCode.NOT_TRADE_BUYER);
        }
    }

    /**
     * 상태를 엔티티에 맡기지 않고 여기서 먼저 본다. 엔티티는 {@code IllegalStateException}
     * 을 던지는데 그대로 두면 500 이 나간다.
     */
    private void requireReceivable(MaterialTrade trade) {
        if (trade.getStatus() == TradeStatus.RECEIVED || trade.getStatus() == TradeStatus.COMPLETED) {
            throw new BusinessException(TradeErrorCode.TRADE_ALREADY_RECEIVED);
        }
        if (trade.getStatus() != TradeStatus.SHIPPED) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_RECEIVABLE);
        }
    }

    /**
     * 잠금은 거래가 아니라 판매 등록이 들고 있다. Escrow 를 두지 않아 판매 등록 시 만든
     * MARKETPLACE 잠금 하나가 배송 중까지 유지되고, 이 시점에 거래 수량만큼 소비된다.
     */
    private MaterialLock activeLockOf(MaterialTrade trade) {
        MaterialListing listing = materialListingRepository.findById(trade.getListingId())
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_LISTING_NOT_FOUND));
        MaterialLock lock = materialLockRepository.findById(listing.getMaterialLockId())
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_LOCK_NOT_FOUND));
        if (lock.getStatus() != MaterialLockStatus.ACTIVE) {
            throw new BusinessException(TradeErrorCode.TRADE_LOCK_NOT_ACTIVE);
        }
        return lock;
    }

    /**
     * 판매까지 간 소재에 보유 내역이 없는 것은 데이터가 어긋난 상태다. 조용히 만들어주면
     * 없던 재고가 생긴다.
     */
    private MaterialBalance sellerBalanceOf(MaterialTrade trade) {
        return materialBalanceRepository
                .findByMaterialIdAndOwnerId(trade.getMaterialId(), trade.getSellerId())
                .orElseThrow(() -> new BusinessException(TradeErrorCode.SELLER_BALANCE_NOT_FOUND));
    }

    /**
     * 구매자는 이 소재를 처음 살 수 있으므로 없으면 만든다.
     *
     * <p>지갑 주소는 거래 시점 스냅샷인 {@code buyer_wallet} 을 쓴다. 지갑을 교체했더라도
     * 이 거래는 그 주소로 이루어진 것이라, 현재 활성 지갑을 끌어오면 기록이 어긋난다.
     */
    private MaterialBalance buyerBalanceOf(MaterialTrade trade) {
        return materialBalanceRepository
                .findByMaterialIdAndOwnerId(trade.getMaterialId(), trade.getBuyerId())
                .orElseGet(() -> materialBalanceRepository.save(MaterialBalance.of(
                        trade.getMaterialId(),
                        trade.getBuyerId(),
                        trade.getBuyerWallet(),
                        0L)));
    }

    private void requireEnough(long available, long required, TradeErrorCode errorCode) {
        if (available < required) {
            throw new BusinessException(errorCode);
        }
    }
}
