package com.ssafy.upcycle.processing.service;

import static com.ssafy.upcycle.processing.ProcessingErrorCode.*;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.*;
import com.ssafy.upcycle.material.repository.*;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.processing.repository.*;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.payment.PaymentGateway;
import com.ssafy.upcycle.user.entity.*;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가공 요청. <b>요청과 작업이 한 번에 만들어진다.</b>
 *
 * <h2>견적 단계가 사라진 자리</h2>
 *
 * <p>예전에는 요청을 올리면 가공사가 견적을 내고, 브랜드가 그중 하나를 고르는 순간에야
 * 작업({@code processing_job})이 생겼다. 그래서 작업을 만드는 유일한 입구가
 * {@code POST /quotes/{id}/select} 였다.
 *
 * <p>지금은 브랜드가 검색 결과에서 가격과 소요일을 보고 바로 고르므로 받을 견적이 없다.
 * 요청이 곧 "이 조건으로 맡기겠다" 이고, 그러니 요청이 작업을 만든다. 만들어진 작업은
 * 결제를 기다리는 {@code CREATED} 상태이고, 응답의 {@code jobId}·{@code fee} 로 화면이
 * 곧장 결제로 넘어간다.
 *
 * <h2>금액은 서버가 정한다</h2>
 *
 * <p>화면이 보낸 금액을 받지 않는다. 가공사가 등록해 둔 kg 당 단가에 요청 수량을 곱해
 * 공정마다 올림한 뒤 더한다 ({@link ProcessingEstimate}). 검색 결과의
 * {@code estimatedPrice} 가 같은 식을 쓰므로 목록에서 본 금액과 청구 금액이 같다.
 */
@Service
@RequiredArgsConstructor
public class ProcessingRequestService {

    private final UserRepository users;
    private final UserWalletRepository wallets;
    private final MaterialRepository materials;
    private final MaterialBalanceRepository balances;
    private final MaterialLockRepository locks;
    private final ProcessorCapabilityRepository capabilities;
    private final ProcessingRequestRepository requests;
    private final ProcessingRequestInputRepository inputs;
    private final ProcessingRequestTypeRepository types;
    private final ProcessingJobRepository jobs;
    private final ProcessingInputRepository jobInputs;
    private final Clock clock;
    private final PaymentGateway gateway;
    private final MaterialEventRecorder eventRecorder;
    private final ProcessingNotificationService businessNotifications;
    private final ProcessingRefundService refunds;

    /**
     * 요청을 만들고, 같은 트랜잭션에서 작업과 소재 잠금까지 만든다.
     *
     * <p>순서가 있다. 요청 → 작업 → 잠금이고, 잠금은 요청을 가리켰다가 작업으로 옮겨 붙는다
     * ({@code attachToProcessingJob}). 잠금이 처음부터 작업을 가리킬 수 없는 것은
     * {@link MaterialLock} 의 생성 규칙이 요청을 요구하기 때문이고, 그 규칙은 잠금이
     * "요청 때문에 걸렸다" 는 사실을 남기기 위한 것이다.
     */
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

        long fee = feeOf(processor.getId(), material, input);

