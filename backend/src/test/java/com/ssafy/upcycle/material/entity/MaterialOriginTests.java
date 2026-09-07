package com.ssafy.upcycle.material.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 발생 사유가 출처와 모순되지 않는지.
 *
 * <p>두 값이 따로 들어오면 <b>"신소재인데 재고 소진"</b> 같은 행이 조용히 저장된다.
 * DB 로는 막지 못한다 — 두 컬럼에 걸친 조건이라 {@code CHECK} 가 필요하고, 값이 없는
 * 기존 행이 있는 상태에서 제약을 추가하려면 별도 절차가 붙는다. 엔티티가 유일한 문이다.
 */
class MaterialOriginTests {

    private static Material material(MaterialRootOrigin rootOrigin) {
        return Material.register(
                "MAT-000001",
                MaterialType.DENIM,
                rootOrigin,
                "소재",
                null,
                null,
                1_000L,
                null,
                null,
                1L);
    }

    @ParameterizedTest
    @EnumSource(
            value = MaterialOriginReason.class,
            names = "ETC",
            mode = EnumSource.Mode.EXCLUDE)
    void wasteAcceptsEveryReasonWithoutANote(MaterialOriginReason reason) {
        Material waste = material(MaterialRootOrigin.WASTE);

        waste.describeOrigin(reason, null);

        assertThat(waste.getOriginReason()).isEqualTo(reason);
        assertThat(waste.getOriginReasonNote()).isNull();
    }

    @Test
    void etcRequiresANote() {
        // ETC 는 값 자체가 뜻을 담지 않아 설명이 없으면 분류가 무의미해진다.
        Material waste = material(MaterialRootOrigin.WASTE);

        assertThatThrownBy(() -> waste.describeOrigin(MaterialOriginReason.ETC, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> waste.describeOrigin(MaterialOriginReason.ETC, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void etcWithANoteIsAccepted() {
        Material waste = material(MaterialRootOrigin.WASTE);

        waste.describeOrigin(MaterialOriginReason.ETC, "행사 후 남은 배너 원단");

        assertThat(waste.getOriginReasonNote()).isEqualTo("행사 후 남은 배너 원단");
    }

    @Test
    void aNoteOnANonEtcReasonIsKeptNotRejected() {
        // 부가 설명은 어느 값에서도 쓸모가 있다. 거절하면 사용자가 적어 둔 정보를 버린다.
        Material waste = material(MaterialRootOrigin.WASTE);

        waste.describeOrigin(MaterialOriginReason.DEADSTOCK, "2024 F/W 재고");

        assertThat(waste.getOriginReason()).isEqualTo(MaterialOriginReason.DEADSTOCK);
        assertThat(waste.getOriginReasonNote()).isEqualTo("2024 F/W 재고");
    }

    @Test
    void virginMaterialCannotHaveAnOriginReason() {
        // 신소재에는 "왜 폐기물이 되었나" 라는 개념이 없다.
        Material virgin = material(MaterialRootOrigin.VIRGIN);

        assertThatThrownBy(() -> virgin.describeOrigin(MaterialOriginReason.DEADSTOCK, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void describingNothingLeavesTheMaterialUnchanged() {
        // 구성·사유를 모두 생략한 등록이 정상 경로다. 여기서 던지면 그 경로가 막힌다.
        Material virgin = material(MaterialRootOrigin.VIRGIN);

        assertThatCode(() -> virgin.describeOrigin(null, null)).doesNotThrowAnyException();
        assertThat(virgin.getOriginReason()).isNull();
    }

    @Test
    void rejectsANoteLongerThanTheColumn() {
        // origin_reason_note VARCHAR(200). 넘으면 DB 에서 잘리거나 터진다.
        Material waste = material(MaterialRootOrigin.WASTE);

        assertThatThrownBy(() ->
                        waste.describeOrigin(MaterialOriginReason.ETC, "가".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
