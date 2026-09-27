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

    // 운영에서 본 require 문구 그대로다. 이 사유가 로그에 남아야 원인을 찾는다.
    @Test
    void decodesTheRequireMessageOfARevertedCall() {
        String data = org.web3j.abi.FunctionEncoder.encodeConstructor(
                java.util.List.of(new org.web3j.abi.datatypes.Utf8String("PredictionMarket: batch is not confirmed")));

        assertThat(Web3jPredictionChainGateway.decodeErrorString("0x08c379a0" + data))
                .isEqualTo("PredictionMarket: batch is not confirmed");
        assertThat(Web3jPredictionChainGateway.decodeErrorString("0x")).isNull();
        assertThat(Web3jPredictionChainGateway.decodeErrorString(null)).isNull();
    }
}
