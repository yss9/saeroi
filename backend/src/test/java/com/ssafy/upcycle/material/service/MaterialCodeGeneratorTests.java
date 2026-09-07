package com.ssafy.upcycle.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.material.entity.Material;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 코드 발급기. 여기서 지키는 것은 두 가지 계약이다 —
 * <b>임시 코드가 컬럼 길이에 정확히 들어맞는다</b>는 것과
 * <b>정식 코드가 {@code id} 에서만 나온다</b>는 것.
 *
 * <p>길이가 20자를 넘으면 INSERT 단계에서 잘려 UNIQUE 충돌이 나는데, 그 실패는
 * "왜 등록이 간헐적으로 실패하는가" 로 나타나 원인 추적이 어렵다. 그래서 형식을 여기 고정한다.
 */
class MaterialCodeGeneratorTests {

    private final MaterialCodeGenerator generator = new MaterialCodeGenerator();

    @ParameterizedTest
    @CsvSource({"1,MAT-000001", "42,MAT-000042", "999999,MAT-999999", "1000000,MAT-1000000"})
    void derivesTheCodeFromTheIdWithSixDigitPadding(long id, String expected) {
        assertThat(generator.of(id)).isEqualTo(expected);
    }

    @Test
    void codeNeverExceedsTheColumnLength() {
        // long 최대값도 19자리라 "MAT-" 를 붙여 23자가 되는데, 그건 도달 불가능한 영역이다.
        // 실제로 걸릴 수 있는 상한(6자리)까지는 컬럼 길이 안에 있어야 한다.
        assertThat(generator.of(999_999)).hasSizeLessThanOrEqualTo(Material.MATERIAL_CODE_MAX_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsANonPositiveId(long id) {
        // id 가 0 이면 저장 전 엔티티를 넘긴 것이다 — 조용히 MAT-000000 을 만들면
        // 유일하지만 존재하지 않는 소재를 가리키는 코드가 생긴다.
        assertThatThrownBy(() -> generator.of(id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void placeholderFillsTheColumnExactly() {
        String placeholder = generator.placeholder();

        assertThat(placeholder).startsWith(Material.TEMPORARY_CODE_PREFIX);
        assertThat(placeholder).hasSize(Material.MATERIAL_CODE_MAX_LENGTH);
    }

    @Test
    void placeholdersDoNotRepeat() {
        // 같은 밀리초에 들어온 두 요청이 같은 임시 코드를 뽑으면 두 번째 INSERT 가
        // uk_material_code 로 실패한다. 시간 기반 값이면 이 단언이 깨진다.
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 1_000).forEach(i -> generated.add(generator.placeholder()));

        assertThat(generated).hasSize(1_000);
    }

    @Test
    void placeholderIsNotMistakenForARealCode() {
        assertThat(generator.placeholder()).doesNotStartWith("MAT-");
    }
}
