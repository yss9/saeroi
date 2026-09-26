package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.processing.entity.*;
import java.time.LocalDateTime;

public record ProcessingSignatureChallengeResponse(Long challengeId, ProcessingSignatureAction action,
        String walletAddress, String nonce, String message, LocalDateTime expiresAt) {
    public static ProcessingSignatureChallengeResponse of(ProcessingSignatureChallenge c) {
        return new ProcessingSignatureChallengeResponse(c.getId(), c.getAction(), c.getWalletAddress(),
                c.getNonce(), c.getMessage(), c.getExpiresAt());
    }
}
