package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.dto.ChainLinkRequest;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.service.ChainTransactionRecorder;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매 등록·주문 생성의 온체인 결과를 DB 에 연결한다.
 *
 * <p>{@link MaterialChainLinkService} 와 같은 이유로 서버가 트랜잭션을 보내지 않는다 —
 * {@code Marketplace.createListing} 은 판매자 지갑이, {@code createOrder} 는 구매자 지갑이
 * 서명한다.
 *
 * <h2>순서가 강제된다</h2>
 *
 * <pre>
 *   소재 mint     → tokenId            (없으면 createListing 을 부를 인자가 없다)
 *   createListing → chain_listing_id   (없으면 createOrder 를 부를 인자가 없다)
 *   createOrder   → chain_trade_id     (없으면 markShipped·confirmReceipt 를 못 부른다)
 * </pre>
 *
 * <p>그래서 각 단계가 앞 단계의 연결을 확인한다. 확인하지 않으면 화면이 <b>존재하지 않는
 * 온체인 번호로 서명을 시도</b>하고, 사용자는 지갑 팝업까지 간 뒤에 revert 를 본다.
 */
@Service
@RequiredArgsConstructor
public class TradeChainLinkService {

    private final MaterialListingRepository listingRepository;
    private final MaterialTradeRepository tradeRepository;
    private final ChainTransactionRecorder recorder;
    private final MaterialEventRecorder eventRecorder;

    /** 판매 글의 온체인 번호를 연결한다. <b>판매자 본인만</b> 할 수 있다. */
    @Transactional
    public void linkListing(Long sellerId, Long listingId, ChainLinkRequest request) {
        recorder.requireConfigured();

        MaterialListing listing = listingRepository
                .findById(listingId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.LISTING_NOT_FOUND));
        if (!listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(TradeErrorCode.LISTING_NOT_OWNED);
        }
        if (listing.getChainListingId() != null) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_ALREADY_LINKED);
        }

        listing.assignChainListingId(request.onChainId());
        recorder.record(
                sellerId,
                ChainTransactionRecorder.Contract.MARKETPLACE,
                "createListing",
                // 판매 글에는 전용 reference type 이 없다. 스키마의 ENUM 이
                // MATERIAL / TRADE / PROCESSING / PRODUCTION 넷뿐이고, 판매 글은 거래의
                // 앞단이므로 TRADE 로 묶는다. reference_id 로 어느 판매 글인지는 남는다.
                BlockchainReferenceType.TRADE,
                listingId,
                request.txHash());
        eventRecorder.record(
                listing.getMaterialId(),
                MaterialEventType.MARKETPLACE_LISTING_LINKED_ON_CHAIN,
                sellerId,
                MaterialEventReferenceType.MATERIAL_LISTING,
                listingId,
                null,
                listing.getTotalQuantity(),
                "마켓 판매 등록 온체인 확정",
                MaterialEventRecorder.key(
                        MaterialEventType.MARKETPLACE_LISTING_LINKED_ON_CHAIN,
                        listing.getMaterialId(), listingId));
    }

    /** 거래의 온체인 주문 번호를 연결한다. <b>구매자 본인만</b> 할 수 있다. */
    @Transactional
    public void linkTrade(Long buyerId, Long tradeId, ChainLinkRequest request) {
        recorder.requireConfigured();

        MaterialTrade trade = tradeRepository
                .findById(tradeId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_NOT_FOUND));
        if (!trade.getBuyerId().equals(buyerId)) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_PARTICIPANT);
        }
        if (trade.getChainTradeId() != null) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_ALREADY_LINKED);
        }

        trade.assignChainTradeId(request.onChainId());
        recorder.record(
                buyerId,
                ChainTransactionRecorder.Contract.MARKETPLACE,
                "createOrder",
                BlockchainReferenceType.TRADE,
                tradeId,
                request.txHash());
        eventRecorder.record(
                trade.getMaterialId(), MaterialEventType.TRADE_LINKED_ON_CHAIN, buyerId,
                MaterialEventReferenceType.MATERIAL_TRADE, tradeId, null, trade.getQuantity(),
                "소재 거래 온체인 주문 확정",
                MaterialEventRecorder.key(
                        MaterialEventType.TRADE_LINKED_ON_CHAIN,
                        trade.getMaterialId(), tradeId));
    }
}
