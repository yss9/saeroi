package com.ssafy.upcycle.trade.dto;

import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 판매 글 한 건. 목록과 상세가 <b>같은 모양</b>을 쓴다.
 *
 * <p>둘을 나누지 않은 이유는 목록 카드가 필요로 하는 값(소재 이름·종류·출처·등급·잔여·단가)이
 * 상세가 필요로 하는 값과 사실상 같기 때문이다. 지금 나누면 필드 하나 추가할 때마다 두 곳을
 * 고쳐야 하고, 상세에 붙을 추적 정보(Journey·Verification)는 어차피 별도 응답이다.
 *
 * <p><b>소재 정보를 펼쳐 담는다.</b> {@code materialId} 만 주면 화면이 카드마다 소재를 다시
 * 불러야 한다 — 목록에서 그게 바로 N+1 이다. 조회 쿼리가 이미 {@code material} 을 조인하므로
 * 여기서 함께 내려보내는 것이 추가 비용이 없다.
 *
 * <p><b>판매 글 자체에는 설명이 없다.</b> {@code materialDescription} 은 소재의 설명이다.
 * {@code material_listing} 에 설명 컬럼을 두지 않은 이유는, 같은 소재를 두 번 올리면 설명이
 * 갈라져 어느 쪽이 그 소재의 사실인지 알 수 없게 되기 때문이다. 소재 정보는 소재가 소유한다.
 *
 * <p>{@code totalQuantity}·{@code availableQuantity} 는 <b>gram 정수</b>다. kg 변환은 화면이
 * 한다 — 서버가 소수로 내려보내면 부동소수 오차가 응답 자체에 박힌다.
 */
public record ListingResponse(
        Long listingId,
        Long chainListingId,
        Long materialId,
        String materialCode,
        String materialName,
        String materialDescription,
        MaterialType materialType,
        MaterialRootOrigin rootOrigin,

        /**
         * 이 소재가 어떻게 생겼는가 — 최초 등록 / 가공 산출물 / 생산 부산물.
         *
         * <p><b>{@code rootOrigin} 과 함께 두 축을 이룬다.</b> "폐기물 + 가공 산출물" 과
         * "폐기물 + 최초 등록" 은 구매자에게 완전히 다른 물건이다 — 앞은 이미 한 번
         * 손이 간 소재고 뒤는 원물이다. 출처만으로는 그 구분이 안 된다 (화면 설계서 B03).
         */
        MaterialGenerationType generationType,

        String grade,
        Long sellerId,
        String sellerName,
        long totalQuantity,
        long availableQuantity,
        long pricePerKg,
        ListingStatus status,
        LocalDateTime createdAt,

        /**
         * 이 소재가 지금까지 거친 가공 종류 — 보유 목록·소재 상세와 같은 값
         * ({@code MaterialProcessHistoryResolver}). 빈 목록이면 가공 전이다.
         *
         * <p>가공을 거쳐도 소재 이름은 바뀌지 않는다. 같은 이름의 판매 글이 여럿일 때
         * 구매자가 가를 수 있는 것이 이 값이다. 조회 쿼리는 모르는 값이라 {@link #of} 는
         * 빈 목록으로 두고, 서비스가 {@link #withCompletedProcessTypes} 로 채운다.
         */
        List<ProcessType> completedProcessTypes) {

    public static ListingResponse of(MaterialListing listing, Material material, User seller) {
        return new ListingResponse(
                listing.getId(),
                listing.getChainListingId(),
                listing.getMaterialId(),
                material.getMaterialCode(),
                material.getName(),
                material.getDescription(),
                material.getMaterialType(),
                material.getRootOrigin(),
                material.getGenerationType(),
                material.getGrade(),
                listing.getSellerId(),
                seller.getName(),
                listing.getTotalQuantity(),
                listing.getAvailableQuantity(),
                listing.getPricePerKg(),
                listing.getStatus(),
                listing.getCreatedAt(),
                List.of());
    }

    public ListingResponse withCompletedProcessTypes(List<ProcessType> processTypes) {
        return new ListingResponse(listingId, chainListingId, materialId, materialCode, materialName,
                materialDescription, materialType, rootOrigin, generationType, grade, sellerId,
                sellerName, totalQuantity, availableQuantity, pricePerKg, status, createdAt,
                List.copyOf(processTypes));
    }
}
