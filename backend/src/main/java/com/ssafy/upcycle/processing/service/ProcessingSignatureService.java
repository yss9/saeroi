package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.processing.repository.*;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.service.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ProcessingSignatureService {
    private final ProcessingJobRepository jobs;
    private final ProcessingOutputRepository outputs;
    private final UserWalletRepository wallets;
    private final UserRepository users;
    private final ProcessingSignatureChallengeRepository challenges;
    private final WalletNonceGenerator nonceGenerator;
    private final WalletSignatureVerifier signatureVerifier;
    private final Clock clock;

    @Transactional
    public ProcessingSignatureChallengeResponse issue(Long actorId, Long jobId,
            ProcessingSignatureChallengeRequest request) {
        ProcessingJob job = requireJob(jobId);
        requireActor(job, actorId, request.action());
        requireActionState(job, request.action());
        String payloadHash = payloadHash(job, request.action(), request.rejectionReason());
        UserWallet wallet = wallets.findByUserIdAndActiveTrue(actorId)
                .orElseThrow(() -> new BusinessException(PROCESSING_WALLET_NOT_FOUND));
        challenges.deleteByProcessingJobIdAndActorIdAndActionAndUsedAtIsNull(jobId, actorId, request.action());
        String nonce = nonceGenerator.generate();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusMinutes(5);
        String message = message(job, request.action(), wallet, payloadHash, nonce, expiresAt);
        return ProcessingSignatureChallengeResponse.of(challenges.saveAndFlush(
                ProcessingSignatureChallenge.issue(jobId, actorId, request.action(), wallet.getWalletAddress(),
                        wallet.getChainId(), job.getResultRevision(), payloadHash, nonce, message, expiresAt)));
    }

    @Transactional
    public String verify(Long actorId, ProcessingJob job, ProcessingSignatureAction action,
            Long challengeId, String signature, String rejectionReason) {
        ProcessingSignatureChallenge challenge = challenges.findForUpdate(challengeId)
                .orElseThrow(() -> new BusinessException(PROCESSING_SIGNATURE_CHALLENGE_NOT_FOUND));
        String currentHash = payloadHash(job, action, rejectionReason);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!challenge.matches(job.getId(), actorId, action, job.getResultRevision(), currentHash)
                || challenge.isUsed() || challenge.isExpiredAt(now))
            throw new BusinessException(PROCESSING_SIGNATURE_CHALLENGE_INVALID);
        String recovered = signatureVerifier.recoverAddress(challenge.getMessage(), signature);
        if (!recovered.equalsIgnoreCase(challenge.getWalletAddress()))
            throw new BusinessException(PROCESSING_SIGNATURE_MISMATCH);
        challenge.consume(now);
        return signature;
    }

    private ProcessingJob requireJob(Long id) {
        return jobs.findById(id).orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
    }

    private void requireActor(ProcessingJob job, Long actorId, ProcessingSignatureAction action) {
        UserRole expected = action == ProcessingSignatureAction.RESULT_SUBMIT ? UserRole.PROCESSOR : UserRole.BRAND;
        boolean allowed = users.findById(actorId).filter(User::isActive).filter(user -> user.getRole() == expected).isPresent()
                && (action == ProcessingSignatureAction.RESULT_SUBMIT
                ? actorId.equals(job.getProcessorId()) : actorId.equals(job.getBrandId()));
        if (!allowed) throw new BusinessException(PROCESSING_FORBIDDEN);
    }

    private void requireActionState(ProcessingJob job, ProcessingSignatureAction action) {
        boolean valid = switch (action) {
            case RESULT_SUBMIT -> (job.getStatus()==ProcessingJobStatus.PROCESSING || job.getStatus()==ProcessingJobStatus.REJECTED)
                    && job.getResultRevision() > 0;
            case APPROVE, REJECT -> job.getStatus()==ProcessingJobStatus.RESULT_SUBMITTED;
        };
        if (!valid) throw new BusinessException(PROCESSING_INVALID_STATE);
    }

    private String payloadHash(ProcessingJob job, ProcessingSignatureAction action, String rejectionReason) {
        if (action == ProcessingSignatureAction.REJECT && (rejectionReason == null || rejectionReason.isBlank()))
            throw new BusinessException(PROCESSING_RESULT_INVALID);
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, action.name());
        appendField(canonical, String.valueOf(job.getId()));
        appendField(canonical, String.valueOf(job.getResultRevision()));
        appendField(canonical, String.valueOf(job.getLossQuantity()));
        List<ProcessingOutput> rows = outputs.findAllByProcessingJobIdOrderById(job.getId());
        for (ProcessingOutput output : rows) {
            appendField(canonical, output.getMaterialType().name());
            appendField(canonical, output.getGrade());
            appendField(canonical, output.getName());
            appendField(canonical, String.valueOf(output.getQuantity()));
        }
        if (action == ProcessingSignatureAction.REJECT) appendField(canonical, rejectionReason.trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private void appendField(StringBuilder canonical, String value) {
        canonical.append(value.length()).append(':').append(value);
    }

    private String message(ProcessingJob job, ProcessingSignatureAction action, UserWallet wallet,
            String payloadHash, String nonce, LocalDateTime expiresAt) {
        return "Upcycle Processing Job\nJob ID: " + job.getId() + "\nAction: " + action
                + "\nWallet: " + wallet.getWalletAddress() + "\nChain ID: " + wallet.getChainId()
                + "\nResult Revision: " + job.getResultRevision() + "\nPayload Hash: " + payloadHash
                + "\nNonce: " + nonce + "\nExpires At: " + expiresAt;
    }
}
