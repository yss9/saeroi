package com.ssafy.upcycle.material.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MaterialLineageTests {

    @Test
    void lineageLinksChildToParent() {
        MaterialLineage lineage = MaterialLineage.of(
                2L, 1L, MaterialLineageRelationType.PROCESSING, 10L);

        assertThat(lineage.getChildMaterialId()).isEqualTo(2L);
        assertThat(lineage.getParentMaterialId()).isEqualTo(1L);
        assertThat(lineage.getRelationType()).isEqualTo(MaterialLineageRelationType.PROCESSING);
        assertThat(lineage.getReferenceId()).isEqualTo(10L);
    }

    @Test
    void materialCannotBeItsOwnParent() {
        assertThatThrownBy(() -> MaterialLineage.of(
                        1L, 1L, MaterialLineageRelationType.PROCESSING, 10L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyReferenceIsRequired() {
        assertThatThrownBy(() -> MaterialLineage.of(
                        null, 1L, MaterialLineageRelationType.PROCESSING, 10L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MaterialLineage.of(
                        2L, null, MaterialLineageRelationType.PROCESSING, 10L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MaterialLineage.of(2L, 1L, null, 10L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MaterialLineage.of(
                        2L, 1L, MaterialLineageRelationType.PROCESSING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
