package com.ssafy.upcycle.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.processing.entity.ProcessingSignatureAction;
import com.ssafy.upcycle.processing.entity.ProcessingSignatureChallenge;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProcessingSignatureChallengeTests {

    @Test
    void challengeIsBoundToJobActorActionRevisionAndPayload() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 10, 0);
        ProcessingSignatureChallenge challenge = ProcessingSignatureChallenge.issue(
                7L, 20L, ProcessingSignatureAction.RESULT_SUBMIT,
                "0x2222222222222222222222222222222222222222", 31337L,
                3, "a".repeat(64), "b".repeat(32), "message", now.plusMinutes(5));

        assertThat(challenge.matches(7L, 20L, ProcessingSignatureAction.RESULT_SUBMIT, 3, "a".repeat(64)))
                .isTrue();
        assertThat(challenge.matches(8L, 20L, ProcessingSignatureAction.RESULT_SUBMIT, 3, "a".repeat(64)))
                .isFalse();
    }

    @Test
    void challengeExpiresAndCanOnlyBeConsumedOnce() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 10, 0);
        ProcessingSignatureChallenge challenge = ProcessingSignatureChallenge.issue(
                7L, 20L, ProcessingSignatureAction.APPROVE,
                "0x2222222222222222222222222222222222222222", 31337L,
                1, "a".repeat(64), "b".repeat(32), "message", now.plusMinutes(5));

        challenge.consume(now.plusMinutes(4));

        assertThat(challenge.isUsed()).isTrue();
        assertThatThrownBy(() -> challenge.consume(now.plusMinutes(4)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(challenge.isExpiredAt(now.plusMinutes(5))).isTrue();
    }
}
