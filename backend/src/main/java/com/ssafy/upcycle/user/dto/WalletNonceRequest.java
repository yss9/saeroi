package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * nonce 발급 요청.
 *
 * <p>{@code chainId} 는 선택이다. 저장되는 값은 언제나 {@code user_wallet.chain_id} 를 복사하며,
 * 요청이 값을 보낸 경우에만 대조해서 다르면 거부한다. 사용자가 MetaMask 를 다른 네트워크로
 * 바꿔 둔 상태를 <b>서명 팝업이 뜨기 전에</b> 알려 주기 위한 것이다.
 *
 * @param walletAddress 지갑이 보고한 주소. 대소문자는 신뢰하지 않고 서버가 정규화한다.
 * @param chainId 10진수 체인 ID. Frontend 는 hex 를 10진수로 바꿔 보낸다.
 */
public record WalletNonceRequest(
        @NotBlank(message = "지갑 주소는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "지갑 주소 형식이 올바르지 않습니다.")
        String walletAddress,

        @Positive(message = "체인 ID는 양수여야 합니다.")
        Long chainId) {
}
