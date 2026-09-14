package com.ssafy.upcycle.processing.service;

import com.ssafy.upcycle.notification.entity.NotificationReferenceType;
import com.ssafy.upcycle.notification.entity.NotificationType;
import com.ssafy.upcycle.notification.service.NotificationCommand;
import com.ssafy.upcycle.notification.service.NotificationService;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingRequest;
import com.ssafy.upcycle.processing.entity.ProcessingRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProcessingNotificationService {

    private final NotificationService notifications;

    public void requestCreated(ProcessingRequest request, long quantity) {
        send(
                request.getProcessorId(),
                NotificationType.PROCESSING_REQUEST_RECEIVED,
                "새 가공 요청이 도착했어요",
                quantity + "g 가공 요청을 확인해 주세요.",
                "/processor/jobs",
                NotificationReferenceType.PROCESSING_REQUEST,
                request.getId(),
                null);
    }

    public void requestTerminated(
            ProcessingRequest request, ProcessingRequestStatus status, Long actorId) {
        NotificationType type = switch (status) {
            case CANCELED -> NotificationType.PROCESSING_REQUEST_CANCELED;
            case REJECTED -> NotificationType.PROCESSING_REQUEST_REJECTED;
            case EXPIRED -> NotificationType.PROCESSING_REQUEST_EXPIRED;
            default -> throw new IllegalArgumentException("Unsupported processing request status");
        };
        String title = switch (status) {
            case CANCELED -> "가공 요청이 취소됐어요";
            case REJECTED -> "가공 요청이 거절됐어요";
            case EXPIRED -> "가공 요청이 만료됐어요";
            default -> throw new IllegalArgumentException("Unsupported processing request status");
        };
        if (actorId == null || !actorId.equals(request.getBrandId())) {
            send(request.getBrandId(), type, title, "가공 요청 상태를 확인해 주세요.",
                    "/brand/processing/requests/" + request.getId(),
                    NotificationReferenceType.PROCESSING_REQUEST, request.getId(), null);
        }
        if (actorId == null || !actorId.equals(request.getProcessorId())) {
            send(request.getProcessorId(), type, title, "가공 요청 상태를 확인해 주세요.",
                    "/processor/jobs", NotificationReferenceType.PROCESSING_REQUEST,
                    request.getId(), null);
        }
    }

    public void quoteSubmitted(ProcessingRequest request) {
        send(
                request.getBrandId(),
                NotificationType.PROCESSING_QUOTE_RECEIVED,
                "새 견적이 도착했어요",
                "가공업체가 제출한 견적을 확인해 주세요.",
                "/brand/processing/requests/" + request.getId(),
                NotificationReferenceType.PROCESSING_REQUEST,
                request.getId(),
                null);
    }

    public void quoteSelected(ProcessingJob job) {
        jobNotification(
                job,
                job.getProcessorId(),
                NotificationType.PROCESSING_QUOTE_SELECTED,
                "견적이 선택됐어요",
                "가공 작업과 결제 상태를 확인해 주세요.",
                null);
    }

    public void paymentCompleted(ProcessingJob job) {
        jobNotification(
                job,
                job.getProcessorId(),
                NotificationType.PROCESSING_PAYMENT_COMPLETED,
                "가공비 결제가 완료됐어요",
                "소재 발송 상태를 확인해 주세요.",
                null);
    }

    public void jobExpired(ProcessingJob job) {
        jobNotification(job, job.getBrandId(), NotificationType.PROCESSING_JOB_EXPIRED,
                "가공비 결제 기한이 만료됐어요", "가공 작업 상태를 확인해 주세요.", null);
        jobNotification(job, job.getProcessorId(), NotificationType.PROCESSING_JOB_EXPIRED,
                "가공비 결제 기한이 만료됐어요", "가공 작업 상태를 확인해 주세요.", null);
    }

    public void jobEvent(ProcessingJob job, NotificationType type) {
        switch (type) {
            case PROCESSING_MATERIAL_SHIPPED -> jobNotification(
                    job, job.getProcessorId(), type, "가공 소재가 발송됐어요",
                    "배송 정보를 확인해 주세요.", null);
            case PROCESSING_MATERIAL_RECEIVED -> jobNotification(
                    job, job.getBrandId(), type, "가공업체가 소재를 수령했어요",
                    "가공 작업 상태를 확인해 주세요.", null);
            case PROCESSING_STARTED -> jobNotification(
                    job, job.getBrandId(), type, "가공 작업이 시작됐어요",
                    "가공 작업 상태를 확인해 주세요.", null);
            case PROCESSING_RESULT_SUBMITTED -> jobNotification(
                    job, job.getBrandId(), type, "가공 결과가 제출됐어요",
                    "결과와 증빙을 확인해 주세요.", job.getResultRevision());
            case PROCESSING_RESULT_REJECTED -> jobNotification(
                    job, job.getProcessorId(), type, "가공 결과가 반려됐어요",
                    "반려 사유를 확인하고 결과를 수정해 주세요.", job.getResultRevision());
            case PROCESSING_RESULT_APPROVED -> jobNotification(
                    job, job.getProcessorId(), type, "가공 결과가 승인됐어요",
                    "완료된 가공 작업을 확인해 주세요.", job.getResultRevision());
            default -> throw new IllegalArgumentException("Unsupported processing notification type");
        }
    }

    private void jobNotification(
            ProcessingJob job,
            Long recipientId,
            NotificationType type,
            String title,
            String message,
            Integer revision) {
        send(recipientId, type, title, message, "/processing/jobs/" + job.getId(),
                NotificationReferenceType.PROCESSING_JOB, job.getId(), revision);
    }

    private void send(
            Long recipientId,
            NotificationType type,
            String title,
            String message,
            String targetPath,
            NotificationReferenceType referenceType,
            Long referenceId,
            Integer revision) {
        String key = type + ":" + referenceId
                + (revision == null ? "" : ":" + revision)
                + ":" + recipientId;
        notifications.notify(new NotificationCommand(
                recipientId, type, title, message, targetPath,
                referenceType, referenceId, key));
    }
}
