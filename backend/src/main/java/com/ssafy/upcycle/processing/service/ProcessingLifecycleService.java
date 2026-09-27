package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.PROCESSING_CHAIN_NOT_LINKED;
import static com.ssafy.upcycle.processing.ProcessingErrorCode.PROCESSING_CHAIN_SHIPMENT_REQUIRED;
import static com.ssafy.upcycle.processing.ProcessingErrorCode.PROCESSING_FORBIDDEN;
import static com.ssafy.upcycle.processing.ProcessingErrorCode.PROCESSING_INVALID_STATE;
import static com.ssafy.upcycle.processing.ProcessingErrorCode.PROCESSING_JOB_NOT_FOUND;

import com.ssafy.upcycle.blockchain.service.ChainProcessingStatusReader;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.entity.ProcessingInput;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.repository.ProcessingInputRepository;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.notification.entity.NotificationType;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessingLifecycleService {
    private final ProcessingJobRepository jobs;
    /** 발송을 DB 에 반영하기 전에 체인에 서명이 남았는지 확인한다. */
    private final ChainProcessingStatusReader chainJobs;
    private final ProcessingInputRepository inputs;
    private final UserRepository users;
    private final MaterialEventRecorder eventRecorder;
    private final Clock clock;
    private final ProcessingNotificationService businessNotifications;

    @Transactional
    /**
     * 옛 발송 경로({@code POST /processing-jobs/{id}/shipment}).
     *
     * <p><b>여기에도 체인 가드가 필요하다.</b> 화면은 {@code PATCH /ship} 만 쓰지만 이 경로도
     * 살아 있는 인증 엔드포인트라, 가드를 한쪽에만 걸면 다른 문으로 그대로 지나간다. 오히려
     * 이쪽이 더 헐거웠다 — {@code chainJobId} 조차 보지 않았다.
     *
     * <p>막지 않으면 DB 는 {@code SHIPPED} 인데 체인은 {@code ACCEPTED} 로 남고, 며칠 뒤
     * 가공업체가 수령을 누를 때 컨트랙트가 거절한다. 막히는 사람이 담당자가 아니라 업체다.
     */
    public ProcessingJobResponse ship(Long actorId, Long jobId) {
        requireChainShipment(jobs.findById(jobId)
                .orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND)));
        // 결제가 아니라 수락된 작업만 보낸다 — 가공사가 받겠다고 답한 뒤여야 한다.
        return transition(actorId, jobId, UserRole.BRAND, ProcessingJobStatus.ACCEPTED,
                job -> job.ship(now()), MaterialEventType.PROCESSING_MATERIAL_SHIPPED,
                "가공업체로 소재 발송");
    }

    /**
     * 체인에 발송 서명이 남았는지. {@code ProcessingJobWorkflowService.requireChainShipment} 와
     * 같은 판정이다 — 두 곳이 다르게 판정하면 한쪽만 고쳐질 때 구멍이 난다.
     */
    private void requireChainShipment(ProcessingJob job) {
        if (!chainJobs.canRead()) {
            // 체인을 아예 쓰지 않는 환경이다. 어긋날 대상이 없으므로 막지 않는다.
            return;
        }
        if (job.getChainJobId() == null) {
            throw new BusinessException(PROCESSING_CHAIN_NOT_LINKED);
        }
        if (!chainJobs.alreadyShipped(job.getChainJobId())) {
            throw new BusinessException(PROCESSING_CHAIN_SHIPMENT_REQUIRED);
        }
    }

    @Transactional
    public ProcessingJobResponse receive(Long actorId, Long jobId) {
        return transition(actorId, jobId, UserRole.PROCESSOR, ProcessingJobStatus.SHIPPED,
                job -> job.receive(now()), MaterialEventType.PROCESSING_MATERIAL_RECEIVED,
                "가공업체 소재 수령");
    }

    @Transactional
    public ProcessingJobResponse start(Long actorId, Long jobId) {
        return transition(actorId, jobId, UserRole.PROCESSOR, ProcessingJobStatus.RECEIVED,
                ProcessingJob::startProcessing, MaterialEventType.PROCESSING_STARTED,
                "가공 시작");
    }

    private ProcessingJobResponse transition(
            Long actorId,
            Long jobId,
            UserRole role,
            ProcessingJobStatus expected,
            Consumer<ProcessingJob> action,
            MaterialEventType eventType,
            String description) {
        requireActiveRole(actorId, role);
        ProcessingJob job = jobs.findForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
        Long ownerId = role == UserRole.BRAND ? job.getBrandId() : job.getProcessorId();
        if (!actorId.equals(ownerId)) throw new BusinessException(PROCESSING_FORBIDDEN);
        if (job.getStatus() != expected) throw new BusinessException(PROCESSING_INVALID_STATE);
        ProcessingInput input = inputs.findByProcessingJobId(jobId)
                .orElseThrow(() -> new BusinessException(PROCESSING_INVALID_STATE));
        action.accept(job);
        eventRecorder.record(
                input.getMaterialId(), eventType, actorId,
                MaterialEventReferenceType.PROCESSING_JOB, jobId, null, input.getQuantity(),
                description, MaterialEventRecorder.key(eventType, input.getMaterialId(), jobId));
        businessNotifications.jobEvent(job, notificationType(eventType));
        return ProcessingJobResponse.of(job);
    }

    private NotificationType notificationType(MaterialEventType eventType) {
        return switch (eventType) {
            case PROCESSING_MATERIAL_SHIPPED -> NotificationType.PROCESSING_MATERIAL_SHIPPED;
            case PROCESSING_MATERIAL_RECEIVED -> NotificationType.PROCESSING_MATERIAL_RECEIVED;
            case PROCESSING_STARTED -> NotificationType.PROCESSING_STARTED;
            default -> throw new IllegalArgumentException("Unsupported processing lifecycle event");
        };
    }

    private void requireActiveRole(Long actorId, UserRole role) {
        User actor = actorId == null ? null : users.findById(actorId).orElse(null);
        if (actor == null || !actor.isActive() || actor.getRole() != role) {
            throw new BusinessException(PROCESSING_FORBIDDEN);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
