package com.ssafy.upcycle.processing.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processing_signature_challenge")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingSignatureChallenge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "processing_job_id", nullable = false) private Long processingJobId;
    @Column(name = "actor_id", nullable = false) private Long actorId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProcessingSignatureAction action;
    @Column(name = "wallet_address", nullable = false, length = 42) private String walletAddress;
    @Column(name = "chain_id", nullable = false) private long chainId;
    @Column(name = "result_revision", nullable = false) private int resultRevision;
    @Column(name = "payload_hash", nullable = false, columnDefinition = "CHAR(64)") private String payloadHash;
    @Column(nullable = false, columnDefinition = "CHAR(32)") private String nonce;
    @Column(nullable = false, columnDefinition = "TEXT") private String message;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "used_at") private LocalDateTime usedAt;

    public static ProcessingSignatureChallenge issue(Long jobId, Long actorId,
            ProcessingSignatureAction action, String walletAddress, long chainId,
            int resultRevision, String payloadHash, String nonce, String message,
            LocalDateTime expiresAt) {
        if (jobId == null || actorId == null || action == null || walletAddress == null
                || walletAddress.isBlank() || chainId <= 0 || resultRevision <= 0
                || payloadHash == null || payloadHash.length() != 64
                || nonce == null || nonce.length() != 32 || message == null || message.isBlank()
                || expiresAt == null) throw new IllegalArgumentException("Valid signature challenge fields are required");
        ProcessingSignatureChallenge challenge = new ProcessingSignatureChallenge();
        challenge.processingJobId = jobId; challenge.actorId = actorId; challenge.action = action;
        challenge.walletAddress = walletAddress.toLowerCase(); challenge.chainId = chainId;
        challenge.resultRevision = resultRevision; challenge.payloadHash = payloadHash;
        challenge.nonce = nonce; challenge.message = message; challenge.expiresAt = expiresAt;
        return challenge;
    }

    public boolean matches(Long jobId, Long actorId, ProcessingSignatureAction action,
            int resultRevision, String payloadHash) {
        return processingJobId.equals(jobId) && this.actorId.equals(actorId) && this.action == action
                && this.resultRevision == resultRevision && this.payloadHash.equals(payloadHash);
    }

    public boolean isExpiredAt(LocalDateTime now) { return !expiresAt.isAfter(now); }
    public boolean isUsed() { return usedAt != null; }
    public void consume(LocalDateTime now) {
        if (now == null || isUsed() || isExpiredAt(now)) throw new IllegalStateException("Challenge cannot be consumed");
        usedAt = now;
    }
}
