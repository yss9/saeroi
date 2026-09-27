package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;

import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.service.ChainTransactionRecorder;
import com.ssafy.upcycle.blockchain.service.ProcessingChainTxFinder;
import com.ssafy.upcycle.blockchain.service.ProcessingReceiptVerifier;
import com.ssafy.upcycle.blockchain.service.ProcessingReceiptVerifier.Action;
import com.ssafy.upcycle.blockchain.service.ProcessingReceiptVerifier.VerifiedReceipt;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
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
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProcessingChainService {
    private final ProcessingJobRepository jobs;
    private final ProcessingRequestRepository requests;
    private final ProcessingRequestTypeRepository requestTypes;
    private final ProcessingInputRepository inputs;
    private final ProcessingOutputRepository outputs;
    private final MaterialLockRepository locks;
    private final MaterialBalanceRepository balances;
    private final MaterialRepository materials;
    private final UserRepository users;
    private final MaterialCodeGenerator materialCodeGenerator;
    private final MaterialBalanceInitializer balanceInitializer;
    private final MaterialLineageRegistrar lineageRegistrar;
    private final MaterialEventRecorder eventRecorder;
    private final ProcessingNotificationService businessNotifications;
    private final ChainTransactionRecorder recorder;
    private final ProcessingReceiptVerifier receiptVerifier;
    private final ProcessingChainTxFinder txFinder;
    private final EvidenceFileRepository evidenceFiles;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProcessingChainPreparationResponse prepare(Long actorId, Long jobId) {
        ProcessingJob job = jobs.findById(jobId)
                .orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND));
        if (!job.getBrandId().equals(actorId) && !job.getProcessorId().equals(actorId))
            throw new BusinessException(PROCESSING_FORBIDDEN);
        ProcessingInput input = requireInput(jobId);
        Material material = materials.findById(input.getMaterialId())
                .orElseThrow(() -> new BusinessException(PROCESSING_MATERIAL_NOT_FOUND));
        // **원인을 말하는 코드로 던진다.** PROCESSING_INVALID_STATE 는 이 파일에서만 열 곳
        // 넘게 쓰여 "현재 상태에서는 가공 요청을 변경할 수 없습니다" 하나로 뭉쳐 나간다.
        // 자투리 500g 으로 가공을 맡겼다가 인계 서명에서 그 문구를 받은 담당자는 무엇이
        // 잘못됐는지 알 수 없었다 — 이미 가공비를 결제하고 업체가 수락한 뒤였다.
        if (material.getTokenId() == null) {
            throw new BusinessException(PROCESSING_MATERIAL_NOT_ON_CHAIN);
        }
        requests.findById(job.getProcessingRequestId())
                .orElseThrow(() -> new BusinessException(PROCESSING_REQUEST_NOT_FOUND));
        return new ProcessingChainPreparationResponse(job.getId(), job.getChainJobId(), job.getStatus(),
                job.getBrandWallet(), job.getProcessorWallet(), material.getTokenId(), input.getQuantity(),
                requestTypes.findAllByProcessingRequestIdOrderBySequenceNoAsc(job.getProcessingRequestId())
                        .stream().map(ProcessingRequestType::getProcessType).toList());
    }

    @Transactional
    public ProcessingJobResponse linkJob(Long actorId, Long jobId, ProcessingChainJobLinkRequest request) {
        ProcessingJob job = lockJob(jobId); requireBrand(job, actorId);
        // 결제 전에는 온체인에 올리지 않는다 — 돈이 오지 않은 일에 소재를 잠그면
        // 브랜드가 스스로 잠근 재고를 풀 방법이 결제 취소밖에 없어진다.
        // ACCEPTED 를 남기는 이유: 이 변경이 배포되는 순간 이미 ACCEPTED 인데 온체인
        // 등록이 안 된 작업이 떠 있을 수 있다. 그것들이 막히면 안 된다.
        if (job.getStatus() != ProcessingJobStatus.PAID && job.getStatus() != ProcessingJobStatus.ACCEPTED) {
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        if (job.getChainJobId() != null) {
            if (job.getChainJobId().equals(request.onChainId())) return response(job);
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        MaterialLock lock = activeLock(jobId);
        lock.linkChainLock(request.chainLockId());
        job.linkChainJob(request.onChainId());
        record(actorId, "createJob", jobId, request.txHash());
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse receive(Long actorId, Long jobId, ProcessingReceiveConfirmRequest request) {
        ProcessingJob job = lockJob(jobId); requireProcessor(job, actorId);
        if (job.getReceiveTxHash() != null) {
            if (sameOrMissing(job.getReceiveTxHash(), request.txHash())) return response(job);
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        if (job.getChainJobId() == null || job.getStatus() != ProcessingJobStatus.SHIPPED)
            throw new BusinessException(PROCESSING_INVALID_STATE);
        ProcessingInput input = requireInput(jobId);
        String txHash = signedTx(request.txHash(), job, Action.RECEIVE);
        VerifiedReceipt receipt = receiptVerifier.verifyReceipt(txHash, job.getChainJobId(),
                job.getProcessorWallet(), Action.RECEIVE, null, 1);
        MaterialLock brandLock = exactActiveLock(input.getBrandMaterialLockId());
        if (!brandLock.getOwnerId().equals(job.getBrandId()) || !brandLock.getMaterialId().equals(input.getMaterialId()))
            throw new BusinessException(PROCESSING_INVALID_STATE);
        MaterialBalance brand = balances.findForUpdate(input.getMaterialId(), job.getBrandId())
                .orElseThrow(() -> new BusinessException(PROCESSING_INSUFFICIENT_BALANCE));
        MaterialBalance processor = balances.findForUpdate(input.getMaterialId(), job.getProcessorId())
                .orElseGet(() -> balances.save(MaterialBalance.of(input.getMaterialId(), job.getProcessorId(),
                        job.getProcessorWallet(), 0)));
        LocalDateTime now = now();
        brandLock.consume(now); brand.decrease(input.getQuantity()); processor.increase(input.getQuantity());
        MaterialLock replacement = locks.save(MaterialLock.create(input.getMaterialId(), job.getProcessorId(),
                input.getQuantity(), MaterialLockPurpose.PROCESSING, MaterialLockReferenceType.PROCESSING_JOB,
                jobId, null));
        replacement.linkChainLock(receipt.processorLockId());
        input.linkProcessorLock(replacement.getId());
        job.receiveOnChain(txHash, receipt.blockNumber(), now);
        record(actorId, "confirmReceipt", jobId, txHash);
        eventRecorder.record(input.getMaterialId(), MaterialEventType.OWNERSHIP_TRANSFERRED, actorId,
                MaterialEventReferenceType.PROCESSING_JOB, jobId, null, input.getQuantity(),
                "가공업체 수령 확인으로 입력 소재 소유권 이전",
                MaterialEventRecorder.key(MaterialEventType.OWNERSHIP_TRANSFERRED, input.getMaterialId(), jobId));
        businessNotifications.jobEvent(job, NotificationType.PROCESSING_MATERIAL_RECEIVED);
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse shipReturn(Long actorId, Long jobId, ProcessingReturnShipRequest request) {
        ProcessingJob job = lockJob(jobId); requireProcessor(job, actorId);
        if (job.getReturnShipTxHash() != null) {
            if (job.getReturnShipTxHash().equalsIgnoreCase(request.txHash())) return response(job);
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        EvidenceFile evidence = requireEvidence(actorId, jobId, request.evidenceFileId());
        VerifiedReceipt receipt = receiptVerifier.verifyReceipt(request.txHash(), job.getChainJobId(),
                job.getProcessorWallet(), Action.RETURN_SHIP, evidence.getFileHash(), 1);
        job.shipReturn(request.carrier(), request.trackingNumber(), evidence.getFileHash(),
                request.txHash(), receipt.blockNumber(), now());
        record(actorId, "markReturnShipped", jobId, request.txHash());
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse receiveReturn(Long actorId, Long jobId, ProcessingReturnReceiveRequest request) {
        ProcessingJob job = lockJob(jobId); requireBrand(job, actorId);
        if (job.getReturnReceiveTxHash() != null) {
            if (sameOrMissing(job.getReturnReceiveTxHash(), request.txHash())) return response(job);
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        ProcessingInput input = requireInput(jobId);
        List<ProcessingOutput> result = outputs.findAllByProcessingJobIdOrderById(jobId);
        if (job.getStatus() != ProcessingJobStatus.RETURN_SHIPPED || result.isEmpty())
            throw new BusinessException(PROCESSING_RESULT_INVALID);
        String txHash = signedTx(request.txHash(), job, Action.APPROVE);
        VerifiedReceipt receipt = receiptVerifier.verifyReceipt(txHash, job.getChainJobId(),
                job.getBrandWallet(), Action.APPROVE, null, result.size());
        MaterialLock lock = exactActiveLock(input.getProcessorMaterialLockId());
        MaterialBalance processor = balances.findForUpdate(input.getMaterialId(), job.getProcessorId())
                .orElseThrow(() -> new BusinessException(PROCESSING_INSUFFICIENT_BALANCE));
        if (!lock.getOwnerId().equals(job.getProcessorId()) || lock.getAmount() != input.getQuantity())
            throw new BusinessException(PROCESSING_INVALID_STATE);
        Material parent = materials.findById(input.getMaterialId())
                .orElseThrow(() -> new BusinessException(PROCESSING_MATERIAL_NOT_FOUND));
        LocalDateTime now = now();
        lock.consume(now); processor.decrease(input.getQuantity());
        eventRecorder.record(parent.getId(), MaterialEventType.MATERIAL_CONSUMED, actorId,
                MaterialEventReferenceType.PROCESSING_JOB, jobId, null, input.getQuantity(),
                "가공 결과 수령으로 Processor 입력 소재 소비",
                MaterialEventRecorder.key(MaterialEventType.MATERIAL_CONSUMED, parent.getId(), jobId));
        for (int index = 0; index < result.size(); index++) {
            ProcessingOutput row = result.get(index);
            Material child = materials.save(Material.produceProcessed(materialCodeGenerator.placeholder(), parent,
                    row.getMaterialType(), parent.getName(), row.getGrade(), row.getQuantity(), job.getProcessorId()));
            child.assignCode(materialCodeGenerator.of(child.getId()));
            child.linkOnChain(receipt.outputTokenIds().get(index), recorder.chainId(), recorder.materialTokenAddress());
            balanceInitializer.open(child.getId(), actorId, row.getQuantity());
            lineageRegistrar.link(child.getId(), parent.getId(), MaterialLineageRelationType.PROCESSING, jobId);
            row.linkMaterial(child.getId());
        }
        job.confirmReturn(txHash, receipt.blockNumber(), null, now);
        record(actorId, "approveProcessingResult", jobId, txHash);
        eventRecorder.record(parent.getId(), MaterialEventType.PROCESSING_RESULT_APPROVED, actorId,
                MaterialEventReferenceType.PROCESSING_JOB, jobId, null, input.getQuantity(),
                "Brand 가공 결과 수령 및 승인",
                MaterialEventRecorder.key(MaterialEventType.PROCESSING_RESULT_APPROVED, parent.getId(), jobId));
        businessNotifications.jobEvent(job, NotificationType.PROCESSING_RESULT_APPROVED);
        return response(job);
    }

    @Transactional
    public ProcessingJobResponse returnRejected(Long actorId, Long jobId, ProcessingRejectedReturnRequest request) {
        ProcessingJob job = lockJob(jobId); requireProcessor(job, actorId);
        if (job.getReturnShipTxHash() != null) {
            if (job.getReturnShipTxHash().equalsIgnoreCase(request.txHash())) return response(job);
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        if (job.getChainJobId() == null || job.getStatus() != ProcessingJobStatus.REJECTED)
            throw new BusinessException(PROCESSING_INVALID_STATE);
        ProcessingInput input = requireInput(jobId);
        EvidenceFile evidence = requireEvidence(actorId, jobId, request.evidenceFileId());
        VerifiedReceipt receipt = receiptVerifier.verifyReceipt(request.txHash(), job.getChainJobId(),
                job.getProcessorWallet(), Action.REJECTED_RETURN, evidence.getFileHash(), 1);
        MaterialLock lock = exactActiveLock(input.getProcessorMaterialLockId());
        if (!lock.getOwnerId().equals(job.getProcessorId()) || lock.getAmount() != input.getQuantity())
            throw new BusinessException(PROCESSING_INVALID_STATE);
        MaterialBalance processor = balances.findForUpdate(input.getMaterialId(), job.getProcessorId())
                .orElseThrow(() -> new BusinessException(PROCESSING_INSUFFICIENT_BALANCE));
        MaterialBalance brand = balances.findForUpdate(input.getMaterialId(), job.getBrandId())
                .orElseGet(() -> balances.save(MaterialBalance.of(input.getMaterialId(), job.getBrandId(),
                        job.getBrandWallet(), 0)));
        LocalDateTime now = now();
        lock.consume(now);
        processor.decrease(input.getQuantity());
        brand.increase(input.getQuantity());
        job.cancelAfterRejectedReturn(request.carrier(), request.trackingNumber(), evidence.getFileHash(),
                request.txHash(), receipt.blockNumber(), now);
        record(actorId, "returnRejectedInput", jobId, request.txHash());
        eventRecorder.record(input.getMaterialId(), MaterialEventType.PROCESSING_INPUT_RETURNED, actorId,
                MaterialEventReferenceType.PROCESSING_JOB, jobId, null, input.getQuantity(),
                "반려된 가공 입력 소재를 Brand에게 반환",
                MaterialEventRecorder.key(MaterialEventType.PROCESSING_INPUT_RETURNED, input.getMaterialId(), jobId));
        return response(job);
    }

    /**
     * 이 단계의 서명 거래. 화면이 해시를 보냈으면 그것을, 안 보냈으면 <b>체인에서 찾는다.</b>
     *
     * <p>서명이 체인에 들어간 뒤 서버 호출이 실패하면 화면은 해시를 잃는다. 다시 서명하려 하면 체인이
     * 이미 그 단계를 지나 되돌리므로, 해시 없이 다시 부르면 서버가 그 거래를 찾아 이어 간다. 어느 쪽이든
     * 뒤의 영수증 검증은 같다 — 서명자·컨트랙트·작업 번호를 다시 본다.
     */
    private String signedTx(String requested, ProcessingJob job, Action action) {
        if (StringUtils.hasText(requested)) {
            return requested;
        }
        return txFinder.find(job.getId(), job.getChainJobId(), action)
                .orElseThrow(() -> new BusinessException(PROCESSING_CHAIN_RECORD_NOT_FOUND));
    }

    /** 이미 기록된 단계에 해시 없이 다시 왔다면, 따라잡으려던 요청이라 기록된 그대로 돌려준다. */
    private static boolean sameOrMissing(String recorded, String requested) {
        return !StringUtils.hasText(requested) || recorded.equalsIgnoreCase(requested);
    }

    private void record(Long actorId, String function, Long jobId, String hash) {
        recorder.record(actorId, ChainTransactionRecorder.Contract.PROCESSING_REGISTRY, function,
                BlockchainReferenceType.PROCESSING, jobId, hash);
    }
    private ProcessingJob lockJob(Long id) { return jobs.findForUpdate(id).orElseThrow(() -> new BusinessException(PROCESSING_JOB_NOT_FOUND)); }
    private ProcessingInput requireInput(Long id) { return inputs.findByProcessingJobId(id).orElseThrow(() -> new BusinessException(PROCESSING_INVALID_STATE)); }
    private MaterialLock activeLock(Long id) { return locks.findByReferenceForUpdate(MaterialLockReferenceType.PROCESSING_JOB, id).stream().filter(l -> l.getStatus()==MaterialLockStatus.ACTIVE).findFirst().orElseThrow(() -> new BusinessException(PROCESSING_INVALID_STATE)); }
    private MaterialLock exactActiveLock(Long lockId) {
        if (lockId == null) throw new BusinessException(PROCESSING_INVALID_STATE);
        return locks.findByIdForUpdate(lockId).filter(l -> l.getStatus() == MaterialLockStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(PROCESSING_INVALID_STATE));
    }
    private EvidenceFile requireEvidence(Long actorId, Long jobId, Long evidenceFileId) {
        EvidenceFile evidence = evidenceFiles.findByIdAndDeletedAtIsNull(evidenceFileId)
                .orElseThrow(() -> new BusinessException(PROCESSING_RESULT_INVALID));
        if (evidence.getReferenceType() != EvidenceReferenceType.PROCESSING_JOB
                || !evidence.getReferenceId().equals(jobId)
                || !evidence.getUploadedBy().equals(actorId)) {
            throw new BusinessException(PROCESSING_FORBIDDEN);
        }
        return evidence;
    }
    private void requireBrand(ProcessingJob j, Long id) { requireRole(id, UserRole.BRAND); if (!j.getBrandId().equals(id)) throw new BusinessException(PROCESSING_FORBIDDEN); }
    private void requireProcessor(ProcessingJob j, Long id) { requireRole(id, UserRole.PROCESSOR); if (!j.getProcessorId().equals(id)) throw new BusinessException(PROCESSING_FORBIDDEN); }
    private void requireRole(Long id, UserRole role) { if (users.findById(id).filter(User::isActive).filter(u -> u.getRole()==role).isEmpty()) throw new BusinessException(PROCESSING_FORBIDDEN); }
    private ProcessingJobResponse response(ProcessingJob j) { return ProcessingJobResponse.of(j, requireInput(j.getId()), outputs.findAllByProcessingJobIdOrderById(j.getId())); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
