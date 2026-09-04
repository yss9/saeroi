package com.ssafy.upcycle.material.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MaterialTests {

    @Test
    void registeredMaterialStartsActive() {
        Material material = registerDenim(20_000L);

        assertThat(material.getStatus()).isEqualTo(MaterialStatus.ACTIVE);
    }

    @Test
    void registrationAlwaysProducesInitialGeneration() {
        Material material = registerDenim(20_000L);

        assertThat(material.getGenerationType()).isEqualTo(MaterialGenerationType.INITIAL);
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        assertThatThrownBy(() -> registerDenim(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registerDenim(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankMaterialCodeIsRejected() {
        assertThatThrownBy(() -> Material.register(
                        "  ",
                        MaterialType.DENIM,
                        MaterialRootOrigin.WASTE,
                        "폐데님",
                        null,
                        null,
                        20_000L,
                        null,
                        null,
                        1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dimensionsAreOptionalButMustBePositiveWhenPresent() {
        Material withoutDimensions = registerDenim(20_000L);
        assertThat(withoutDimensions.getLengthMm()).isNull();
        assertThat(withoutDimensions.getWidthMm()).isNull();

        assertThatThrownBy(() -> Material.register(
                        "MAT-000001",
                        MaterialType.DENIM,
                        MaterialRootOrigin.WASTE,
                        "폐데님",
                        null,
                        null,
                        20_000L,
                        0L,
                        1_500L,
                        1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quantityIsStoredInGrams() {
        Material material = registerDenim(18_500L);

        assertThat(material.getTotalQuantity()).isEqualTo(18_500L);
    }

    private Material registerDenim(long totalQuantity) {
        return Material.register(
                "MAT-000001",
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "폐데님",
                null,
                null,
                totalQuantity,
                null,
                null,
                1L);
    }
}
