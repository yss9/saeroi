package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.*;
import com.ssafy.upcycle.material.repository.*;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.processing.repository.*;
import com.ssafy.upcycle.user.entity.*;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessingRequestService {
    private final UserRepository users;
    private final MaterialRepository materials;
    private final MaterialBalanceRepository balances;
    private final MaterialLockRepository locks;
    private final ProcessorCapabilityRepository capabilities;
    private final ProcessingRequestRepository requests;
    private final ProcessingRequestInputRepository inputs;
    private final ProcessingRequestTypeRepository types;
    private final Clock clock;
    private final MaterialEventRecorder eventRecorder;
    private final ProcessingNotificationService businessNotifications;

    @Transactional
    public ProcessingRequestResponse create(Long brandId, CreateProcessingRequest input) {
        requireActiveRole(brandId, UserRole.BRAND);
        validate(input);
        User processor = users.findById(input.processorId())
                .filter(u -> u.isActive() && u.getRole() == UserRole.PROCESSOR)
                .orElseThrow(() -> new BusinessException(PROCESSING_PROCESSOR_NOT_FOUND));
        Material material = materials.findById(input.materialId())
                .filter(m -> m.getStatus() == MaterialStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(PROCESSING_MATERIAL_NOT_FOUND));

        Set<ProcessType> supported = capabilities
                .findAllByProcessorProfileIdAndMaterialType(processor.getId(), material.getMaterialType())
                .stream().filter(c -> c.accepts(input.quantity()))
                .map(ProcessorCapability::getProcessType).collect(Collectors.toSet());
        if (!supported.containsAll(input.processTypes())) {
            throw new BusinessException(PROCESSING_CAPABILITY_MISMATCH);
        }

        // Every reservation writer must serialize on this same owner/material row.
        MaterialBalance balance = balances.findForUpdate(input.materialId(), brandId)
                .orElseThrow(() -> new BusinessException(PROCESSING_INSUFFICIENT_BALANCE));
        long available = balance.getQuantity();
        // Current locking read avoids a stale snapshot under MySQL REPEATABLE READ.
        var activeLocks = locks.findActiveForUpdate(input.materialId(), brandId);
        LocalDateTime now = LocalDateTime.now(clock);
        for (MaterialLock lock : activeLocks) {
            if (lock.getAmount() > available) {
                throw new BusinessException(PROCESSING_INSUFFICIENT_BALANCE);
            }
            available -= lock.getAmount();
        }
        if (input.quantity() > available) {
            throw new BusinessException(PROCESSING_INSUFFICIENT_BALANCE);
        }
        ProcessingRequest request = requests.save(ProcessingRequest.create(brandId, processor.getId(), now));
        inputs.save(new ProcessingRequestInput(request.getId(), material.getId(), input.quantity()));
        for (int i = 0; i < input.processTypes().size(); i++) {
            types.save(new ProcessingRequestType(request.getId(), input.processTypes().get(i), i + 1));
        }
        MaterialLock lock = locks.save(MaterialLock.create(material.getId(), brandId, input.quantity(),
                MaterialLockPurpose.PROCESSING, MaterialLockReferenceType.PROCESSING_REQUEST,
                request.getId(), request.getExpiresAt()));
        eventRecorder.record(
                material.getId(), MaterialEventType.PROCESSING_REQUESTED, brandId,
                MaterialEventReferenceType.PROCESSING_REQUEST, request.getId(), null,
                input.quantity(), "가공 요청 생성 및 수량 예약",
                MaterialEventRecorder.key(
                        MaterialEventType.PROCESSING_REQUESTED, material.getId(), request.getId()));
        businessNotifications.requestCreated(request, input.quantity());
        return ProcessingRequestResponse.of(request, input);
    }

    @Transactional
    public ProcessingRequestStatus cancel(Long actorId, Long requestId) {
        requireActiveRole(actorId, UserRole.BRAND);
        ProcessingRequest request = lockedRequest(requestId);
        if (!request.getBrandId().equals(actorId)) throw new BusinessException(PROCESSING_FORBIDDEN);
        return terminate(request, ProcessingRequestStatus.CANCELED, actorId);
    }

    @Transactional
    public ProcessingRequestStatus reject(Long actorId, Long requestId) {
        requireActiveRole(actorId, UserRole.PROCESSOR);
        ProcessingRequest request = lockedRequest(requestId);
        if (!actorId.equals(request.getProcessorId())) throw new BusinessException(PROCESSING_FORBIDDEN);
        return terminate(request, ProcessingRequestStatus.REJECTED, actorId);
    }

    @Transactional
    public void expire(Long requestId) {
        ProcessingRequest request = lockedRequest(requestId);
        if (request.isDue(LocalDateTime.now(clock))) {
            releaseAndTerminate(request, ProcessingRequestStatus.EXPIRED, null);
        }
    }

    private ProcessingRequestStatus terminate(
            ProcessingRequest request, ProcessingRequestStatus target, Long actorId) {
        if (request.isDue(LocalDateTime.now(clock))) {
            releaseAndTerminate(request, ProcessingRequestStatus.EXPIRED, null);
        } else if (request.getStatus() == ProcessingRequestStatus.QUOTING) {
            releaseAndTerminate(request, target, actorId);
        } else if (request.getStatus() != target && request.getStatus() != ProcessingRequestStatus.EXPIRED) {
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        return request.getStatus();
    }

    private void releaseAndTerminate(
            ProcessingRequest request, ProcessingRequestStatus target, Long actorId) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (MaterialLock lock : locks.findByReferenceForUpdate(
                MaterialLockReferenceType.PROCESSING_REQUEST, request.getId())) {
            if (lock.getStatus() == MaterialLockStatus.ACTIVE) {
                if (target == ProcessingRequestStatus.EXPIRED) lock.expire(now);
                else lock.release(now);
            }
            MaterialEventType eventType = switch (target) {
                case CANCELED -> MaterialEventType.PROCESSING_REQUEST_CANCELED;
                case REJECTED -> MaterialEventType.PROCESSING_REQUEST_REJECTED;
                case EXPIRED -> MaterialEventType.PROCESSING_REQUEST_EXPIRED;
                default -> throw new IllegalArgumentException("Unsupported processing terminal state");
            };
            eventRecorder.record(
                    lock.getMaterialId(), eventType, actorId,
                    MaterialEventReferenceType.PROCESSING_REQUEST, request.getId(), null,
                    lock.getAmount(), "가공 요청 종료: " + target,
                    MaterialEventRecorder.key(eventType, lock.getMaterialId(), request.getId()));
        }
        request.terminate(target);
        businessNotifications.requestTerminated(request, target, actorId);
    }

    private ProcessingRequest lockedRequest(Long id) {
        return requests.findForUpdate(id).orElseThrow(() -> new BusinessException(PROCESSING_REQUEST_NOT_FOUND));
    }

    private void requireActiveRole(Long id, UserRole role) {
        if (id == null || users.findById(id).filter(u -> u.isActive() && u.getRole() == role).isEmpty()) {
            throw new BusinessException(PROCESSING_FORBIDDEN);
        }
    }

    private void validate(CreateProcessingRequest input) {
        if (input == null || input.materialId() == null || input.materialId() <= 0
                || input.processorId() == null || input.processorId() <= 0
                || input.quantity() == null || input.quantity() <= 0
                || input.processTypes() == null || input.processTypes().isEmpty()
                || input.processTypes().stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(input.processTypes()).size() != input.processTypes().size()) {
            throw new BusinessException(PROCESSING_INVALID_REQUEST);
        }
    }
}
