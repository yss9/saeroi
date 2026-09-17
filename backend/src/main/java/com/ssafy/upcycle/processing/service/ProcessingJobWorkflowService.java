package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.material.entity.*;
import com.ssafy.upcycle.material.repository.*;
import com.ssafy.upcycle.material.service.*;
import com.ssafy.upcycle.notification.entity.NotificationType;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.processing.repository.*;
import com.ssafy.upcycle.user.entity.*;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ProcessingJobWorkflowService {
    private static final List<ProcessingJobStatus> CUSTODY_STATUSES = List.of(
            ProcessingJobStatus.SHIPPED,
            ProcessingJobStatus.RECEIVED,
            ProcessingJobStatus.PROCESSING,
            ProcessingJobStatus.RESULT_SUBMITTED,
            ProcessingJobStatus.RETURN_SHIPPED,
            ProcessingJobStatus.REJECTED);

    private final ProcessingJobRepository jobs;
    private final ProcessingInputRepository inputs;
    private final ProcessingOutputRepository outputs;
    private final MaterialLockRepository locks;
    private final MaterialBalanceRepository balances;
    private final MaterialRepository materials;
    private final UserRepository users;
    private final EvidenceFileRepository evidenceFiles;
    private final ProcessingSignatureService signatures;
    private final MaterialCodeGenerator materialCodeGenerator;
    private final MaterialIssuer materialIssuer;
    private final MaterialLineageRegistrar lineageRegistrar;
    private final MaterialEventRecorder eventRecorder;
    private final Clock clock;
    private final ProcessingNotificationService businessNotifications;

    @Transactional(readOnly = true)
    public List<ProcessingJobResponse> list(Long actorId) {
        requireActive(actorId);
        return jobs.findAllForActor(actorId).stream().map(this::response).toList();
    }

    /**
     * Processor에게 배송 중이거나 가공을 위해 보관 중인 소재를 반환한다.
     * 토큰 소유권과 Brand 잔액은 이동시키지 않는다.
     */
    @Transactional(readOnly = true)
    public List<ProcessingCustodyMaterialResponse> custodyMaterials(Long actorId) {
        requireRole(actorId, UserRole.PROCESSOR);
        return jobs.findCustodyMaterials(actorId, CUSTODY_STATUSES);
    }

    @Transactional(readOnly = true)
    public ProcessingJobResponse detail(Long actorId, Long jobId) {
        requireActive(actorId);
        ProcessingJob job = requireJob(jobId);
        requireParty(job, actorId);
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse ship(Long actorId, Long jobId, ProcessingJobShipRequest request) {
        ProcessingJob job = lockJob(jobId); requireBrand(job, actorId);
        if (job.getChainJobId() == null) throw new BusinessException(PROCESSING_CHAIN_NOT_LINKED);
        job.ship(request.carrier(), request.trackingNumber(), now());
        recordInput(job, MaterialEventType.PROCESSING_MATERIAL_SHIPPED, actorId, "가공 소재 발송");
        businessNotifications.jobEvent(job, NotificationType.PROCESSING_MATERIAL_SHIPPED);
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse receive(Long actorId, Long jobId) {
        ProcessingJob job = lockJob(jobId); requireProcessor(job, actorId);
        job.receive(now());
        recordInput(job, MaterialEventType.PROCESSING_MATERIAL_RECEIVED, actorId, "가공업체 소재 수령");
        businessNotifications.jobEvent(job, NotificationType.PROCESSING_MATERIAL_RECEIVED);
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse start(Long actorId, Long jobId) {
        ProcessingJob job = lockJob(jobId); requireProcessor(job, actorId);
        job.start(now());
        recordInput(job, MaterialEventType.PROCESSING_STARTED, actorId, "가공 시작");
        businessNotifications.jobEvent(job, NotificationType.PROCESSING_STARTED);
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse saveResult(Long actorId, Long jobId, ProcessingResultRequest request) {
        ProcessingJob job = lockJob(jobId); requireProcessor(job, actorId);
        ProcessingInput input = requireInput(jobId);
        long outputSum;
        try { outputSum = request.outputs().stream().mapToLong(ProcessingOutputRequest::quantity).reduce(0, Math::addExact); }
        catch (ArithmeticException overflow) { throw new BusinessException(PROCESSING_RESULT_INVALID); }
        if (outputSum > input.getQuantity())
            throw new BusinessException(PROCESSING_RESULT_INVALID);
        long lossQuantity = input.getQuantity() - outputSum;
        job.saveResultDraft(lossQuantity, now());
        outputs.deleteByProcessingJobId(jobId);
        outputs.saveAll(request.outputs().stream().map(row -> ProcessingOutput.draft(jobId, input.getId(),
                row.materialType(), row.grade(), row.gradeReason(), row.name(), row.quantity())).toList());
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse submitResult(Long actorId, Long jobId, ProcessingSignedCommandRequest request) {
        ProcessingJob job = lockJob(jobId); requireProcessor(job, actorId);
        if (evidenceFiles.countByReferenceTypeAndReferenceIdAndDeletedAtIsNull(
                EvidenceReferenceType.PROCESSING_JOB, jobId) < 1)
            throw new BusinessException(PROCESSING_EVIDENCE_REQUIRED);
        String signature = signatures.verify(actorId, job, ProcessingSignatureAction.RESULT_SUBMIT,
                request.challengeId(), request.signature(), null);
        job.submitResult(signature, now());
        recordInput(job, MaterialEventType.PROCESSING_RESULT_SUBMITTED, actorId, "가공 결과 제출");
        businessNotifications.jobEvent(job, NotificationType.PROCESSING_RESULT_SUBMITTED);
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse reject(Long actorId, Long jobId, RejectProcessingResultRequest request) {
        ProcessingJob job = lockJob(jobId); requireBrand(job, actorId);
        String signature = signatures.verify(actorId, job, ProcessingSignatureAction.REJECT,
                request.challengeId(), request.signature(), request.reason());
        job.reject(request.reason(), signature, now());
        recordInput(job, MaterialEventType.PROCESSING_RESULT_REJECTED, actorId, "가공 결과 반려: " + request.reason().trim());
        businessNotifications.jobEvent(job, NotificationType.PROCESSING_RESULT_REJECTED);
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse approve(Long actorId, Long jobId, ProcessingSignedCommandRequest request) {
        ProcessingJob job = lockJob(jobId); requireBrand(job, actorId);
        String signature = signatures.verify(actorId, job, ProcessingSignatureAction.APPROVE,
                request.challengeId(), request.signature(), null);
        ProcessingInput input = requireInput(jobId);
        List<ProcessingOutput> result = outputs.findAllByProcessingJobIdOrderById(jobId);
        if (result.isEmpty()) throw new BusinessException(PROCESSING_RESULT_INVALID);
        MaterialLock lock = locks.findByReferenceForUpdate(MaterialLockReferenceType.PROCESSING_JOB, jobId)
                .stream().filter(row -> row.getStatus() == MaterialLockStatus.ACTIVE).findFirst()
                .orElseThrow(() -> new BusinessException(PROCESSING_INVALID_STATE));
        MaterialBalance balance = balances.findForUpdate(input.getMaterialId(), actorId)
                .orElseThrow(() -> new BusinessException(PROCESSING_INSUFFICIENT_BALANCE));
        if (lock.getMaterialId() == null || !lock.getMaterialId().equals(input.getMaterialId())
                || !lock.getOwnerId().equals(actorId) || lock.getAmount() != input.getQuantity()
                || balance.getQuantity() < input.getQuantity()) throw new BusinessException(PROCESSING_INVALID_STATE);
        Material parent = materials.findById(input.getMaterialId())
                .orElseThrow(() -> new BusinessException(PROCESSING_MATERIAL_NOT_FOUND));

        LocalDateTime now = now();
        lock.consume(now); balance.decrease(input.getQuantity());
        eventRecorder.record(parent.getId(), MaterialEventType.MATERIAL_CONSUMED, actorId,
                MaterialEventReferenceType.PROCESSING_JOB, jobId, null, input.getQuantity(),
                "가공 결과 승인으로 투입 소재 소비",
                MaterialEventRecorder.key(MaterialEventType.MATERIAL_CONSUMED, parent.getId(), jobId));
        for (ProcessingOutput row : result) {
            Material child = materials.save(Material.produceProcessed(materialCodeGenerator.placeholder(), parent,
                    row.getMaterialType(), row.getName(), row.getGrade(), row.getQuantity(), job.getProcessorId()));
            child.assignCode(materialCodeGenerator.of(child.getId()));
            materialIssuer.issue(child.getId(), actorId, row.getQuantity());
            lineageRegistrar.link(child.getId(), parent.getId(), MaterialLineageRelationType.PROCESSING, jobId);
            row.linkMaterial(child.getId());
        }
        job.approve(signature, now);
        recordInput(job, MaterialEventType.PROCESSING_RESULT_APPROVED, actorId, "가공 결과 승인");
        businessNotifications.jobEvent(job, NotificationType.PROCESSING_RESULT_APPROVED);
        return response(job);
    }

    private ProcessingJobResponse response(ProcessingJob job) {
        return ProcessingJobResponse.of(job, requireInput(job.getId()),
                outputs.findAllByProcessingJobIdOrderById(job.getId()));
    }
    private ProcessingJob requireJob(Long id) { return jobs.findById(id).orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND)); }
    private ProcessingJob lockJob(Long id) { return jobs.findForUpdate(id).orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND)); }
    private ProcessingInput requireInput(Long jobId) { return inputs.findByProcessingJobId(jobId).orElseThrow(() -> new BusinessException(PROCESSING_INVALID_STATE)); }
    private void requireParty(ProcessingJob job, Long id) { if (!id.equals(job.getBrandId()) && !id.equals(job.getProcessorId())) throw new BusinessException(PROCESSING_FORBIDDEN); }
    private void requireBrand(ProcessingJob job, Long id) { requireRole(id, UserRole.BRAND); if (!id.equals(job.getBrandId())) throw new BusinessException(PROCESSING_FORBIDDEN); }
    private void requireProcessor(ProcessingJob job, Long id) { requireRole(id, UserRole.PROCESSOR); if (!id.equals(job.getProcessorId())) throw new BusinessException(PROCESSING_FORBIDDEN); }
    private void requireActive(Long id) { if (id == null || users.findById(id).filter(User::isActive).isEmpty()) throw new BusinessException(PROCESSING_FORBIDDEN); }
    private void requireRole(Long id, UserRole role) { if (id == null || users.findById(id).filter(User::isActive).filter(u -> u.getRole()==role).isEmpty()) throw new BusinessException(PROCESSING_FORBIDDEN); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private void recordInput(ProcessingJob job, MaterialEventType type, Long actorId, String summary) {
        ProcessingInput input = requireInput(job.getId());
        eventRecorder.record(input.getMaterialId(), type, actorId, MaterialEventReferenceType.PROCESSING_JOB,
                job.getId(), null, input.getQuantity(), summary,
                MaterialEventRecorder.key(type, input.getMaterialId(), job.getId(), (long) job.getResultRevision()));
    }
}
