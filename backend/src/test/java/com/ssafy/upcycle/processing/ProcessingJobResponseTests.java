package com.ssafy.upcycle.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code chainReleaseRequired} 가 <b>정말로 잠금이 남은 작업에서만</b> 참인지 본다.
 *
 * <p>{@code CANCELED} 는 두 길로 도달한다. 시작 전 종료({@code terminateBeforeStart})와
 * 반려 결과의 반환 종료({@code cancelAfterRejectedReturn}) 다. 뒤쪽은 온체인에서 이미
 * {@code REJECTED_INPUT_RETURNED} 로 끝났고 소재도 브랜드 잔량으로 돌아가 있다 — 여기에
 * 해제 안내를 띄우면 브랜드는 {@code cancelJob} 이 revert 하는 버튼을 누르게 된다.
 * 둘을 가르는 표시가 {@code declinedAt} 이고, 그 값은 {@code terminateBeforeStart} 만 적는다.
 */
class ProcessingJobResponseTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 10, 0);
    private static final String TX = "0x" + "a".repeat(64);
    private static final String HASH = "0x" + "b".repeat(64);

    @Test
    @DisplayName("반환 종료로 끝난 작업은 온체인 잠금이 이미 소진돼 해제할 것이 없다")
    void rejectedReturnCompletedJobNeedsNoChainRelease() {
        ProcessingJob job = chainLinkedPaidJob();
        job.accept(NOW.plusMinutes(2));
        job.ship("CJ", "1234", NOW.plusMinutes(3));
        job.receive(NOW.plusMinutes(4));
        job.start(NOW.plusMinutes(5));
        job.saveResultDraft(10, NOW.plusMinutes(6));
        job.submitResult("0xsubmit", NOW.plusMinutes(7));
        job.reject("수량을 다시 확인해 주세요", "0xreject", NOW.plusMinutes(8));
        job.cancelAfterRejectedReturn("CJ", "5678", HASH, TX, 100L, NOW.plusMinutes(9));

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.CANCELED);
        assertThat(job.getChainJobId()).isNotNull();
        assertThat(ProcessingJobResponse.of(job).chainReleaseRequired())
                .as("체인은 REJECTED_INPUT_RETURNED 로 끝났다 — cancelJob 은 revert 한다")
                .isFalse();
    }

    @Test
    @DisplayName("가공사가 거절한 체인 연결 작업은 여전히 해제가 필요하다")
    void declinedChainLinkedJobStillNeedsChainRelease() {
        ProcessingJob job = chainLinkedPaidJob();
        job.terminateBeforeStart(ProcessingJobStatus.DECLINED, "설비 점검 중", NOW.plusMinutes(2));

        assertThat(ProcessingJobResponse.of(job).chainReleaseRequired()).isTrue();
    }

    @Test
    @DisplayName("기한이 지나 만료된 체인 연결 작업도 해제가 필요하다")
    void expiredChainLinkedJobStillNeedsChainRelease() {
        ProcessingJob job = chainLinkedPaidJob();
        job.terminateBeforeStart(ProcessingJobStatus.EXPIRED, null, NOW.plusDays(3));

        assertThat(ProcessingJobResponse.of(job).chainReleaseRequired()).isTrue();
    }

    @Test
    @DisplayName("브랜드가 시작 전에 취소한 체인 연결 작업도 해제가 필요하다")
    void preStartCanceledChainLinkedJobStillNeedsChainRelease() {
        ProcessingJob job = chainLinkedPaidJob();
        job.terminateBeforeStart(ProcessingJobStatus.CANCELED, "직접 취소", NOW.plusMinutes(2));

        assertThat(ProcessingJobResponse.of(job).chainReleaseRequired()).isTrue();
    }

    private ProcessingJob chainLinkedPaidJob() {
        ProcessingJob job = ProcessingJob.create(
                "PJ0001", 1L, 10L, 20L,
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                120_000L, PaymentMethod.MOCK, NOW);
        job.markPaid("payment-ref", 120_000L, NOW.plusMinutes(1));
        job.linkChainJob(77L);
        return job;
    }
}
