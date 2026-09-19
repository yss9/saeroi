package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.processing.entity.ProcessType;
import java.util.List;

/**
 * 가공을 맡길 수 있는 내 소재 한 건.
 *
 * <p>{@code completedProcessTypes} 는 <b>혈통을 따라 누적된</b> 가공 종류다. 화면이 "이 소재는
 * 세척까지 됐으니 이번엔 선별을 맡기자" 를 그 자리에서 판단하는 근거이고, 이 값이 없으면
 * 같은 소재를 보유 소재 목록에서 한 번 더 읽어야 한다. 가공 전이면 <b>빈 배열</b>이고
 * {@code null} 이 아니다.
 *
 * <p>잔량 쿼리가 알 수 없는 값이라 {@link #withCompletedProcessTypes(List)} 로 나중에 채운다.
 */
public record ProcessingMaterialResponse(
        Long materialId,
        String materialCode,
        String name,
        String materialType,
        long quantity,
        long lockedQuantity,
        long availableQuantity,
        List<ProcessType> completedProcessTypes) {

    public ProcessingMaterialResponse withCompletedProcessTypes(
            List<ProcessType> completedProcessTypes) {
        return new ProcessingMaterialResponse(materialId, materialCode, name, materialType,
                quantity, lockedQuantity, availableQuantity, completedProcessTypes);
    }
}
