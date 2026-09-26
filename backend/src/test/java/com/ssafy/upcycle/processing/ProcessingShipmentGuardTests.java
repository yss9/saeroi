package com.ssafy.upcycle.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.blockchain.service.ChainProcessingStatusReader;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.processing.dto.ProcessingJobShipRequest;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingInput;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.repository.ProcessingInputRepository;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.processing.service.ProcessingLifecycleService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가공 소재 발송은 <b>체인에 서명이 남은 뒤에만</b> DB 에 반영된다.
 *
 * <h2>왜 이 가드가 필요한가</h2>
 *
 * <p>예전에는 {@code chainJobId != null} 만 봤다. 그 값은 "작업이 체인에 등록됐다" 는 뜻이지
 * "발송 서명이 남았다" 는 뜻이 아니다 — 훨씬 앞 단계의 흔적이다.
 *
 * <p><b>DB 가 앞서면 상대편이 막힌다.</b> DB 는 {@code SHIPPED} 인데 체인이 {@code ACCEPTED}
 * 면, 가공업체가 수령을 누를 때 컨트랙트의 {@code confirmReceipt} 가
 * {@code require(status == SHIPPED)} 에서 revert 한다. 그 함수가 체인에서 소재 보관을 브랜드
 * 잠금에서 업체 잠금으로 옮기는 자리라 이후 단계가 줄줄이 막히고, 그때는 이미 가공비를 내고
 * 물건도 보낸 뒤다. 막히는 사람이 담당자가 아니라 업체여서 담당자는 이유도 알 수 없다.
 *
 * <p><b>체인이 앞선 경우는 통과시켜야 한다.</b> 화면은 서명을 먼저 하고 서버를 뒤에 부르므로,
 * 그 사이에 실패하면 체인만 {@code SHIPPED} 로 남는다. 막아 버리면 DB 가 영원히 따라잡지
 * 못한다 — 그 회복 경로를 여기서 함께 고정한다.
 */
@SpringBootTest
@Transactional
class ProcessingShipmentGuardTests {

    @Autowired private ProcessingJobWorkflowService workflow;
    @Autowired private ProcessingJobRepository jobs;
    @Autowired private ProcessingInputRepository inputs;
    @Autowired private ProcessingLifecycleService lifecycle;
    @Autowired private UserRepository users;

    /** 이 슬라이스에는 RPC 가 없다. 체인이 무엇을 답하는지가 이 테스트의 입력이다. */
    @MockitoBean private ChainProcessingStatusReader chainJobs;

    private Long brandId;
    private Long jobId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "proc-guard-brand@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
                .getId();
        Long processorId = users.save(User.register(
                        "proc-guard-proc@test.com", "{noop}x", "proc", "010-1111-1111", UserRole.PROCESSOR))
                .getId();

        // 발송 직전 상태(ACCEPTED)까지 정상 경로로 밀어 둔다. 상태를 직접 써넣으면 엔티티의
        // 전이 규칙을 건너뛰어, 실제로 도달할 수 없는 상태에서 가드를 검증하게 된다.
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 9, 0);
        ProcessingJob job = ProcessingJob.create(
                "JOB-GUARD-1", 1L, brandId, processorId,
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                10_000L, PaymentMethod.KAKAO_PAY, now);
        job.markPaid("pay-ref-1", 10_000L, now.plusMinutes(1), now.plusDays(1));
        job.accept(now.plusMinutes(2));
        job.linkChainJob(77L);
        jobId = jobs.save(job).getId();

        // 발송은 소재 사건을 남기므로 투입 행이 있어야 한다 (recordInput → requireInput).
        // 없으면 가드와 무관하게 PROCESSING_INVALID_STATE 로 끝나 검증이 흐려진다.
        inputs.save(new ProcessingInput(jobId, 33L, 5_000L, 1L));
    }

    @Test
    void 체인에_발송이_없으면_거절한다() {
        when(chainJobs.canRead()).thenReturn(true);
        when(chainJobs.alreadyShipped(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> workflow.ship(brandId, jobId, ship()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("블록체인에 먼저 서명");

        assertThat(jobs.findById(jobId).orElseThrow().getStatus())
                .isEqualTo(ProcessingJobStatus.ACCEPTED);
    }

    /** 체인이 앞선 경우다. 막으면 DB 가 영원히 따라잡지 못한다. */
    @Test
    void 체인에_발송이_남았으면_DB_가_따라잡는다() {
        when(chainJobs.canRead()).thenReturn(true);
        when(chainJobs.alreadyShipped(anyLong())).thenReturn(true);

        workflow.ship(brandId, jobId, ship());

        assertThat(jobs.findById(jobId).orElseThrow().getStatus())
                .isEqualTo(ProcessingJobStatus.SHIPPED);
    }

    /**
     * 체인을 쓰지 않는 환경은 막지 않는다.
     *
     * <p>"읽을 수 없다" 와 "읽었는데 아직 아니다" 를 같게 취급하면 로컬·테스트에서 발송이
     * 통째로 불가능해진다. 어긋날 대상이 없으므로 통과시킨다.
     */
    @Test
    void 체인을_쓰지_않는_환경은_막지_않는다() {
        when(chainJobs.canRead()).thenReturn(false);

        workflow.ship(brandId, jobId, ship());

        assertThat(jobs.findById(jobId).orElseThrow().getStatus())
                .isEqualTo(ProcessingJobStatus.SHIPPED);
    }

    /**
     * <b>옛 경로도 같은 가드를 지난다.</b> 화면은 {@code PATCH /ship} 만 쓰지만
     * {@code POST /shipment} 도 살아 있는 인증 엔드포인트다. 한쪽에만 걸면 다른 문으로
     * 그대로 지나간다 — 오히려 이쪽이 더 헐거워서 {@code chainJobId} 조차 보지 않았다.
     */
    @Test
    void 옛_발송_경로도_체인_서명을_요구한다() {
        when(chainJobs.canRead()).thenReturn(true);
        when(chainJobs.alreadyShipped(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> lifecycle.ship(brandId, jobId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("블록체인에 먼저 서명");

        assertThat(jobs.findById(jobId).orElseThrow().getStatus())
                .isEqualTo(ProcessingJobStatus.ACCEPTED);
    }

    private ProcessingJobShipRequest ship() {
        return new ProcessingJobShipRequest("한진", "1234-5678");
    }
}
