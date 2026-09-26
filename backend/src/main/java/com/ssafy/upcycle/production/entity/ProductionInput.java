package com.ssafy.upcycle.production.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배치에 투입된 소재 한 건. <b>제품과 소재를 잇는 실제 고리다.</b>
 *
 * <p>"이 제품에 어떤 폐데님이 얼마나 들어갔나" 의 답이 이 행이며, QR 이력이 소재까지
 * 거슬러 올라갈 때 지나는 곳이다. {@code (production_batch_id, material_id)} 가 UNIQUE 라
 * 같은 소재를 한 배치에 두 번 넣을 수 없다.
 *
 * <h2>투입량이 셋으로 갈린다</h2>
 *
 * <pre>
 *   input_quantity = product_used_quantity   제품에 실제로 들어간 양
 *                  + reusable_scrap_quantity 자투리 (새 소재로 되살아난다)
 *                  + waste_quantity          폐기
 * </pre>
 *
 * <p><b>이 식을 행마다 검사한다.</b> ERD 가 못 박은 규칙이고 DB 에도
 * {@code chk_production_input_quantities} 로 들어가 있다. 배치 합계로만 보면 한 소재를
 * 과다 입력하고 다른 소재를 과소 입력해도 통과하는데, 그러면 <b>개별 소재의 이력이
 * 거짓이 된다</b> — 합계는 맞으니 아무도 눈치채지 못한다.
 *
 * <p>업사이클 비율에 들어가는 것은 {@code product_used_quantity} 뿐이다. 자투리와 폐기는
 * 제품에 남아 있지 않으므로 "이 제품의 몇 %가 업사이클인가" 에 셀 수 없다.
 *
 * <h2>Lock 을 가리킨다</h2>
 *
 * <p>{@code material_lock_id} 가 {@code NOT NULL} 인 것이 이 엔티티의 안전장치다. 잠그지
 * 않은 소재를 투입할 수 없다는 뜻이며, 확정 시점에 그 잠금을 {@code CONSUMED} 로 바꾸는
 * 대상도 이 값으로 찾는다. 잠금 없이 투입을 허용하면 같은 소재를 두 배치가 동시에 쓰고
 * 잔량이 음수가 된다.
 */
@Getter
@Entity
@Table(name = "production_input", uniqueConstraints = @UniqueConstraint(
        name = "uk_production_input_batch_material",
        columnNames = {"production_batch_id", "material_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionInput {

    private static final int WASTE_REASON_MAX_LENGTH = 200;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "production_batch_id", nullable = false)
    private Long productionBatchId;

    /** 투입된 소재. material FK 다. */
    @Column(name = "material_id", nullable = false)
    private Long materialId;

    /** 투입량 (g). 아래 셋의 합이다. */
    @Column(name = "input_quantity", nullable = false)
    private long inputQuantity;

    /** 제품에 실제로 들어간 양 (g). 업사이클 비율에 세는 것은 이것뿐이다. */
    @Column(name = "product_used_quantity", nullable = false)
    private long productUsedQuantity;

    /** 재사용 가능한 자투리 (g). 확정 시 새 소재로 되살아난다. */
    @Column(name = "reusable_scrap_quantity", nullable = false)
    private long reusableScrapQuantity;

    /** 폐기량 (g). */
    @Column(name = "waste_quantity", nullable = false)
    private long wasteQuantity;

    @Column(name = "waste_reason", length = WASTE_REASON_MAX_LENGTH)
    private String wasteReason;

    /** 대응하는 소재 잠금. 잠그지 않은 소재는 투입할 수 없다. */
    @Column(name = "material_lock_id", nullable = false)
    private Long materialLockId;

    private ProductionInput(
            Long productionBatchId,
            Long materialId,
            long inputQuantity,
            long productUsedQuantity,
            long reusableScrapQuantity,
            long wasteQuantity,
            String wasteReason,
            Long materialLockId) {
        this.productionBatchId = requireNonNull(productionBatchId, "production batch id");
        this.materialId = requireNonNull(materialId, "material id");
        if (inputQuantity <= 0) {
            throw new IllegalArgumentException("Production input quantity must be positive");
        }
        requireNotNegative(productUsedQuantity, "product used quantity");
        requireNotNegative(reusableScrapQuantity, "reusable scrap quantity");
        requireNotNegative(wasteQuantity, "waste quantity");
        if (inputQuantity != productUsedQuantity + reusableScrapQuantity + wasteQuantity) {
            throw new IllegalArgumentException(
                    "Production input quantity must equal product used + reusable scrap + waste");
        }
        this.inputQuantity = inputQuantity;
        this.productUsedQuantity = productUsedQuantity;
        this.reusableScrapQuantity = reusableScrapQuantity;
        this.wasteQuantity = wasteQuantity;
        this.wasteReason = requireOptionalText(wasteReason, "waste reason", WASTE_REASON_MAX_LENGTH);
        this.materialLockId = requireNonNull(materialLockId, "material lock id");
    }

    /**
     * 소재를 배치에 넣는다.
     *
     * <p><b>투입 자격은 여기서 보지 않는다.</b> ERD 는 {@code WASTE} + {@code INITIAL} 소재를
     * 막는데(폐소재는 Processing 을 최소 1회 거쳐야 한다), 그 판정은 {@code material} 행을
     * 읽어야 나온다. 엔티티는 소재를 모르므로 투입 서비스가 확인한다.
     */
    public static ProductionInput record(
            Long productionBatchId,
            Long materialId,
            long inputQuantity,
            long productUsedQuantity,
            long reusableScrapQuantity,
            long wasteQuantity,
            String wasteReason,
            Long materialLockId) {
        return new ProductionInput(
                productionBatchId,
                materialId,
                inputQuantity,
                productUsedQuantity,
                reusableScrapQuantity,
                wasteQuantity,
                wasteReason,
                materialLockId);
    }

    /** 자투리가 남았는가. 남았다면 확정 시 새 소재를 발행한다. */
    public boolean hasReusableScrap() {
        return this.reusableScrapQuantity > 0;
    }

    private static void requireNotNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException("Production input " + field + " must not be negative");
        }
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Production input " + field + " must not be null");
        }
        return value;
    }

    private static String requireOptionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Production input " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