        // 지갑은 작업을 만드는 시점에 고정한다 — 나중에 바꿔도 이 작업의 서명 주소는 그대로다.
        UserWallet brandWallet = wallets.findByUserIdAndActiveTrue(brandId)
                .orElseThrow(() -> new BusinessException(PROCESSING_WALLET_NOT_FOUND));
        UserWallet processorWallet = wallets.findByUserIdAndActiveTrue(processor.getId())
                .orElseThrow(() -> new BusinessException(PROCESSING_WALLET_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        requireAvailable(input, brandId);

        // 요청과 작업이 같은 기한을 쓴다. 각자 만들면 결제창은 살아 있는데 요청이 먼저 죽는다.
        ProcessingRequest request = requests.saveAndFlush(ProcessingRequest.create(
                brandId, processor.getId(), now, now.plus(ProcessingJob.PAYMENT_WINDOW)));
        inputs.save(new ProcessingRequestInput(request.getId(), material.getId(), input.quantity()));
        for (int i = 0; i < input.processTypes().size(); i++) {
            types.save(new ProcessingRequestType(request.getId(), input.processTypes().get(i), i + 1));
        }

        ProcessingJob job = jobs.saveAndFlush(ProcessingJob.create(
                "PJ" + UUID.randomUUID().toString().replace("-", "").substring(0, 28),
                request.getId(), brandId, processor.getId(),
                brandWallet.getWalletAddress(), processorWallet.getWalletAddress(),
                fee, paymentMethod(), now));

        MaterialLock lock = locks.save(MaterialLock.create(material.getId(), brandId, input.quantity(),
                MaterialLockPurpose.PROCESSING, MaterialLockReferenceType.PROCESSING_REQUEST,
                request.getId(), request.getExpiresAt()));
        lock.attachToProcessingJob(request.getId(), job.getId(), job.getPaymentExpiresAt());
        jobInputs.save(new ProcessingInput(
                job.getId(), material.getId(), input.quantity(), lock.getId()));

        eventRecorder.record(
                material.getId(), MaterialEventType.PROCESSING_REQUESTED, brandId,
                MaterialEventReferenceType.PROCESSING_REQUEST, request.getId(), null,
                input.quantity(), "가공 요청 생성 및 수량 예약",
                MaterialEventRecorder.key(
                        MaterialEventType.PROCESSING_REQUESTED, material.getId(), request.getId()));
        businessNotifications.requestCreated(request, input.quantity());
        return ProcessingRequestResponse.of(request, job, input);
    }

    /**
     * 가공비. 요청한 공정 전부가 그 업체에 등록돼 있어야 한다.
     *
     * <p>하나라도 빠지면 거절한다 — 할 수 있는 공정만 골라 반쯤 맡기면, 브랜드는 나머지를
     * 어디에 맡겨야 하는지 모른 채 돈을 내게 된다.
     */
    private long feeOf(Long processorId, Material material, CreateProcessingRequest input) {
        Map<ProcessType, ProcessorCapability> terms = new EnumMap<>(ProcessType.class);
        capabilities.findAllByProcessorProfileIdAndMaterialType(processorId, material.getMaterialType())
                .forEach(capability -> terms.put(capability.getProcessType(), capability));

        long fee = 0;
        for (ProcessType processType : input.processTypes()) {
            ProcessorCapability capability = terms.get(processType);
            if (capability == null || capability.getDailyCapacity() <= 0) {
                throw new BusinessException(PROCESSING_CAPABILITY_MISMATCH);
            }
            fee += capability.feeFor(input.quantity());
        }
        return fee;
    }

    /**
     * 지금 이만큼을 더 잠글 수 있는가.
     *
     * <p>보유 행과 활성 잠금을 <b>모두 잠그고</b> 읽는다. 같은 소재로 두 요청이 동시에
     * 들어오면 둘 다 통과해 가용량보다 많이 잠긴다.
     */
    private void requireAvailable(CreateProcessingRequest input, Long brandId) {
        MaterialBalance balance = balances.findForUpdate(input.materialId(), brandId)
                .orElseThrow(() -> new BusinessException(PROCESSING_INSUFFICIENT_BALANCE));
        long available = balance.getQuantity();
        for (MaterialLock lock : locks.findActiveForUpdate(input.materialId(), brandId)) {
            if (lock.getAmount() > available) {
                throw new BusinessException(PROCESSING_INSUFFICIENT_BALANCE);
            }
            available -= lock.getAmount();
        }
        if (input.quantity() > available) {
            throw new BusinessException(PROCESSING_INSUFFICIENT_BALANCE);
        }
    }

    /**
     * 붙어 있는 결제 게이트웨이를 그대로 적는다.
     *
     * <p>화면이 고르게 두지 않는 이유는 환불 때문이다 — 모의 게이트웨이가 붙은 환경에서
     * {@code KAKAO_PAY} 로 기록되면, 가공사가 거절했을 때 있지도 않은 결제를 취소하려 들고
     * 그 실패가 거절 자체를 막는다 (제품 주문에서 실제로 났던 사고다).
     */
    private PaymentMethod paymentMethod() {
        return gateway.isLive() ? PaymentMethod.KAKAO_PAY : PaymentMethod.MOCK;
    }

    @Transactional
    public ProcessingRequestStatus cancel(Long actorId, Long requestId) {
        requireActiveRole(actorId, UserRole.BRAND);
        ProcessingRequest request = lockedRequest(requestId);
        if (!request.getBrandId().equals(actorId)) throw new BusinessException(PROCESSING_FORBIDDEN);
        return terminate(request, ProcessingRequestStatus.CANCELED, actorId);
    }

    /**
     * 가공사가 요청을 거절한다.
     *
     * <p>{@code PATCH /processing-jobs/{jobId}/reject} 와 같은 일을 한다 — 화면은 작업을 두고
     * 말하고 여기서는 요청을 두고 말하지만, 하나가 끝나면 다른 하나도 끝나므로 어느 쪽으로
     * 들어와도 같은 곳에 도착한다.
     */
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
            releaseAndTerminate(request, ProcessingRequestStatus.EXPIRED, null, null);
        }
    }

    private ProcessingRequestStatus terminate(
            ProcessingRequest request, ProcessingRequestStatus target, Long actorId) {
        if (request.isDue(LocalDateTime.now(clock))) {
            releaseAndTerminate(request, ProcessingRequestStatus.EXPIRED, null, null);
        } else if (request.getStatus() == ProcessingRequestStatus.REQUESTED) {
            releaseAndTerminate(request, target, actorId, null);
        } else if (request.getStatus() != target && request.getStatus() != ProcessingRequestStatus.EXPIRED) {
            throw new BusinessException(PROCESSING_INVALID_STATE);
        }
        return request.getStatus();
    }

    /**
     * 요청을 끝내면서 잠금을 풀고, <b>딸린 작업도 함께 끝낸다.</b>
     *
     * <p>요청과 작업이 한 몸으로 만들어졌으므로 한 몸으로 끝나야 한다. 요청만 끝내면
     * 결제를 기다리는 작업이 남아 브랜드가 이미 취소한 요청에 돈을 낼 수 있다.
     *
     * <p>결제가 끝난 뒤의 종료라면 환불이 함께 일어난다 — {@link ProcessingRefundService}.
     */
    private void releaseAndTerminate(
            ProcessingRequest request, ProcessingRequestStatus target, Long actorId, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        jobs.findByProcessingRequestId(request.getId())
                .ifPresent(job -> refunds.terminate(job, target, reason, actorId, now));
        for (MaterialLock lock : locks.findByReferenceForUpdate(
                MaterialLockReferenceType.PROCESSING_REQUEST, request.getId())) {
            releaseLock(lock, request, target, actorId, now);
        }
        for (MaterialLock lock : locks.findByReferenceForUpdate(
                MaterialLockReferenceType.PROCESSING_JOB, jobIdOf(request))) {
            releaseLock(lock, request, target, actorId, now);
        }
        request.terminate(target);
        businessNotifications.requestTerminated(request, target, actorId);
    }

    private Long jobIdOf(ProcessingRequest request) {
        return jobs.findByProcessingRequestId(request.getId())
                .map(ProcessingJob::getId)
                // 작업이 없는 요청은 지금 흐름에서 생기지 않지만, 옛 데이터에는 있을 수 있다.
                // 없는 id 로 조회하면 빈 목록이 오므로 아무 일도 일어나지 않는다.
                .orElse(-1L);
    }

    private void releaseLock(MaterialLock lock, ProcessingRequest request,
            ProcessingRequestStatus target, Long actorId, LocalDateTime now) {
        if (lock.getStatus() == MaterialLockStatus.ACTIVE) {
            // 만료로 끝나더라도 **잠금 자체에 기한이 남아 있을 때만** 만료로 적는다.
            // 결제가 끝난 작업의 잠금은 기한이 풀려 있어(clearExpiryForPaidProcessingJob)
            // 아직 due 가 아니고, 그때 expire 를 부르면 "Material lock is not due" 로 터져
            // 환불·정리가 통째로 롤백된다. 그 경우는 상위 작업이 끝나서 푸는 것이므로 해제다.
            if (target == ProcessingRequestStatus.EXPIRED && lock.isExpiredAt(now)) lock.expire(now);
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

    /**
     * 결제가 끝났다. 요청이 기다리는 대상을 결제에서 수락으로 바꾼다.
     *
     * <p>결제 승인과 같은 트랜잭션에서 불린다 — 나뉘면 결제는 됐는데 요청은 여전히 결제
     * 기한을 보고 있어서, 15분이 지난 순간 이미 결제된 요청이 만료된다.
     */
    @Transactional
    public void markAwaitingAcceptance(Long requestId, LocalDateTime acceptDeadline) {
        ProcessingRequest request = lockedRequest(requestId);
        if (request.getStatus() != ProcessingRequestStatus.REQUESTED) {
            return;
        }
        request.awaitAcceptance(acceptDeadline);
    }

    /**
     * 가공사가 요청을 받았다. 작업 쪽 입구({@code PATCH /processing-jobs/{id}/accept})가 부른다.
     */
    @Transactional
    public void markAccepted(Long requestId) {
        ProcessingRequest request = lockedRequest(requestId);
        if (request.getStatus() == ProcessingRequestStatus.ACCEPTED) {
            return;
        }
        request.accept();
    }

    /** 가공사가 일을 거절했다. 요청까지 함께 끝낸다. */
    @Transactional
    public void markDeclined(Long requestId, Long actorId, String reason, boolean byDeadline) {
        ProcessingRequest request = lockedRequest(requestId);
        if (request.getStatus() != ProcessingRequestStatus.REQUESTED) {
            return;
        }
        releaseAndTerminate(request,
                byDeadline ? ProcessingRequestStatus.EXPIRED : ProcessingRequestStatus.REJECTED,
                actorId, reason);
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
