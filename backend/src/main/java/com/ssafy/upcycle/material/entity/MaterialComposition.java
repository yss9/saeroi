package com.ssafy.upcycle.material.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소재 구성 성분 한 줄. "면 60%" 처럼 섬유 종류와 비율 하나를 담는다.
 *
 * <h2>왜 별도 테이블인가</h2>
 *
 * <p>혼방 비율이라 <b>한 소재에 여러 값</b>이 붙는다 (면 60% / 폴리 40%).
 * {@code material} 컬럼 하나로는 표현되지 않는다.
 *
 * <p>{@code JSON} 컬럼으로 두는 쪽이 간단해 보이지만 <b>이 값은 조회 조건이 된다</b> —
 * "면 50% 이상인 소재" 검색과 Upcycle Ratio 집계가 예정돼 있다. MySQL JSON 은 인덱스를
 * 붙이려면 generated column 을 또 만들어야 해서 결국 컬럼이 늘어난다.
 *
 * <h2>합계 100 을 요구하지 않는다</h2>
 *
 * <p>각 행은 {@code 0 < percentage <= 100} 이고 <b>합계는 100 이하</b>면 된다.
 * 정확히 100 을 요구하면 <b>모르는 부분을 지어내게 된다</b> — 수거 소재는 "면 60% 는
 * 확실하고 나머지는 모른다" 가 흔한 상황인데, 그때 "기타 40%" 를 넣게 만들면 없는 정보를
 * 만든 것이다. 빈 부분은 빈 채로 둔다.
 *
 * <p>합계 검사는 <b>DB 가 못 한다</b> — 여러 행에 걸친 조건이라 {@code CHECK} 로 표현되지
 * 않는다. 그래서 {@code MaterialCompositionRegistrar} 가 본다. DB 가 막아 줄 거라
 * 착각하면 직접 INSERT 하는 경로에서 새어 나간다.
 *
 * <h2>fiber_type 이 VARCHAR 인 이유</h2>
 *
 * <p>섬유 종류는 계속 늘어난다(면 · 폴리 · 나일론 · 울 · 레이온 · 텐셀…). ENUM 이면 값
 * 추가마다 DDL 이고, {@code ddl-auto: validate} 환경에서 그건 배포 절차를 부른다.
 * 대신 <b>허용 목록을 코드가 강제한다</b> ({@link MaterialFiberType}) — {@code grade} 를
 * 자유 텍스트로 방치해 생긴 문제를 반복하지 않는다.
 *
 * <p>근거: {@code docs/superpowers/specs/2026-09-07-material-composition-and-origin-reason.md}
 */
@Getter
@Entity
@Table(name = "material_composition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialComposition {

    /** 비율 합계의 상한. 각 행의 상한이기도 하다. */
    public static final BigDecimal MAX_TOTAL_PERCENTAGE = new BigDecimal("100.00");

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 연관을 {@code @ManyToOne} 이 아니라 id 로 들고 있다.
     *
     * <p>{@code MaterialLineage} 가 같은 방식이다 — 구성은 소재 상세를 그릴 때만 필요한데,
     * 양방향 연관을 만들면 소재를 읽는 모든 경로가 이 컬렉션을 끌고 다닐 위험이 생긴다.
     */
    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "fiber_type", nullable = false, length = 50)
    private String fiberType;

    /**
     * 비율. {@code DECIMAL(5,2)} 이라 {@code BigDecimal} 로 받는다.
     *
     * <p>{@code double} 로 두면 60.0 + 40.0 이 100.00000000000001 이 되어 합계 검사가
     * 실패한다. 비율은 사람이 입력하는 소수 두 자리 값이므로 십진수로 다뤄야 한다.
     */
    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    private MaterialComposition(Long materialId, String fiberType, BigDecimal percentage) {
        this.materialId = materialId;
        this.fiberType = fiberType;
        this.percentage = percentage;
    }

    /**
     * 구성 한 줄을 만든다.
     *
     * <p>여기서 막는 것은 <b>행 하나로 판정되는 것</b>뿐이다. 합계와 중복은 여러 행을
     * 함께 봐야 알 수 있어 등록자가 본다.
     */
    public static MaterialComposition of(Long materialId, String fiberType, BigDecimal percentage) {
        if (materialId == null) {
            throw new IllegalArgumentException("Material composition requires a material id");
        }
        String normalized = normalizeFiberType(fiberType);
        if (percentage == null
                || percentage.signum() <= 0
                || percentage.compareTo(MAX_TOTAL_PERCENTAGE) > 0) {
            throw new IllegalArgumentException(
                    "Material composition percentage must be within (0, 100]");
        }
        return new MaterialComposition(materialId, normalized, percentage);
    }

    /**
     * 대문자로 통일한다. {@code uk_material_composition_fiber} 가 {@code (material_id,
     * fiber_type)} UNIQUE 인데 컬레이션이 {@code utf8mb4_0900_ai_ci}(대소문자 무시)라
     * DB 는 {@code cotton} 과 {@code COTTON} 을 같게 보고 막는다. 그런데 <b>저장된 값은
     * 입력한 대로</b> 남아 화면에 섞여 보인다. 들어오는 문을 하나로 만든다.
     */
    private static String normalizeFiberType(String fiberType) {
        if (fiberType == null || fiberType.isBlank()) {
            throw new IllegalArgumentException("Material composition requires a fiber type");
        }
        String normalized = fiberType.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("Material composition fiber type is too long");
        }
        return normalized;
    }
}
