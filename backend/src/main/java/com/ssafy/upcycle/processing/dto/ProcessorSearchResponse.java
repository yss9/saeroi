package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.processing.entity.ProcessType;
import java.util.List;

/**
 * 가공사 검색 결과.
 *
 * <p>견적이 사라지면서 <b>여기에 금액과 기간이 실린다.</b> 브랜드는 이 목록만 보고 고르고,
 * 고른 값이 그대로 청구된다.
 */
public record ProcessorSearchResponse(List<Item> items, long total, int page, int size) {

    /**
     * 업체 한 곳.
     *
     * @param dailyCapacity 요청한 공정 중 <b>가장 느린</b> 것의 하루 처리량 (gram/일).
     *     필터 없이 둘러볼 때는 등록된 조합 중 가장 큰 값이다
     * @param pricePerKg 요청한 공정 <b>전부를 합한</b> kg 당 단가 (원).
     *     필터 없이 둘러볼 때는 등록된 조합 중 가장 싼 값이다
     * @param estimatedPrice 이 수량을 맡겼을 때 실제로 청구될 금액 (원).
     *     <b>수량을 주지 않고 둘러보면 {@code null}</b> 이다 — 0 으로 채우면 "공짜" 가 된다
     * @param estimatedDays 이 수량을 맡겼을 때의 총 소요일. 공정이 여럿이면 합이다
     *     (동시에 하는 것이 아니라 차례로 한다). 수량이 없으면 {@code null}
     * @param estimatedLeadTimes 공정별 소요일. 수량이 없으면 빈 목록
     */
    public record Item(
            Long processorId,
            String name,
            String region,
            long dailyCapacity,
            long pricePerKg,
            Long estimatedPrice,
            Integer estimatedDays,
            List<EstimatedLeadTime> estimatedLeadTimes) {
    }

    public record EstimatedLeadTime(ProcessType processType, int leadDays) {
    }
}
