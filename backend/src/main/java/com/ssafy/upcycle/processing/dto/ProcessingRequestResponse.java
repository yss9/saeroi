package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 만들어진 가공 요청.
 *
 * <p><b>작업(job)과 결제 정보가 함께 내려온다.</b> 견적이 사라지면서 요청과 동시에 작업이
 * 만들어지고 곧바로 결제로 이어지는데, 여기에 {@code jobId}·{@code fee}·
 * {@code paymentExpiresAt} 이 없으면 화면은 요청을 만든 직후 작업 목록을 다시 조회해
 * 방금 만든 것을 찾아야 한다.
 *
 * <p>{@code fee} 는 <b>서버가 확정한 금액</b>이다. 가공사가 등록해 둔 kg 당 단가를 요청 수량에
 * 곱해 공정마다 올림한 뒤 더한 값이고, 검색 결과의 {@code estimatedPrice} 와 같은 식이라
 * 두 값이 어긋나지 않는다.
 *
 * <p>{@code expiresAt} 은 요청이 저절로 끝나는 시각이다. 결제 전에는 결제 기한
 * ({@code paymentExpiresAt} 과 같은 값), 결제 후에는 가공사의 수락 기한이다.
 */
public record ProcessingRequestResponse(
        Long requestId, String requestCode, ProcessingRequestStatus status,
        Long materialId, Long processorId, long quantity, List<ProcessType> processTypes,
        Long jobId, long fee, PaymentMethod paymentMethod, LocalDateTime paymentExpiresAt,
        LocalDateTime createdAt, LocalDateTime expiresAt) {

    public static ProcessingRequestResponse of(
            ProcessingRequest request, ProcessingJob job, CreateProcessingRequest input) {
        return new ProcessingRequestResponse(request.getId(), request.getRequestCode(), request.getStatus(),
                input.materialId(), request.getProcessorId(), input.quantity(),
                List.copyOf(input.processTypes()),
                job.getId(), job.getFee(), job.getPaymentMethod(), job.getPaymentExpiresAt(),
                request.getCreatedAt(), request.getExpiresAt());
    }
}
