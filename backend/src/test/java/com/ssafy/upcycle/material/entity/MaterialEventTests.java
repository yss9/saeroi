package com.ssafy.upcycle.material.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MaterialEventTests {

    @Test
    void recordsAnImmutableMaterialEvent() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 10, 12, 0);

        MaterialEvent event = MaterialEvent.record(
                10L,
                MaterialEventType.MATERIAL_REGISTERED,
                3L,
                MaterialEventReferenceType.MATERIAL,
                10L,
                null,
                1_500L,
                "최초 소재 등록",
                "MATERIAL_REGISTERED:10",
                occurredAt);

        assertThat(event.getMaterialId()).isEqualTo(10L);
        assertThat(event.getEventType()).isEqualTo(MaterialEventType.MATERIAL_REGISTERED);
        assertThat(event.getActorId()).isEqualTo(3L);
        assertThat(event.getReferenceType()).isEqualTo(MaterialEventReferenceType.MATERIAL);
        assertThat(event.getReferenceId()).isEqualTo(10L);
        assertThat(event.getQuantity()).isEqualTo(1_500L);
        assertThat(event.getIdempotencyKey()).isEqualTo("MATERIAL_REGISTERED:10");
        assertThat(event.getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void rejectsAnEventWithoutAnIdempotencyKey() {
        assertThatThrownBy(() -> MaterialEvent.record(
                10L,
                MaterialEventType.MATERIAL_REGISTERED,
                3L,
                MaterialEventReferenceType.MATERIAL,
                10L,
                null,
                1_500L,
                null,
                " ",
                LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeQuantities() {
        assertThatThrownBy(() -> MaterialEvent.record(
                10L,
                MaterialEventType.MATERIAL_REGISTERED,
                3L,
                MaterialEventReferenceType.MATERIAL,
                10L,
                null,
                -1L,
                null,
                "MATERIAL_REGISTERED:10",
                LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
