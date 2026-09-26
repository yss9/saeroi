package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialStatus;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 내가 보유한 소재 한 건. {@code GET /api/materials/me} 의 항목이다.
 *
 * <p>소재 메타데이터와 <b>내 잔량</b>을 함께 담는다. 화면(My Materials)이 목록에서
 * "무슨 소재를 얼마나 갖고 있나" 를 한 번에 보여줘야 하는데, 소재 정보와 잔량을
 * 따로 조회하면 목록 길이만큼 요청이 늘어난다.
 *
 * <p>{@code totalQuantity} 와 {@code quantity} 를 구분해 담는 것이 중요하다 —
 * 앞은 <b>그 소재의 총 발행량</b>, 뒤는 <b>내 보유량</b>이다. 거래가 일어나면 둘이 달라진다.
 * 이름이 비슷해 헷갈리기 쉬운데 화면에서 섞으면 "1200kg 을 갖고 있다" 는 거짓이 된다.
 *
 * <p>{@code available}·{@code syncedAt} 의 의미는 {@link MaterialBalanceResponse} 와 같다.
 *
 * <p>{@code completedProcessTypes} 는 <b>혈통을 따라 누적된</b> 가공 종류다. 잔량 쿼리가
 * 알 수 없는 값이라 {@code MaterialQueryService} 가 나중에 채운다 —
 * {@link #withCompletedProcessTypes(List)} 참고. 가공을 거치지 않았으면 <b>빈 배열</b>이고
 * {@code null} 이 아니다.
 */
public record MyMaterialResponse(
        Long materialId,
        String materialCode,
        MaterialType materialType,
        MaterialRootOrigin rootOrigin,
        MaterialGenerationType generationType,
        String name,
        String grade,
        MaterialStatus status,
        /** 소재의 총 발행량 (gram) */
        long totalQuantity,
        /** 내 보유량 (gram) */
        long quantity,
        long lockedQuantity,
        long available,
        LocalDateTime syncedAt,
        /** 혈통을 따라 누적된 가공 종류. 가공 전이면 빈 배열이다 */
        List<ProcessType> completedProcessTypes) {

    /**
     * 가공 이력을 얹어 돌려준다.
     *
     * <p>잔량 집계는 QueryDSL 한 방에 끝나지만 가공 이력은 혈통을 세대별로
     * 거슬러 올라가야 나온다. 두 가지를 한 쿼리로 묶으려면 재귀 CTE 가 필요하고,
     * 그러면 깊이 상한을 SQL 안에 숨기게 된다 — 그래서 조회 서비스가 두 번째
     * 단계로 채운다.
     */
    public MyMaterialResponse withCompletedProcessTypes(List<ProcessType> completedProcessTypes) {
        return new MyMaterialResponse(
                materialId, materialCode, materialType, rootOrigin, generationType, name, grade,
                status, totalQuantity, quantity, lockedQuantity, available, syncedAt,
                completedProcessTypes);
    }
}
