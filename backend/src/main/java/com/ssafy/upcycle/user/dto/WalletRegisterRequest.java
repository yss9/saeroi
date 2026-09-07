package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** 서명 검증 후 지갑을 등록하는 요청. */
public record WalletRegisterRequest(
        @NotBlank(message = "지갑 주소는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "지갑 주소 형식이 올바르지 않습니다.")
        String walletAddress,

        @NotNull(message = "체인 ID는 필수입니다.")
        @Positive(message = "체인 ID는 양수여야 합니다.")
        Long chainId,

        @NotBlank(message = "nonce는 필수입니다.")
        @Pattern(regexp = "^[0-9a-f]{32}$", message = "nonce 형식이 올바르지 않습니다.")
        String nonce,

        @NotBlank(message = "서명은 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{130}$", message = "서명 형식이 올바르지 않습니다.")
        String signature) {
}
