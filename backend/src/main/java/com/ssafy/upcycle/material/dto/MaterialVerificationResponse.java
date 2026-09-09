package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.MaterialVerificationState;
import com.ssafy.upcycle.material.service.BlockchainVerification.SourceVerificationEvidence;
import java.time.LocalDateTime;

/**
 * Blockchain Proof 화면(누가·언제·어느 Tx)이 그리는 근거.
 *
 * <p>근거가 없으면 이 객체 자체가 {@code null} 이다 — 빈 객체를 내려보내면 화면이 "값이
 * 있는데 비어 있음" 과 "아직 없음" 을 구분해야 한다
 * ({@code docs/superpowers/specs/2026-09-07-material-verification-state.md}).
 *
 * <p>지금은 {@code SOURCE_VERIFIED} 만 채운다. 다른 단(예: {@code TRANSACTION_VERIFIED})은
 * 근거가 온체인 Tx 가 아니라 거래 서명이라 이 모양(지갑·Tx 해시)이 맞지 않는다 — 그 단의
 * 근거를 보여줄 화면이 생기면 그때 별도로 정한다.
 */
public record MaterialVerificationResponse(
        MaterialVerificationState state,
        String signerWallet,
        LocalDateTime signedAt,
        String txHash) {

    public static MaterialVerificationResponse of(
            MaterialVerificationState state, SourceVerificationEvidence evidence) {
        return new MaterialVerificationResponse(
                state, evidence.signerWallet(), evidence.confirmedAt(), evidence.txHash());
    }
}
