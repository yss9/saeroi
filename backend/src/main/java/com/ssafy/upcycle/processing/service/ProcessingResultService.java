package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.material.service.MaterialCodeGenerator;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.material.service.MaterialIssuer;
import com.ssafy.upcycle.material.service.MaterialLineageRegistrar;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.dto.ProcessingOutputRequest;
import com.ssafy.upcycle.processing.dto.SubmitProcessingResultRequest;
import com.ssafy.upcycle.processing.entity.ProcessingInput;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.entity.ProcessingOutput;
import com.ssafy.upcycle.processing.repository.ProcessingInputRepository;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.processing.repository.ProcessingOutputRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessingResultService {
    private final ProcessingJobRepository jobs;
    private final ProcessingInputRepository inputs;
    private final ProcessingOutputRepository outputs;
    private final MaterialRepository materials;
    private final MaterialBalanceRepository balances;
    private final MaterialLockRepository locks;
    private final MaterialCodeGenerator codeGenerator;
    private final MaterialIssuer materialIssuer;
    private final MaterialLineageRegistrar lineageRegistrar;
    private final MaterialEventRecorder eventRecorder;
    private final UserRepository users;
    private final Clock clock;

    @Transactional
    public ProcessingJobResponse submit(
            Long actorId, Long jobId, SubmitProcessingResultRequest request) {
        requireActiveRole(actorId, UserRole.PROCESSOR);
        ProcessingJob job = lockedJob(jobId);
        if (!actorId.equals(job.getProcessorId())) throw new BusinessException(PROCESSING_FORBIDDEN);
        if (job.getStatus() != ProcessingJobStatus.PROCESSING) {
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        ProcessingInput input = requireInput(jobId);
        long outputQuantity = sumOutputQuantity(request.outputs());
        if (outputQuantity > input.getQuantity()) {
            throw new BusinessException(PROCESSING_OUTPUT_INVALID);
        }

        for (ProcessingOutputRequest output : request.outputs()) {
            outputs.save(ProcessingOutput.submit(
                    jobId, input.getId(), output.materialType(), output.name(), output.grade(),
                    output.quantity()));
        }
        job.submitResult(
                input.getQuantity(), outputQuantity, request.evidenceHash(), request.manifestUrl(), now());
        eventRecorder.record(
                input.getMaterialId(), MaterialEventType.PROCESSING_RESULT_SUBMITTED, actorId,
                MaterialEventReferenceType.PROCESSING_JOB, jobId, null, outputQuantity,
                "가공 결과 제출", MaterialEventRecorder.key(
                        MaterialEventType.PROCESSING_RESULT_SUBMITTED, input.getMaterialId(), jobId));
        return ProcessingJobResponse.of(job);
    }

    @Transactional
    public ProcessingJobResponse confirm(Long actorId, Long jobId) {
        requireActiveRole(actorId, UserRole.BRAND);
        ProcessingJob job = lockedJob(jobId);
        if (!actorId.equals(job.getBrandId())) throw new BusinessException(PROCESSING_FORBIDDEN);
        if (job.getStatus() != ProcessingJobStatus.RESULT_SUBMITTED) {
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }

        ProcessingInput input = requireInput(jobId);
        List<ProcessingOutput> submitted = outputs.findAllByProcessingJobIdOrderById(jobId);
        if (submitted.isEmpty() || submitted.stream().anyMatch(output -> output.getOutputMaterialId() != null)) {
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        Material parent = materials.findById(input.getMaterialId())
                .orElseThrow(() -> new BusinessException(PROCESSING_MATERIAL_NOT_FOUND));
        MaterialLock lock = requireActiveLock(jobId, input.getMaterialLockId());
        MaterialBalance balance = balances.findForUpdate(input.getMaterialId(), actorId)
                .orElseThrow(() -> new BusinessException(PROCESSING_BALANCE_INVALID));
        if (balance.getQuantity() < input.getQuantity()) {
            throw new BusinessException(PROCESSING_BALANCE_INVALID);
        }

        LocalDateTime confirmedAt = now();
        lock.consume(confirmedAt);
        balance.decrease(input.getQuantity());
        eventRecorder.record(
                parent.getId(), MaterialEventType.MATERIAL_CONSUMED, actorId,
                MaterialEventReferenceType.PROCESSING_JOB, jobId, null, input.getQuantity(),
                "가공 결과 승인으로 원소재 소비", MaterialEventRecorder.key(
                        MaterialEventType.MATERIAL_CONSUMED, parent.getId(), jobId));

        for (ProcessingOutput output : submitted) {
            Material child = materials.save(Material.process(
                    codeGenerator.placeholder(), parent, output.getMaterialType(), output.getName(),
                    output.getGrade(), output.getQuantity(), job.getProcessorId()));
            child.assignCode(codeGenerator.of(child.getId()));
            materialIssuer.issue(child.getId(), actorId, output.getQuantity());
            lineageRegistrar.link(
                    child.getId(), parent.getId(), MaterialLineageRelationType.PROCESSING, jobId);
            output.linkMaterial(child.getId());
        }

        job.confirmResult(confirmedAt);
        eventRecorder.record(
                parent.getId(), MaterialEventType.PROCESSING_RESULT_CONFIRMED, actorId,
                MaterialEventReferenceType.PROCESSING_JOB, jobId, null,
                input.getQuantity() - job.getLossQuantity(), "가공 결과 승인",
                MaterialEventRecorder.key(
                        MaterialEventType.PROCESSING_RESULT_CONFIRMED, parent.getId(), jobId));
        return ProcessingJobResponse.of(job);
    }

    private ProcessingJob lockedJob(Long jobId) {
        return jobs.findForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
    }

    private ProcessingInput requireInput(Long jobId) {
        return inputs.findByProcessingJobId(jobId)
                .orElseThrow(() -> new BusinessException(PROCESSING_INVALID_STATE));
    }

    private MaterialLock requireActiveLock(Long jobId, Long lockId) {
        return locks.findByReferenceForUpdate(MaterialLockReferenceType.PROCESSING_JOB, jobId)
                .stream()
                .filter(lock -> lock.getId().equals(lockId))
                .filter(lock -> lock.getStatus() == MaterialLockStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new BusinessException(PROCESSING_LOCK_NOT_ACTIVE));
    }

    private long sumOutputQuantity(List<ProcessingOutputRequest> submitted) {
        try {
            long total = 0;
            for (ProcessingOutputRequest output : submitted) {
                total = Math.addExact(total, output.quantity());
            }
            return total;
        } catch (ArithmeticException exception) {
            throw new BusinessException(PROCESSING_OUTPUT_INVALID);
        }
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
