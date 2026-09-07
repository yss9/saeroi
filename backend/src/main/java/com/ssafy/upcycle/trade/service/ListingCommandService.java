package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.ListingRegisterRequest;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매 등록과 취소. <b>DB 만 채운다</b> — 온체인 {@code createListing} 은 별도 경로다.
 *
 * <p>그래서 등록된 판매 글은 {@code chain_listing_id} 가 {@code NULL} 이다. 이것이
 * "등록됐지만 아직 온체인에 없음" 상태이며, 응답이 그대로 내려보내 화면이 구분할 수 있게 한다
 * ({@code MaterialRegistrationService} 와 같은 구조).
 *
 * <p>조회는 {@link ListingQueryService} 가 맡는다. 읽기는 필터·페이지네이션으로 커지고
 * 쓰기는 온체인 confirm 단계로 커져서, 두 축이 한 파일에 섞이지 않게 나눴다.
 */
@Service
@RequiredArgsConstructor
public class ListingCommandService {

    /**
     * 이 상태의 거래가 하나라도 있으면 판매 글을 내릴 수 없다.
     *
     * <p>{@code COMPLETED}·{@code CANCELED} 만 빠져 있다. 끝난 거래는 판매 글과 무관하지만,
     * 그 외에는 구매자가 대금을 냈거나 물건을 기다리는 중이다 — 판매 글을 내린다고 그 거래가
     * 사라지지 않으므로 순서를 강제한다: <b>거래부터 정리하고 판매 글을 내린다.</b>
     */
    private static final Set<TradeStatus> BLOCKING_TRADE_STATUSES =
            EnumSet.of(
                    TradeStatus.CREATED,
                    TradeStatus.PAID,
                    TradeStatus.SHIPPED,
                    TradeStatus.RECEIVED);

    private final MaterialListingRepository listingRepository;
    private final MaterialTradeRepository tradeRepository;
    private final MaterialRepository materialRepository;
    private final MaterialLockRepository lockRepository;
    private final UserRepository userRepository;
    private final MaterialAvailability materialAvailability;
    private final Clock clock;

    /**
     * 소재를 판매에 올린다.
     *
     * <p><b>저장 순서가 강제되어 있다.</b> {@code material_listing.material_lock_id} 에 FK 가
     * 걸려 있어 잠금이 먼저 INSERT 되어야 하는데, 그 잠금의 {@code reference_id} 는 아직 없는
     * 판매 글의 id 다. 순환이라 잠금을 자리값으로 저장한 뒤 판매 글 id 로 교체한다 —
     * 이 메서드가 {@code @Transactional} 인 것이 그 계약이다. 중간에 실패하면 자리값이 남은
     * 잠금 행도 함께 롤백된다.
     *
     * <p><b>가용 수량으로 검증한다. 보유량이 아니다.</b> 보유량만 보면 같은 30kg 을 판매 글
     * 세 개에 걸 수 있고, 셋 다 팔리면 실제로 넘길 물건이 없다.
     *
     * @param sellerId JWT {@code sub} 에서 온 사용자 id. 요청 바디로 받지 않는다
     */
    @Transactional
    public ListingResponse register(Long sellerId, ListingRegisterRequest request) {
        requireSellingCapableUser(sellerId);

        Material material = materialRepository
                .findById(request.materialId())
                .orElseThrow(() -> new BusinessException(TradeErrorCode.LISTING_MATERIAL_NOT_FOUND));

        long quantity = toGrams(request.quantity(), request.unit());
        requireSellableQuantity(material.getId(), sellerId, quantity);

        MaterialLock lock = lockRepository.save(MaterialLock.create(
                material.getId(),
                sellerId,
                quantity,
                MaterialLockPurpose.MARKETPLACE,
                MaterialLockReferenceType.LISTING,
                MaterialLock.PENDING_REFERENCE_ID,
                null));

        MaterialListing listing = listingRepository.save(MaterialListing.open(
                material.getId(), sellerId, quantity, request.pricePerKg(), lock.getId()));

        lock.assignReference(listing.getId());
        return ListingResponse.of(listing, material);
    }

    /**
     * 판매를 내린다. <b>남은 수량만 회수된다</b> — 이미 팔린 몫은 각자 거래로 살아 있다.
     *
     * <p>진행 중인 거래가 있으면 거부한다. 그 거래들의 수량은 아직 이 판매 글의 잠금 안에
     * 있는데, 판매 글을 내리며 잠금을 풀면 판매자가 배송해야 할 물건을 다른 곳에 또 팔 수 있다.
     *
     * <p>이미 {@code SOLD_OUT} 인 글도 내릴 수 있다. 회수할 잔량이 0 이라 상태만 바뀐다.
     */
    @Transactional
    public ListingResponse cancel(Long sellerId, Long listingId) {
        MaterialListing listing = listingRepository
                .findById(listingId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.LISTING_NOT_FOUND));
        if (!listing.getSellerId().equals(sellerId)) {
            throw new BusinessException(TradeErrorCode.LISTING_NOT_OWNED);
        }
        if (listing.getStatus() == ListingStatus.CANCELED) {
            throw new BusinessException(TradeErrorCode.LISTING_NOT_ON_SALE);
        }
        requireNoOngoingTrade(listingId);

