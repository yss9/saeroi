package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.*;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.processing.repository.*;
import com.ssafy.upcycle.user.entity.*;
import com.ssafy.upcycle.user.repository.*;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ProcessingJobService {
    private final ProcessingQuoteRepository quotes;
    private final ProcessingRequestRepository requests;
    private final ProcessingRequestInputRepository requestInputs;
    private final ProcessingJobRepository jobs;
    private final ProcessingInputRepository jobInputs;
    private final MaterialLockRepository locks;
    private final UserRepository users;
    private final UserWalletRepository wallets;
    private final Clock clock;

    @Transactional
    public ProcessingJobResponse select(Long actorId, Long quoteId, SelectProcessingQuoteRequest input) {
        requireActiveBrand(actorId);
        ProcessingQuote quote=quotes.findForUpdate(quoteId).orElseThrow(() -> new BusinessException(PROCESSING_QUOTE_NOT_FOUND));
        ProcessingRequest request=requests.findForUpdate(quote.getProcessingRequestId())
                .orElseThrow(() -> new BusinessException(PROCESSING_REQUEST_NOT_FOUND));
        LocalDateTime now=LocalDateTime.now(clock);
        if (!actorId.equals(request.getBrandId())) throw new BusinessException(PROCESSING_FORBIDDEN);
        if (!quote.getProcessorId().equals(request.getProcessorId()) || request.getStatus()!=ProcessingRequestStatus.QUOTING
                || quote.getStatus()!=ProcessingQuoteStatus.SUBMITTED || request.isDue(now))
            throw new BusinessException(PROCESSING_INVALID_STATE);
        UserWallet brandWallet=wallets.findByUserIdAndActiveTrue(actorId)
                .orElseThrow(() -> new BusinessException(PROCESSING_WALLET_NOT_FOUND));
        UserWallet processorWallet=wallets.findByUserIdAndActiveTrue(request.getProcessorId())
                .orElseThrow(() -> new BusinessException(PROCESSING_WALLET_NOT_FOUND));
        ProcessingRequestInput requestInput=requestInputs.findByProcessingRequestId(request.getId())
                .orElseThrow(() -> new BusinessException(PROCESSING_INVALID_STATE));
        MaterialLock lock=locks.findByReferenceForUpdate(MaterialLockReferenceType.PROCESSING_REQUEST, request.getId())
                .stream().filter(l->l.getStatus()==MaterialLockStatus.ACTIVE).findFirst()
                .orElseThrow(() -> new BusinessException(PROCESSING_INVALID_STATE));
        ProcessingJob job=jobs.saveAndFlush(ProcessingJob.create("PJ"+UUID.randomUUID().toString().replace("-", "").substring(0,28),
                request.getId(), quote.getId(), request.getBrandId(), request.getProcessorId(),
                brandWallet.getWalletAddress(), processorWallet.getWalletAddress(), quote.getPrice(),
                input.paymentMethod(), now));
        lock.attachToProcessingJob(request.getId(), job.getId(), job.getPaymentExpiresAt());
        jobInputs.save(new ProcessingInput(job.getId(), requestInput.getMaterialId(), requestInput.getQuantity(), lock.getId()));
        quote.select(); request.select();
        return ProcessingJobResponse.of(job);
    }

    @Transactional(readOnly=true)
    public ProcessingJobResponse detail(Long actorId, Long jobId) {
        ProcessingJob job=jobs.findById(jobId).orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
        if (!actorId.equals(job.getBrandId()) && !actorId.equals(job.getProcessorId()))
            throw new BusinessException(PROCESSING_FORBIDDEN);
        if (users.findById(actorId).filter(User::isActive).isEmpty()) throw new BusinessException(PROCESSING_FORBIDDEN);
        return ProcessingJobResponse.of(job);
    }

    @Transactional
    public void expire(Long jobId) {
        ProcessingJob job=jobs.findForUpdate(jobId).orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
        LocalDateTime now=LocalDateTime.now(clock);
        if (!job.isPaymentDue(now)) return;
        for (MaterialLock lock:locks.findByReferenceForUpdate(MaterialLockReferenceType.PROCESSING_JOB, jobId)) {
            if (lock.getStatus()==MaterialLockStatus.ACTIVE) lock.expire(now);
        }
        job.expire(now);
    }

    private void requireActiveBrand(Long id) {
        if (id==null || users.findById(id).filter(User::isActive).filter(u->u.getRole()==UserRole.BRAND).isEmpty())
            throw new BusinessException(PROCESSING_FORBIDDEN);
    }
}
