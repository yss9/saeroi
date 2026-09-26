package com.ssafy.upcycle.processing.entity;

import com.ssafy.upcycle.material.entity.MaterialType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가공사가 "이 소재를 이 공정으로 처리한다" 고 밝힌 한 줄. 여기에 <b>가격과 속도</b>가 붙는다.
 *
 * <h2>견적이 사라진 자리</h2>
 *
 * <p>예전에는 브랜드가 요청을 올리면 가공사가 금액과 소요일을 적어 견적을 냈다. 그 왕복이
 * 사라지고, 가공사가 <b>미리 한 번</b> 적어 둔 값으로 브랜드가 검색 결과에서 바로 고른다.
 * 그래서 이 표가 가격표 역할을 한다.
 *
 * <p>가공사가 적는 것은 둘뿐이다 — {@code dailyCapacity}(하루 처리량)와
 * {@code pricePerKg}(kg 당 단가). 수량 구간({@code min_quantity}/{@code max_quantity})과
 * 별도의 소요일 표({@code processor_lead_time})는 없앴다. 구간을 나눠 적게 하면 가공사가
 * 표를 채우는 일이 커지는데, 실제로 달라지는 것은 <b>얼마나 걸리는가</b>뿐이고 그건
 * 수량 ÷ 하루 처리량으로 나온다.
 *
 * <p>그래서 "얼마나 받을 수 있는가" 라는 상한도 두지 않는다. 100톤을 맡기면 오래 걸릴 뿐
 * 못 받는 것이 아니고, 너무 오래 걸리면 브랜드가 소요일을 보고 거른다 — 그 판단은
 * 검색 화면이 한다.
 */
@Getter
@Entity
@Table(name = "processor_capability")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessorCapability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "processor_profile_id", nullable = false)
    private Long processorProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 50)
    private MaterialType materialType;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", nullable = false)
    private ProcessType processType;

    /** 하루에 처리할 수 있는 양 (gram/일). 소요일의 분모다. */
    @Column(name = "daily_capacity", nullable = false)
    private long dailyCapacity;

    /** kg 당 단가 (원). 가공비의 유일한 근거다 — 요청 시 서버가 이 값으로 금액을 확정한다. */
    @Column(name = "price_per_kg", nullable = false)
    private long pricePerKg;

    private ProcessorCapability(
            Long processorProfileId,
            MaterialType materialType,
            ProcessType processType,
            long dailyCapacity,
            long pricePerKg) {
        this.processorProfileId = processorProfileId;
        this.materialType = materialType;
        this.processType = processType;
        this.dailyCapacity = dailyCapacity;
        this.pricePerKg = pricePerKg;
    }

    public static ProcessorCapability of(
            Long processorProfileId,
            MaterialType materialType,
            ProcessType processType,
            long dailyCapacity,
            long pricePerKg) {
        return new ProcessorCapability(
                processorProfileId, materialType, processType, dailyCapacity, pricePerKg);
    }

    /** 이 공정에 이만큼을 맡기면 드는 돈. */
    public long feeFor(long quantityGram) {
        return ProcessingEstimate.fee(pricePerKg, quantityGram);
    }

    /** 이 공정에 이만큼을 맡기면 걸리는 날. */
    public int leadDaysFor(long quantityGram) {
        return ProcessingEstimate.leadDays(dailyCapacity, quantityGram);
    }
}
