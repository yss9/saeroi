package com.ssafy.upcycle.user.dto;

import java.time.LocalDateTime;

/**
 * nonce 발급 결과.
 *
 * <p>{@code message} 를 함께 내려주는 것이 핵심이다. 서명 문자열의 <b>정본은 서버</b>이고,
 * Frontend 는 자기가 만든 문자열과 이 값을 대조한 뒤 <b>이 값에</b> 서명한다.
 * 그래서 양쪽 구현이 어긋나면 서명이 만들어지기 전에 드러난다.
 *
 * @param walletAddress EIP-55 체크섬 주소. 요청이 보낸 대소문자가 아니라 서버가 계산한 값이다.
 */
public record WalletNonceResponse(
        String walletAddress,
        String nonce,
        String message,
        LocalDateTime expiresAt) {
}
