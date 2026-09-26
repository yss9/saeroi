package com.ssafy.upcycle.material.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 화면이 지갑으로 {@code MaterialToken.verifySource} 를 보내 확정된 뒤, 그 결과를 서버에
 * 알리는 요청.
 *
 * <p>{@code onChainId} 가 없다 — mint 확정({@code ChainLinkRequest})과 달리 verifySource 는
 * 새로 채번되는 값이 없다. 이미 온체인에 있는 {@code tokenId} 에 서명 사실만 얹는다.
 *
 * @param txHash 확정된 트랜잭션 해시. {@code 0x} + 64 hex
 * @param blockNumber 채굴된 블록. 없어도 기록은 성립하므로 선택이다
 */
public record MaterialSourceVerificationRequest(
        @NotBlank
        @Pattern(regexp = "^0x[0-9a-fA-F]{64}$", message = "트랜잭션 해시 형식이 올바르지 않습니다.")
        String txHash,

        Long blockNumber) {
}