        long released = listing.cancel();
        releaseLock(listing, released);

        Material material = materialRepository
                .findById(listing.getMaterialId())
                .orElseThrow(() -> new BusinessException(TradeErrorCode.LISTING_MATERIAL_NOT_FOUND));
        return ListingResponse.of(listing, material);
    }

    /**
     * 잠금에서 회수분을 뺀다.
     *
     * <p>잠금이 이미 {@code ACTIVE} 가 아닐 수 있다 — 전량이 팔리고 모두 수령 확인까지 끝나면
     * 수령 처리가 잠금을 {@code CONSUMED} 로 넘긴다. 그 상태의 판매 글을 내리는 것은 정상이며
     * 뺄 것이 없다.
     *
     * <p>회수분이 잠금 전체와 같으면 {@code release} 를, 일부면 {@code decreaseBy} 를 쓴다.
     * 둘을 구분하는 이유는 남는 상태가 다르기 때문이다 — 취소로 풀린 잠금은
     * {@code RELEASED} 이지 {@code CONSUMED} 가 아니다. {@code decreaseBy} 는 전량이면
     * {@code CONSUMED} 로 넘기므로 그대로 쓰면 "소비됨" 이력이 거짓이 된다.
     */
    private void releaseLock(MaterialListing listing, long released) {
        if (released <= 0) {
            return;
        }
        MaterialLock lock = lockRepository
                .findById(listing.getMaterialLockId())
                .orElseThrow(() -> new BusinessException(TradeErrorCode.LISTING_LOCK_NOT_FOUND));
        if (lock.getStatus() != MaterialLockStatus.ACTIVE) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (lock.getAmount() <= released) {
            lock.release(now);
        } else {
            lock.decreaseBy(released, now);
        }
    }

    private void requireNoOngoingTrade(Long listingId) {
        boolean ongoing = tradeRepository.findAllByListingId(listingId).stream()
                .map(MaterialTrade::getStatus)
                .anyMatch(BLOCKING_TRADE_STATUSES::contains);
        if (ongoing) {
            throw new BusinessException(TradeErrorCode.LISTING_HAS_ONGOING_TRADE);
        }
    }

    private void requireSellableQuantity(Long materialId, Long sellerId, long quantity) {
        if (!materialAvailability.owns(materialId, sellerId)) {
            throw new BusinessException(TradeErrorCode.LISTING_MATERIAL_NOT_OWNED);
        }
        if (materialAvailability.availableQuantity(materialId, sellerId) < quantity) {
            throw new BusinessException(TradeErrorCode.LISTING_QUANTITY_EXCEEDS_AVAILABLE);
        }
    }

    /**
     * 요청 단위를 gram 정수로 환산한다. {@code long} 범위를 넘는 곱셈은 조용히 음수로 감기고,
     * 그 값이 {@code total_quantity} 에 들어가면 CHECK 위반으로 설명할 수 없는 500 이 된다.
     */
    private long toGrams(long quantity, MaterialQuantityUnit unit) {
        try {
            return unit.toGrams(quantity);
        } catch (ArithmeticException overflow) {
            throw new BusinessException(TradeErrorCode.LISTING_QUANTITY_INVALID);
        }
    }

    /**
     * 판매 자격을 확인한다.
     *
     * <p>JWT 의 {@code roles} 클레임을 믿지 않고 DB 를 다시 읽는다 — 토큰은 발급 시점의
     * 스냅샷이라 정지된 계정이 만료 전까지 계속 판매를 올릴 수 있다.
     *
     * <p>자격은 {@code canRegisterMaterial()} 을 그대로 쓴다 ({@code SUPPLIER}·{@code BRAND}).
     * 온체인 {@code Marketplace.createListing} 이 요구하는 역할과 같다 —
     * {@code SUPPLIER_ROLE || BRAND_ROLE}. 두 곳이 어긋나면 DB 에는 올라갔는데 체인 등록만
     * 실패하는 판매 글이 생긴다.
     */
    private void requireSellingCapableUser(Long sellerId) {
        User user = userRepository
                .findById(sellerId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.LISTING_SELLER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(TradeErrorCode.LISTING_SELLER_SUSPENDED);
        }
        if (!user.getRole().canRegisterMaterial()) {
            throw new BusinessException(TradeErrorCode.LISTING_ROLE_NOT_ALLOWED);
        }
    }
}
