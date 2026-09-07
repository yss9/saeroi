package com.ssafy.upcycle.material.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 단위 환산. DB 에 단위 컬럼이 없으므로 <b>여기가 유일한 환산 지점</b>이고,
 * 값이 틀리면 소재 수량이 1000배 어긋난 채 온체인까지 나간다.
 */
class MaterialQuantityUnitTests {

    @ParameterizedTest
    @CsvSource({"G,1,1", "G,50000,50000", "KG,1,1000", "KG,50,50000"})
    void convertsToGrams(MaterialQuantityUnit unit, long quantity, long expectedGrams) {
        assertThat(unit.toGrams(quantity)).isEqualTo(expectedGrams);
    }

    @Test
    void gramsPassThroughUnchanged() {
        // 저장 단위와 같으므로 환산이 개입하면 안 된다.
        assertThat(MaterialQuantityUnit.G.toGrams(12_345)).isEqualTo(12_345);
    }

    @Test
    void rejectsAnOverflowingConversion() {
        // 조용히 음수로 감기면 chk_material_quantity 위반으로 설명할 수 없는 500 이 된다.
        // 여기서 터뜨려 호출부가 400 으로 바꾼다.
        assertThatThrownBy(() -> MaterialQuantityUnit.KG.toGrams(Long.MAX_VALUE))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void supportsOnlyUnitsThatConvertToWholeGrams() {
        // 파운드처럼 정수로 나누어떨어지지 않는 단위를 넣으면 반올림 정책이 먼저 필요하다.
        // 목록이 늘어나면 이 단언이 깨져 그 논의를 강제한다.
        assertThat(MaterialQuantityUnit.values())
                .containsExactly(MaterialQuantityUnit.G, MaterialQuantityUnit.KG);
    }
}
