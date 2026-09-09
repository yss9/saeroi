package com.ssafy.upcycle.production.dto;

import com.ssafy.upcycle.production.entity.ProductionInput;

/**
 * 투입 한 건.
 *
 * <p>{@code materialLockId} 를 함께 내려보낸다 — 투입을 취소하면 이 잠금이 풀린다는 것을
 * 화면이 설명할 수 있어야 하고, 잠금이 풀리지 않은 채 남는 사고를 눈으로 확인할 수 있다.
 */
public record ProductionInputResponse(
        Long productionInputId,
        Long materialId,
        long inputQuantity,
        long productUsedQuantity,
        long reusableScrapQuantity,
        long wasteQuantity,
        String wasteReason,
        Long materialLockId) {

    public static ProductionInputResponse from(ProductionInput input) {
        return new ProductionInputResponse(
                input.getId(),
                input.getMaterialId(),
                input.getInputQuantity(),
                input.getProductUsedQuantity(),
                input.getReusableScrapQuantity(),
                input.getWasteQuantity(),
                input.getWasteReason(),
                input.getMaterialLockId());
    }
}
