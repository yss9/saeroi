package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.TradeCreateRequest;
import com.ssafy.upcycle.trade.dto.TradeQuoteResponse;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeShipping;
import com.ssafy.upcycle.trade.repository.ListingLockRepository;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.repository.TradeShippingRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매 요청(거래 생성)과 거래 조회.
 *
 * <p>결제는 {@link PaymentService} 가 맡는다. 나눈 이유는 <b>거래 생성과 결제가 서로 다른
 * 시점에 일어나기 때문</b>이다 — 사용자가 구매를 누르면 거래가 {@code CREATED} 로 만들어지고,
 * 결제창을 거쳐 돌아온 뒤에야 {@code PAID} 가 된다. 그 사이에 사용자가 이탈할 수 있고,
 * 그 이탈한 거래를 정리하는 것도 결제 쪽 책임이다.
 *
 * <p><b>수량은 거래 생성 시점에 차감된다.</b> 결제 완료가 아니다. 결제창에 머무는 동안
 * 다른 사람이 같은 수량을 사가면, 결제를 마치고 돌아온 사용자에게 "없는 물건을 샀다" 고
 * 말해야 한다. 대신 이탈한 거래는 취소로 수량을 되돌린다.
 */
@Service
@RequiredArgsConstructor
public class TradeCommandService {

    private final MaterialListingRepository listingRepository;
    private final ListingLockRepository listingLockRepository;
    private final MaterialTradeRepository tradeRepository;
    private final TradeShippingRepository tradeShippingRepository;
    private final UserRepository userRepository;
    private final UserWalletRepository walletRepository;
    private final TradeCodeGenerator tradeCodeGenerator;
    private final TradeResponseAssembler tradeResponseAssembler;

    /**
     * 구매 전 금액과 구매 가능 여부를 미리 계산한다. <b>아무것도 바꾸지 않는다.</b>
     *
     * <p>화면도 같은 계산을 할 수 있지만, 화면이 목록을 읽은 뒤 잔여 수량이 바뀌었을 수 있다.
     * 이 응답의 {@code availableQuantity} 가 지금 시점의 답이다 — 다만 이것도 약속은 아니며
     * 실제 금액은 {@link #create} 가 다시 계산한다.
     */
    @Transactional(readOnly = true)
    public TradeQuoteResponse quote(Long listingId, long quantity, MaterialQuantityUnit unit) {
        MaterialListing listing = listingRepository
                .findById(listingId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.LISTING_NOT_FOUND));

        long quantityGram = toGrams(quantity, unit);
        boolean purchasable = listing.getStatus() == ListingStatus.ON_SALE
                && listing.getAvailableQuantity() >= quantityGram;

