package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 지갑 등록용 nonce 발급 요청.
 *
 * <p>로그인용({@code WalletNonceRequest})과 달리 <b>{@code chainId} 가 필수</b>다.
 * 로그인은 이미 등록된 지갑에서 체인을 읽어 오지만, 등록은 그 값을 처음 저장하는 순간이라
 * 서버가 대신 채울 근거가 없다.
 */
public record WalletRegisterNonceRequest(
        @NotBlank(message = "지갑 주소는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "지갑 주소 형식이 올바르지 않습니다.")
        String walletAddress,

        @NotNull(message = "체인 ID는 필수입니다.")
        @Positive(message = "체인 ID는 양수여야 합니다.")
        Long chainId) {
}
