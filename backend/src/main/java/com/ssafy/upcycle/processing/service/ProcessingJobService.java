package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.*;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.processing.repository.*;
import com.ssafy.upcycle.user.entity.*;
import com.ssafy.upcycle.user.repository.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제와 발송 사이 — <b>가공사가 일을 받을지 답하는 구간</b>.
 *
 * <p>견적이 사라지면서 새로 생긴 자리다. 예전에는 견적을 낸다는 것 자체가 수락이었고,
 * 브랜드가 그 견적을 고르는 순간 일이 확정됐다. 지금은 브랜드가 먼저 고르고 먼저 결제하므로,
 * <b>가공사에게도 거절할 기회가 있어야 한다</b> — 없으면 받을 수 없는 일이 들어와도
 * 가공사는 소재를 받고 나서야 못 하겠다고 말하게 된다.
 *
 * <p>거절은 돈이 오간 뒤의 일이라 환불이 따라붙는다. 그 책임은
 * {@link ProcessingRefundService} 한 곳에 있다.
 */
@Service
@RequiredArgsConstructor
public class ProcessingJobService {

    private final ProcessingJobRepository jobs;
    private final MaterialLockRepository locks;
    private final UserRepository users;
    private final Clock clock;
    private final MaterialEventRecorder eventRecorder;
    private final ProcessingNotificationService businessNotifications;
    private final ProcessingRequestService requestService;

    /**
     * 가공사가 일을 받는다.
     *
     * <p>결제가 끝난 작업만 수락된다 — 돈이 오지 않은 일을 받아 두면 가공사가 일정을 비워
     * 둔 채로 기다리게 된다.
     */
    @Transactional
    public ProcessingJobResponse accept(Long actorId, Long jobId) {
        ProcessingJob job = lockedProcessorJob(actorId, jobId);
        if (job.getStatus() != ProcessingJobStatus.PAID) {
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        // 수락은 온체인 서명으로 이뤄진다. 체인에 job 이 없으면 서명할 대상이 없고,
        // 여기서 DB 만 ACCEPTED 로 올려 두면 체인은 영영 REQUESTED 에 남는다.
        if (job.getChainJobId() == null) {
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        job.accept(now);
        requestService.markAccepted(job.getProcessingRequestId());
        recordOnInputs(job, MaterialEventType.PROCESSING_JOB_ACCEPTED, actorId, "가공사가 요청을 수락");
        businessNotifications.jobAccepted(job);
        return ProcessingJobResponse.of(job);
    }

    /**
     * 가공사가 일을 거절한다. <b>결제분은 환불된다.</b>
     *
     * <p>요청도 함께 끝난다 — 요청과 작업이 한 몸으로 만들어졌으므로 한 몸으로 끝나야 하고,
     * 작업만 닫으면 브랜드의 소재 잠금이 남는다. 잠금 해제와 환불은
     * {@code ProcessingRequestService#markDeclined} 가 이어받는다.
     */
    @Transactional
    public ProcessingJobResponse decline(Long actorId, Long jobId, String reason) {
        ProcessingJob job = lockedProcessorJob(actorId, jobId);
        if (job.getStatus() != ProcessingJobStatus.PAID
                && job.getStatus() != ProcessingJobStatus.CREATED) {
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        requestService.markDeclined(job.getProcessingRequestId(), actorId, reason, false);
        return ProcessingJobResponse.of(job);
    }

    /**
     * 수락 기한이 지난 작업을 정리한다. 배치가 부른다.
     *
     * <p>브랜드 입장에서 <b>가장 나쁜 결말은 아무 일도 일어나지 않는 것</b>이다 — 돈과 소재가
     * 묶인 채로 가공사의 답을 기다리기만 한다. 그래서 기한이 지나면 서버가 대신 끝내고
     * 환불한다. 사유는 비워 둔다 (아무도 적지 않았으므로).
     *
     * <p><b>온체인 잠금은 서버가 풀지 않는다.</b> {@code cancelJob} 은 브랜드 지갑이나
     * DEFAULT_ADMIN_ROLE 만 부를 수 있는데, 서버에 개인키를 두면 이 시스템의 모든
     * 서명이 사용자 지갑에서 나온다는 전제가 깨진다. 대신 응답의
     * {@code chainReleaseRequired} 로 브랜드 화면이 해제를 안내한다.
     */
    @Transactional
    public void expireAcceptance(Long jobId) {
        ProcessingJob job = jobs.findForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
        if (!job.isAcceptanceDue(LocalDateTime.now(clock))) {
            return;
        }
        requestService.markDeclined(job.getProcessingRequestId(), null, null, true);
    }

    @Transactional(readOnly = true)
    public ProcessingJobResponse detail(Long actorId, Long jobId) {
        ProcessingJob job = jobs.findById(jobId)
                .orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
        if (!actorId.equals(job.getBrandId()) && !actorId.equals(job.getProcessorId())) {
            throw new BusinessException(PROCESSING_FORBIDDEN);
        }
        if (users.findById(actorId).filter(User::isActive).isEmpty()) {
            throw new BusinessException(PROCESSING_FORBIDDEN);
        }
        return ProcessingJobResponse.of(job);
    }

    /** 결제 기한이 지난 작업. 아직 돈이 오지 않았으므로 환불할 것이 없다. */
    @Transactional
    public void expire(Long jobId) {
        ProcessingJob job = jobs.findForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!job.isPaymentDue(now)) return;
        for (MaterialLock lock : locks.findByReferenceForUpdate(
                MaterialLockReferenceType.PROCESSING_JOB, jobId)) {
            if (lock.getStatus() == MaterialLockStatus.ACTIVE) lock.expire(now);
            eventRecorder.record(
                    lock.getMaterialId(), MaterialEventType.PROCESSING_JOB_EXPIRED, null,
                    MaterialEventReferenceType.PROCESSING_JOB, jobId, null, lock.getAmount(),
                    "가공 Job 결제 기한 만료",
                    MaterialEventRecorder.key(
                            MaterialEventType.PROCESSING_JOB_EXPIRED,
                            lock.getMaterialId(), jobId));
        }
        job.expire(now);
        businessNotifications.jobExpired(job);
    }

    private ProcessingJob lockedProcessorJob(Long actorId, Long jobId) {
        if (actorId == null || users.findById(actorId).filter(User::isActive)
                .filter(user -> user.getRole() == UserRole.PROCESSOR).isEmpty()) {
            throw new BusinessException(PROCESSING_FORBIDDEN);
        }
        ProcessingJob job = jobs.findForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
        if (!actorId.equals(job.getProcessorId())) {
            throw new BusinessException(PROCESSING_FORBIDDEN);
        }
        return job;
    }

    private void recordOnInputs(
            ProcessingJob job, MaterialEventType eventType, Long actorId, String note) {
        for (MaterialLock lock : locks.findByReferenceForUpdate(
                MaterialLockReferenceType.PROCESSING_JOB, job.getId())) {
            eventRecorder.record(
                    lock.getMaterialId(), eventType, actorId,
                    MaterialEventReferenceType.PROCESSING_JOB, job.getId(), null, lock.getAmount(),
                    note,
                    MaterialEventRecorder.key(eventType, lock.getMaterialId(), job.getId()));
        }
    }
}
