package com.ssafy.upcycle.processing.service;

import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.entity.ProcessingRequestStatus;
import com.ssafy.upcycle.processing.repository.ProcessingInputRepository;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.payment.PaymentCancelCommand;
import com.ssafy.upcycle.trade.payment.PaymentGateway;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 시작 전에 끝난 가공 작업의 뒷정리 — <b>상태를 닫고 돈을 돌려준다.</b>
 *
 * <p>요청 시 결제로 정했기 때문에 필요해진 자리다. 브랜드가 먼저 돈을 내고 가공사가 나중에
 * 답하므로, 가공사가 거절하거나 기한 안에 답하지 않으면 <b>받은 적 없는 일에 대한 돈</b>이
 * 남는다. 그걸 돌려주지 않으면 거절이 곧 갈취가 된다.
 *
 * <p>요청 쪽({@code ProcessingRequestService})과 작업 쪽({@code ProcessingJobService})이
 * 모두 여기를 부른다. 두 입구가 각자 환불을 적으면 한쪽만 고쳐질 때 다른 쪽이 조용히
 * 돈을 삼킨다.
 *
 * <h2>{@code MOCK} 결제는 건너뛴다</h2>
 *
 * <p>모의 게이트웨이가 붙은 환경의 결제는 카카오페이에 존재하지 않는다. 그 tid 로 취소를
 * 부르면 게이트웨이가 502 를 내고, <b>그 실패가 거절 자체를 막는다</b> — 제품 주문에서
 * 실제로 났던 사고다 ({@code ProductOrderPaymentService#refundIfPaid} 에 같은 판단이 있다).
 */
@Service
@RequiredArgsConstructor
public class ProcessingRefundService {

    private final PaymentGateway gateway;
    private final ProcessingInputRepository jobInputs;
    private final MaterialEventRecorder eventRecorder;
    private final ProcessingNotificationService businessNotifications;

    /**
     * 작업을 끝내고 필요하면 환불한다.
     *
     * <p>이미 끝난 작업이면 아무 일도 하지 않는다 — 요청 만료와 작업 만료가 거의 동시에
     * 들어올 수 있고, 그때 두 번 환불되면 돌려준 돈을 또 돌려준다.
     *
     * <p><b>환불을 먼저 하고 상태를 닫는다.</b> 순서를 뒤집으면 게이트웨이가 실패했을 때
     * 이미 닫힌 작업이 남고, 그 작업은 다시 환불할 입구가 없다. 환불이 실패하면 예외가
     * 트랜잭션 전체를 되돌리므로 요청도 작업도 원래대로 남는다.
     */
    public void terminate(
            ProcessingJob job,
            ProcessingRequestStatus requestStatus,
            String reason,
            Long actorId,
            LocalDateTime now) {
        if (!job.isBeforeStart()) {
            return;
        }
        if (job.isRefundable()) {
            if (job.getPaymentMethod() != PaymentMethod.MOCK) {
                gateway.cancel(new PaymentCancelCommand(
                        job.getPaymentReference(), job.getPaymentAmount()));
            }
            job.markRefunded(now);
            recordRefund(job, actorId);
        }
        job.terminateBeforeStart(jobStatusFor(requestStatus), reason, now);
        if (jobStatusFor(requestStatus) == ProcessingJobStatus.DECLINED) {
            businessNotifications.jobDeclined(job);
        }
    }

    /**
     * 요청이 어떻게 끝났는지가 작업이 어떻게 끝나는지를 정한다.
     *
     * <p>둘을 1:1 로 맞춰 둔다. 한쪽만 보고도 무슨 일이 있었는지 알 수 있어야 하는데,
     * 예컨대 요청은 거절인데 작업은 만료로 남으면 나중에 어느 쪽이 사실인지 알 수 없다.
     */
    private ProcessingJobStatus jobStatusFor(ProcessingRequestStatus requestStatus) {
        return switch (requestStatus) {
            case CANCELED -> ProcessingJobStatus.CANCELED;
            case REJECTED -> ProcessingJobStatus.DECLINED;
            case EXPIRED -> ProcessingJobStatus.EXPIRED;
            default -> throw new IllegalArgumentException(
                    "Not a terminal request status: " + requestStatus);
        };
    }

    private void recordRefund(ProcessingJob job, Long actorId) {
        jobInputs.findByProcessingJobId(job.getId()).ifPresent(input -> eventRecorder.record(
                input.getMaterialId(), MaterialEventType.PROCESSING_JOB_REFUNDED, actorId,
                MaterialEventReferenceType.PROCESSING_JOB, job.getId(), null,
                input.getQuantity(), "가공이 시작되기 전에 끝나 가공비를 환불",
                MaterialEventRecorder.key(
                        MaterialEventType.PROCESSING_JOB_REFUNDED,
                        input.getMaterialId(), job.getId())));
    }
}
