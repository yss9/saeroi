package com.ssafy.upcycle.production.dto;

import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.production.entity.ProductionInput;

/**
 * 투입 한 건.
 *
 * <h2>소재 이름을 함께 담는다</h2>
 *
 * <p>{@code materialId} 만 주면 화면이 <b>"소재 #11"</b> 로 표시하게 된다. 이름을 붙이려면
 * 프론트가 보유 목록에서 찾아야 하는데, <b>확정된 배치의 소재는 소비되어 그 목록에서
 * 사라진다</b> — 이력이 가장 필요한 시점에 이름이 안 나온다.
 *
 * <p>거래 응답이 같은 이유로 {@code materialCode} · {@code materialName} 을 함께 내려준다
 * ({@code TradeResponse}).
 *
 * <p>{@code materialLockId} 를 담는 이유는 다르다 — 투입을 취소하면 이 잠금이 풀린다는 것을
 * 화면이 설명할 수 있어야 하고, 잠금이 풀리지 않은 채 남는 사고를 눈으로 확인할 수 있다.
 *
 * <p>{@code chainLockId} 가 {@code null} 이면 <b>온체인 소각이 아직 보고되지 않은</b> 투입이다.
 * DB 에서는 소비됐는데 체인에는 소재가 남아 있는 상태이고, 화면이 "온체인 소각" 버튼을
 * 언제 보여줄지 이 값으로 정한다.
 */
public record ProductionInputResponse(
        Long productionInputId,
        Long materialId,
        String materialCode,
        String materialName,
        long inputQuantity,
        long productUsedQuantity,
        long reusableScrapQuantity,
        long wasteQuantity,
        String wasteReason,
        Long materialLockId,
        Long chainLockId) {

    /**
     * @param material 투입된 소재. {@code null} 이면 이름 자리가 비는데, 그건 소재 행이
     *     사라졌다는 뜻이라 화면이 "알 수 없음" 으로 보여야 할 상태다. FK 가 있어 정상
     *     경로에서는 일어나지 않는다
     */
    public static ProductionInputResponse of(
            ProductionInput input, Material material, MaterialLock lock) {
        return new ProductionInputResponse(
                input.getId(),
                input.getMaterialId(),
                material == null ? null : material.getMaterialCode(),
                material == null ? null : material.getName(),
                input.getInputQuantity(),
                input.getProductUsedQuantity(),
                input.getReusableScrapQuantity(),
                input.getWasteQuantity(),
                input.getWasteReason(),
                input.getMaterialLockId(),
                lock == null ? null : lock.getChainLockId());
    }
}