        return new TradeQuoteResponse(
                listing.getId(),
                quantityGram,
                listing.getPricePerKg(),
                TradePricing.totalPrice(quantityGram, listing.getPricePerKg()),
                listing.getAvailableQuantity(),
                purchasable);
    }

    /**
     * 구매 요청을 만든다. <b>부분 구매를 지원한다</b> — 판매 글의 전량일 필요가 없다.
     *
     * <p>{@code findByIdForUpdate} 로 판매 글을 행 잠금으로 읽는 것이 핵심이다. 그러지 않으면
     * 잔여 12kg 을 두 사람이 동시에 10kg 씩 사서 잔여가 음수가 된다 — 자세한 것은 그 메서드의
     * 주석에 있다.
     *
     * <p><b>금액은 서버가 계산한다.</b> 요청에 {@code totalPrice} 가 없는 이유이며, 결제 승인
     * 금액은 이 값과 대조된다 ({@link PaymentService#confirm}).
     *
     * <p>지갑 주소는 <b>거래 시점 스냅샷</b>으로 저장한다. 나중에 지갑을 교체해도 이 거래가
     * 어느 주소로 이루어졌는지는 바뀌면 안 된다. 그래서 양쪽 모두 활성 지갑이 있어야 하고,
     * 없으면 거래를 만들지 않는다 — 소유권 이전 대상이 정해지지 않은 거래이기 때문이다.
     *
     * @param buyerId JWT {@code sub} 에서 온 사용자 id. 요청 바디로 받지 않는다
     */
    @Transactional
    public TradeResponse create(Long buyerId, TradeCreateRequest request) {
        requireBuyingCapableUser(buyerId);

        MaterialListing listing = listingLockRepository
                .findByIdForUpdate(request.listingId())
                .orElseThrow(() -> new BusinessException(TradeErrorCode.LISTING_NOT_FOUND));

        if (listing.getStatus() != ListingStatus.ON_SALE) {
            throw new BusinessException(TradeErrorCode.LISTING_NOT_ON_SALE);
        }
        if (listing.getSellerId().equals(buyerId)) {
            throw new BusinessException(TradeErrorCode.TRADE_SELF_PURCHASE);
        }

        long quantity = toGrams(request.quantity(), request.unit());
        if (listing.getAvailableQuantity() < quantity) {
            throw new BusinessException(TradeErrorCode.LISTING_QUANTITY_EXCEEDS_AVAILABLE);
        }

        String sellerWallet = activeWalletAddress(listing.getSellerId());
        String buyerWallet = activeWalletAddress(buyerId);
        long totalPrice = TradePricing.totalPrice(quantity, listing.getPricePerKg());

        listing.reduceAvailable(quantity);

        MaterialTrade trade = tradeRepository.save(MaterialTrade.create(
                tradeCodeGenerator.generate(),
                listing.getId(),
                listing.getMaterialId(),
                listing.getSellerId(),
                buyerId,
                sellerWallet,
                buyerWallet,
                quantity,
                totalPrice,
                request.paymentMethod()));

        // 배송지를 거래와 같은 트랜잭션에서 만든다. 나중에 채우는 구조로 두면 그 사이의
        // 거래는 발송도 수령도 할 수 없는 상태가 된다 — 양쪽 모두 배송 행을 필수로 읽는다.
        tradeShippingRepository.save(TradeShipping.of(
                trade.getId(),
                request.receiverName(),
                request.receiverPhone(),
                request.zipCode(),
                request.address(),
                request.addressDetail()));

        return tradeResponseAssembler.of(trade);
    }

    /**
     * 거래 시점의 활성 지갑 주소.
     *
     * <p>비활성 지갑은 없는 것과 같게 취급한다 ({@code findByUserIdAndActiveTrue}).
     * 계정당 활성 지갑은 하나라는 정책이 있으므로 이 조회는 항상 0 또는 1건이다.
     */
    private String activeWalletAddress(Long userId) {
        return walletRepository
                .findByUserIdAndActiveTrue(userId)
                .map(wallet -> wallet.getWalletAddress())
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_WALLET_NOT_LINKED));
    }

    private long toGrams(long quantity, MaterialQuantityUnit unit) {
        try {
            return unit.toGrams(quantity);
        } catch (ArithmeticException overflow) {
            throw new BusinessException(TradeErrorCode.LISTING_QUANTITY_INVALID);
        }
    }

    /**
     * 구매 자격을 확인한다.
     *
     * <p><b>{@code BRAND} 만이다.</b> 판매는 {@code SUPPLIER}·{@code BRAND} 인데 구매는 더
     * 좁다. 온체인 {@code Marketplace.createOrder} 가 {@code BRAND_ROLE} 을 요구하기 때문이며,
     * 여기서 넓게 열면 DB 거래는 만들어졌는데 체인 주문만 실패한다.
     *
     * <p>{@code UserRole} 에 {@code canBuyMaterial()} 을 추가하지 않고 여기서 판정한다.
     * 이 제약의 근거가 정책이 아니라 <b>컨트랙트의 현재 구현</b>이라 역할 자체의 성질로
     * 올리기에는 이르다. 정책 문서가 같은 제약을 명시하면 그때 열거형으로 옮긴다.
     */
    private void requireBuyingCapableUser(Long buyerId) {
        User user = userRepository
                .findById(buyerId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_BUYER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(TradeErrorCode.TRADE_BUYER_SUSPENDED);
        }
        if (user.getRole() != UserRole.BRAND) {
            throw new BusinessException(TradeErrorCode.TRADE_ROLE_NOT_ALLOWED);
        }
    }
}
