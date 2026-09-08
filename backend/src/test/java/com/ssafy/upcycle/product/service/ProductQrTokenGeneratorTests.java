package com.ssafy.upcycle.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProductQrTokenGeneratorTests {

    private final ProductQrTokenGenerator generator = new ProductQrTokenGenerator();

    /** CHAR(32) 를 정확히 채워야 한다. 엔티티도 같은 형식을 강제한다. */
    @Test
    void tokenIsThirtyTwoLowercaseHexDigits() {
        assertThat(generator.generate()).matches("^[0-9a-f]{32}$");
    }

    /**
     * 값이 겹치면 UNIQUE 제약에 걸려 생산 확정이 실패한다. 더 나쁜 것은 겹칠 수 있다는 것이
     * 곧 추측할 수 있다는 뜻이라는 점이다 — QR 이력은 인증 없이 열리는 경로다.
     */
    @Test
    void tokensDoNotRepeat() {
        Set<String> tokens = new HashSet<>();
        IntStream.range(0, 1_000).forEach(i -> tokens.add(generator.generate()));

        assertThat(tokens).hasSize(1_000);
    }
}
