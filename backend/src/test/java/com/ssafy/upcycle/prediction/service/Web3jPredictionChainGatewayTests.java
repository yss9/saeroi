package com.ssafy.upcycle.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class Web3jPredictionChainGatewayTests {
    @Test
    void convertsKoreanLocalDateTimeToUnixEpoch() {
        LocalDateTime koreanTime = LocalDateTime.of(2026, 9, 21, 20, 0);

        BigInteger epoch = Web3jPredictionChainGateway.epoch(koreanTime);

        assertThat(epoch).isEqualTo(BigInteger.valueOf(
                Instant.parse("2026-09-21T11:00:00Z").getEpochSecond()));
    }
}
