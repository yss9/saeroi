package com.ssafy.upcycle.production.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionOutputTests {

    @Test
    void expectedScrapHasNoMaterialYet() {
        // 확정 전에는 "자투리가 이만큼 나올 것" 이라는 예정이다. null 은 오류가 아니라 상태다.
        ProductionOutput output = ProductionOutput.expect(1L, 2_000L);

        assertThat(output.getNewMaterialId()).isNull();
        assertThat(output.isIssued()).isFalse();
        assertThat(output.getQuantity()).isEqualTo(2_000L);
    }

    @Test
    void quantityMustBePositive() {
        // 아무것도 남지 않은 투입에 빈 산출 행을 남기면
        // "자투리가 있었는데 소재가 안 만들어졌다" 와 구분할 수 없다.
        assertThatThrownBy(() -> ProductionOutput.expect(1L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issuedMaterialIsLinkedOnce() {
        ProductionOutput output = ProductionOutput.expect(1L, 2_000L);
        output.linkNewMaterial(42L);

        assertThat(output.isIssued()).isTrue();
        assertThat(output.getNewMaterialId()).isEqualTo(42L);
    }

    @Test
    void relinkingIsRejected() {
        // 두 번 부르면 자투리 하나에서 소재가 둘 생겼다는 뜻이고,
        // 그중 하나는 아무도 가리키지 않은 채 잔량만 늘린다.
        ProductionOutput output = ProductionOutput.expect(1L, 2_000L);
        output.linkNewMaterial(42L);

        assertThatThrownBy(() -> output.linkNewMaterial(43L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void outputHangsOffTheInputNotTheBatch() {
        // 자투리는 "이 배치에서 나온 천 조각" 이 아니라 "이 폐데님에서 나온 천 조각" 이다.
        // 배치에 붙이면 어느 소재에서 나왔는지 알 수 없고 혈통이 끊긴다.
        assertThat(ProductionOutput.expect(7L, 100L).getProductionInputId()).isEqualTo(7L);
        assertThatThrownBy(() -> ProductionOutput.expect(null, 100L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
