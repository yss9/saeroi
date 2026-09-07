package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 서명 검증 요청.
 *
 * <p>{@code nonce} 를 함께 받는다. 없어도 주소로 최신 nonce 를 찾을 수는 있지만,
 * 어느 발급분에 대한 서명인지 명시받아야 정확히 그 한 건만 소모할 수 있다.
 *
 * <p>이 record 에 {@code toString} 을 추가하지 말 것 — {@code signature} 가 로그에 섞여 나간다.
 * record 의 기본 {@code toString} 은 모든 필드를 찍으므로 로깅 대상으로 넘기지도 않는다.
 */
public record WalletVerifyRequest(
        @NotBlank(message = "지갑 주소는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "지갑 주소 형식이 올바르지 않습니다.")
        String walletAddress,

        @NotBlank(message = "nonce는 필수입니다.")
        @Pattern(regexp = "^[0-9a-f]{32}$", message = "nonce 형식이 올바르지 않습니다.")
        String nonce,

        @NotBlank(message = "서명은 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{130}$", message = "서명 형식이 올바르지 않습니다.")
        String signature) {
}
