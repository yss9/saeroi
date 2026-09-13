package com.ssafy.upcycle.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RefreshTokenCodecTests {

    private final RefreshTokenCodec codec = new RefreshTokenCodec();

    @Test
    void generatesUrlSafeUnpredictableTokens() {
        String first = codec.generate();
        String second = codec.generate();

        assertThat(first).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(second).matches("^[A-Za-z0-9_-]{43}$").isNotEqualTo(first);
    }

    @Test
    void hashesDeterministicallyWithoutReturningTheRawToken() {
        String hash = codec.hash("refresh-token");

        assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(hash).isEqualTo(codec.hash("refresh-token")).doesNotContain("refresh-token");
    }

    @Test
    void rejectsBlankTokens() {
        assertThatThrownBy(() -> codec.hash(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
