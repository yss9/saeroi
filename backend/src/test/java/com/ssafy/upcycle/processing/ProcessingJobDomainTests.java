package com.ssafy.upcycle.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import jakarta.persistence.Column;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProcessingJobDomainTests {

    @Test
    void evidenceHashUsesTheCharTypeDeclaredBySchemaSql() throws Exception {
        Column column = ProcessingJob.class
                .getDeclaredField("evidenceHash")
                .getAnnotation(Column.class);

        assertThat(column.columnDefinition()).isEqualTo("CHAR(66)");
    }

    @Test
    void jobPaymentExpiresExactlyFifteenMinutesAfterCreation() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 12, 0);

        ProcessingJob job = ProcessingJob.create(
                "PJ0001", 1L, 10L, 20L,
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                120_000L, PaymentMethod.KAKAO_PAY, now);

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.CREATED);
        assertThat(job.getCreatedAt()).isEqualTo(now);
        assertThat(job.getPaymentExpiresAt()).isEqualTo(now.plusMinutes(15));
        assertThat(job.isPaymentDue(now.plusMinutes(14).plusSeconds(59))).isFalse();
        assertThat(job.isPaymentDue(now.plusMinutes(15))).isTrue();
    }

    @Test
    void expiredJobCannotBePaid() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 12, 0);
        ProcessingJob job = ProcessingJob.create(
                "PJ0001", 1L, 10L, 20L,
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                120_000L, PaymentMethod.MOCK, now);

        job.expire(now.plusMinutes(15));

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.EXPIRED);
        assertThatThrownBy(() -> job.markPaid("payment-ref", 120_000L, now.plusMinutes(15)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void paidJobMovesThroughShippingReceivingAndProcessing() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 10, 0);
        ProcessingJob job = paidJob(now);

        job.ship("CJ", "1234", now.plusMinutes(1));
        job.receive(now.plusMinutes(2));
        job.start(now.plusMinutes(3));

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.PROCESSING);
        assertThat(job.getCarrier()).isEqualTo("CJ");
        assertThat(job.getTrackingNumber()).isEqualTo("1234");
        assertThat(job.getShippedAt()).isEqualTo(now.plusMinutes(1));
        assertThat(job.getReceivedAt()).isEqualTo(now.plusMinutes(2));
        assertThat(job.getStartedAt()).isEqualTo(now.plusMinutes(3));
    }

    @Test
    void rejectedResultCanBeRevisedAndResubmitted() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 10, 0);
        ProcessingJob job = paidJob(now);
        job.ship(null, null, now.plusMinutes(1));
        job.receive(now.plusMinutes(2));
        job.start(now.plusMinutes(3));

        job.saveResultDraft(10, now.plusMinutes(4));
        job.submitResult("0xsig", now.plusMinutes(5));
        job.reject("수량을 다시 확인해 주세요", "0xreject", now.plusMinutes(6));
        job.saveResultDraft(5, now.plusMinutes(7));
        job.submitResult("0xresubmit", now.plusMinutes(8));

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.RESULT_SUBMITTED);
        assertThat(job.getResultRevision()).isEqualTo(2);
        assertThat(job.getRejectionReason()).isNull();
    }

    @Test
    void confirmedJobCannotBeChanged() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 10, 0);
        ProcessingJob job = paidJob(now);
        job.ship(null, null, now.plusMinutes(1));
        job.receive(now.plusMinutes(2));
        job.start(now.plusMinutes(3));
        job.saveResultDraft(0, now.plusMinutes(4));
        job.submitResult("0xsubmit", now.plusMinutes(5));
        job.approve("0xapprove", now.plusMinutes(6));

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.CONFIRMED);
        assertThatThrownBy(() -> job.reject("다시", "0xsig", now.plusMinutes(7)))
                .isInstanceOf(IllegalStateException.class);
    }

    private ProcessingJob paidJob(LocalDateTime now) {
        ProcessingJob job = ProcessingJob.create(
                "PJ0001", 1L, 10L, 20L,
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                120_000L, PaymentMethod.MOCK, now);
        job.markPaid("payment-ref", 120_000L, now.plusMinutes(1));
        // 결제만으로는 보내지 못한다. 가공사가 받겠다고 답한 뒤에야 발송이 열린다.
        job.accept(now.plusMinutes(1));
        return job;
    }
    @Test
    void resultLifecycleCalculatesLossAndRecordsReviewTimes() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 11, 9, 0);
        ProcessingJob job = ProcessingJob.create(
                "PJ0001", 1L, 10L, 20L,
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                120_000L, PaymentMethod.MOCK, createdAt);
        job.markPaid("payment-ref", 120_000L, createdAt.plusMinutes(1));
        job.accept(createdAt.plusMinutes(2));
        job.ship(createdAt.plusHours(1));
        job.receive(createdAt.plusHours(2));
        job.startProcessing();

        LocalDateTime submittedAt = createdAt.plusDays(1);
        job.submitResult(1_000, 920, null, null, submittedAt);
        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.RESULT_SUBMITTED);
        assertThat(job.getLossQuantity()).isEqualTo(80);
        assertThat(job.getProcessorSubmittedAt()).isEqualTo(submittedAt);

        LocalDateTime confirmedAt = submittedAt.plusHours(1);
        job.confirmResult(confirmedAt);
        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.CONFIRMED);
        assertThat(job.getBrandReviewedAt()).isEqualTo(confirmedAt);
        assertThat(job.getCompletedAt()).isEqualTo(confirmedAt);
    }
}
