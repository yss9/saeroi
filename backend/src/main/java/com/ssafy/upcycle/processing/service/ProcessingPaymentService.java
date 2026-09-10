package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.*;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.trade.payment.*;
import com.ssafy.upcycle.user.entity.*;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ProcessingPaymentService {
    private final ProcessingJobRepository jobs;
    private final MaterialLockRepository locks;
    private final UserRepository users;
    private final PaymentGateway gateway;
    private final Clock clock;
    private final MaterialEventRecorder eventRecorder;
    @Value("${app.payment.kakaopay.processing-return-url:http://localhost:5173/processing/jobs/{jobId}/payment/callback}")
    private String returnUrl;

    @Transactional
    public ProcessingPaymentReadyResponse ready(Long actorId, Long jobId) {
        ProcessingJob job=lockedOwnedJob(actorId, jobId);
        LocalDateTime now=LocalDateTime.now(clock);
        requirePayable(job, now);
        PaymentReady result=gateway.ready(new PaymentReadyCommand(job.getJobCode(), String.valueOf(job.getBrandId()),
                "가공비 "+job.getJobCode(), job.getFee(), returnUrl.replace("{jobId}", String.valueOf(jobId))));
        job.preparePayment(result.tid());
        return new ProcessingPaymentReadyResponse(jobId, result.tid(), result.redirectUrl(), job.getFee(),
                gateway.isLive(), job.getPaymentExpiresAt());
    }

    @Transactional
    public ProcessingJobResponse confirm(Long actorId, Long jobId, String pgToken) {
        ProcessingJob job=lockedOwnedJob(actorId, jobId);
        requirePayable(job, LocalDateTime.now(clock));
        if (job.getPaymentReference()==null || job.getPaymentReference().isBlank())
            throw new BusinessException(PROCESSING_PAYMENT_NOT_PREPARED);
        PaymentApproval approval=gateway.approve(new PaymentApproveCommand(job.getPaymentReference(), job.getJobCode(),
                String.valueOf(job.getBrandId()), pgToken, job.getFee()));
        if (approval.approvedAmount()!=job.getFee()) throw new BusinessException(PROCESSING_PAYMENT_AMOUNT_MISMATCH);
        try { job.markPaid(approval.tid(), approval.approvedAmount(), approval.approvedAt()); }
        catch (IllegalStateException e) { throw new BusinessException(PROCESSING_PAYMENT_EXPIRED); }
        for (MaterialLock lock:locks.findByReferenceForUpdate(MaterialLockReferenceType.PROCESSING_JOB, jobId)) {
            if (lock.getStatus()==MaterialLockStatus.ACTIVE) lock.clearExpiryForPaidProcessingJob(jobId);
            eventRecorder.record(
                    lock.getMaterialId(), MaterialEventType.PROCESSING_JOB_PAID, actorId,
                    MaterialEventReferenceType.PROCESSING_JOB, jobId, null, lock.getAmount(),
                    "가공비 결제 승인",
                    MaterialEventRecorder.key(
                            MaterialEventType.PROCESSING_JOB_PAID,
                            lock.getMaterialId(), jobId));
        }
        return ProcessingJobResponse.of(job);
    }

    private ProcessingJob lockedOwnedJob(Long actorId, Long jobId) {
        if (actorId==null || users.findById(actorId).filter(User::isActive)
                .filter(user->user.getRole()==UserRole.BRAND).isEmpty())
            throw new BusinessException(PROCESSING_FORBIDDEN);
        ProcessingJob job=jobs.findForUpdate(jobId).orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
        if (!actorId.equals(job.getBrandId())) throw new BusinessException(PROCESSING_FORBIDDEN);
        return job;
    }
    private void requirePayable(ProcessingJob job, LocalDateTime now) {
        if (job.getStatus()!=ProcessingJobStatus.CREATED) throw new BusinessException(PROCESSING_INVALID_STATE);
        if (job.isPaymentDue(now)) throw new BusinessException(PROCESSING_PAYMENT_EXPIRED);
    }
}
