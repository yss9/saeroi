package com.ssafy.upcycle.production.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 투입에서 되살아난 자투리. 생산이 낸 <b>부산물</b>이다.
 *
 * <p>{@link ProductionInput#getReusableScrapQuantity()} 가 0 보다 크면 그만큼이 버려지지
 * 않고 새 소재가 된다. 새 소재는 {@code generation_type = PRODUCTION_SCRAP} 이고
 * {@code root_origin} 은 부모에서 그대로 복사하며, 동시에 {@code material_lineage} 간선이
 * 생긴다 (ERD).
 *
 * <p><b>배치가 아니라 투입에 매달린다.</b> 자투리는 "이 배치에서 나온 천 조각" 이 아니라
 * "이 폐데님에서 나온 천 조각" 이다. 배치에 붙이면 어느 소재에서 나왔는지 알 수 없고,
 * 그러면 혈통이 끊긴다 — 추적성 플랫폼에서 그건 기능이 없는 것과 같다.
 *
 * <h2>{@code new_material_id} 가 NULL 을 허용한다</h2>
 *
 * <p>확정 후에 채워지기 때문이다. 새 소재를 만들려면 부모 소재를 읽고 코드를 발급하고
 * 혈통을 이어야 하는데, 그 일은 배치가 확정될 때 한다. 그전까지 이 행은 <b>"자투리가
 * 이만큼 나올 것" 이라는 예정</b>이고, 확정 후에는 "이 소재가 됐다" 는 사실이 된다.
 *
 * <p>그래서 {@code null} 인 {@code newMaterialId} 는 오류가 아니라 상태다. 확정된 배치인데
 * 여전히 {@code null} 이면 그때는 문제이며, 그 판정은 배치 상태를 아는 쪽이 한다.
 */
@Getter
@Entity
@Table(name = "production_output")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionOutput {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어느 투입에서 나왔는가. 배치가 아니라 투입이다 — 혈통이 소재 단위이기 때문이다. */
    @Column(name = "production_input_id", nullable = false)
    private Long productionInputId;

    /** 자투리로 발행된 새 소재. 확정 전에는 {@code null} 이다. */
    @Column(name = "new_material_id")
    private Long newMaterialId;

    /** 재발행 수량 (g). */
    @Column(name = "quantity", nullable = false)
    private long quantity;

    private ProductionOutput(Long productionInputId, long quantity) {
        this.productionInputId = requireNonNull(productionInputId, "production input id");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Production output quantity must be positive");
        }
        this.quantity = quantity;
    }

    /**
     * 자투리가 나올 것을 예정한다. 소재는 아직 없다.
     *
     * <p>수량이 0 이면 만들지 않는다 — 아무것도 남지 않은 투입에 빈 산출 행을 남기면
     * "자투리가 있었는데 소재가 안 만들어졌다" 와 구분할 수 없다.
     */
    public static ProductionOutput expect(Long productionInputId, long quantity) {
        return new ProductionOutput(productionInputId, quantity);
    }

    /**
     * 발행된 새 소재를 연결한다. 배치 확정과 같은 트랜잭션에서 부른다.
     *
     * <p>이미 연결된 산출을 다시 연결하지 않는다. 두 번 부르면 자투리 하나에서 소재가 둘
     * 생겼다는 뜻이고, 그중 하나는 아무도 가리키지 않은 채 잔량만 늘린다.
     */
    public void linkNewMaterial(Long newMaterialId) {
        if (this.newMaterialId != null) {
            throw new IllegalStateException(
                    "Production output already links material " + this.newMaterialId);
        }
        this.newMaterialId = requireNonNull(newMaterialId, "new material id");
    }

    /** 새 소재가 실제로 발행됐는가. */
    public boolean isIssued() {
        return this.newMaterialId != null;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Production output " + field + " must not be null");
        }
        return value;
    }
}
