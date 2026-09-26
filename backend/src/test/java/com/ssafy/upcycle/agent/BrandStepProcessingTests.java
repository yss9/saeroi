package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.service.step.BrandStep;
import com.ssafy.upcycle.agent.service.step.BrandStepResolver;
import com.ssafy.upcycle.agent.service.step.ProductionChainProgress;
import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
import com.ssafy.upcycle.blockchain.service.ChainProcessingStatusReader;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 가공의 남은 일. 담당자 차례와 업체 차례를 가르고, 무엇을 맡겼는지로 부른다.
 *
 * <p>두 소재를 함께 가공하자 "가공 작업 9 — 온체인 작업 등록이 필요합니다" 같은 안내가 줄줄이 떴다. 담당자는
 * 9번이 무엇인지 몰랐고, 업체가 체인에서 수락하기 전에도 "소재를 인계할 차례" 로 적혀 있어 쓸데없는 단계가
 * 끼어든 줄 알았다.
 */
class BrandStepProcessingTests {

    private static final long JOB = 8L;
    private static final long CHAIN_JOB = 21L;
    private static final int CHAIN_REQUESTED = 1;
    private static final int CHAIN_ACCEPTED = 2;

    private ProcessingJobWorkflowService jobWorkflow;
    private ChainProcessingStatusReader chainJobs;
    private MaterialRepository materials;
    private BrandStepResolver resolver;

    @BeforeEach
    void setUp() {
        jobWorkflow = mock(ProcessingJobWorkflowService.class);
        chainJobs = mock(ChainProcessingStatusReader.class);
        materials = mock(MaterialRepository.class);
        when(materials.findById(anyLong())).thenReturn(Optional.empty());
        resolver = new BrandStepResolver(
                mock(TradeQueryService.class),
                jobWorkflow,
                mock(ProductionBatchService.class),
                mock(ProductModelQueryService.class),
                mock(ChainOrderStatusReader.class),
                chainJobs,
                materials,
                mock(ProductionChainProgress.class));
    }

    @Test
    void 등록_뒤_업체가_확인하기_전은_담당자_차례가_아니다() {
        givenJob(ProcessingJobStatus.ACCEPTED, CHAIN_JOB);
        when(chainJobs.canRead()).thenReturn(true);
        when(chainJobs.status(CHAIN_JOB)).thenReturn(Optional.of(CHAIN_REQUESTED));

        BrandStep step = onlyStep();

        assertThat(step.isActionable()).isFalse();
        assertThat(step.label()).isEqualTo("업체가 작업 내용을 확인하는 중입니다");
        assertThat(step.instruction()).doesNotContain("온체인");
    }

    @Test
    void 업체가_확인하면_소재를_보낼_차례다() {
        givenJob(ProcessingJobStatus.ACCEPTED, CHAIN_JOB);
        when(chainJobs.canRead()).thenReturn(true);
        when(chainJobs.status(CHAIN_JOB)).thenReturn(Optional.of(CHAIN_ACCEPTED));

        BrandStep step = onlyStep();

        assertThat(step.isActionable()).isTrue();
        assertThat(step.toolName()).isEqualTo("request_custody_transfer");
    }

    /** 체인을 읽지 못하는 환경은 판정하지 않는다 — 막으면 인계 안내가 통째로 사라진다. */
    @Test
    void 체인을_읽지_못하면_예전처럼_인계_차례로_둔다() {
        givenJob(ProcessingJobStatus.ACCEPTED, CHAIN_JOB);
        when(chainJobs.canRead()).thenReturn(false);

        assertThat(onlyStep().toolName()).isEqualTo("request_custody_transfer");
    }

    @Test
    void 업체가_수락하면_소재를_보내기_위한_첫_번째_서명이다() {
        givenJob(ProcessingJobStatus.ACCEPTED, null);

        BrandStep step = onlyStep();

        assertThat(step.label()).isEqualTo("소재를 보내기 위한 첫 번째 서명(작업 등록)이 필요합니다");
        // 모델은 카드를 띄울 때 두 서명이 무엇인지 먼저 설명한다.
        assertThat(step.instruction()).contains("먼저 설명한다").contains("서명이 두 번");
    }

    @Test
    void 업체가_작업을_확인하면_두_번째_서명이다() {
        givenJob(ProcessingJobStatus.ACCEPTED, CHAIN_JOB);
        when(chainJobs.canRead()).thenReturn(true);
        when(chainJobs.status(CHAIN_JOB)).thenReturn(Optional.of(CHAIN_ACCEPTED));

        assertThat(onlyStep().label()).isEqualTo("소재 보내기 서명(두 번째 서명)을 할 차례입니다");
    }

    @Test
    void 번호_대신_맡긴_소재와_양으로_부른다() {
        givenJob(ProcessingJobStatus.PAID, CHAIN_JOB);
        Material wool = mock(Material.class);
        when(wool.getName()).thenReturn("울 원단");
        when(materials.findById(38L)).thenReturn(Optional.of(wool));

        assertThat(onlyStep().subject()).isEqualTo("울 원단 5kg 가공");
    }

    @Test
    void 소재를_찾지_못하면_작업_번호로_부른다() {
        givenJob(ProcessingJobStatus.PAID, CHAIN_JOB);

        assertThat(onlyStep().subject()).isEqualTo("가공 작업 8");
    }

    private BrandStep onlyStep() {
        List<BrandStep> steps = resolver.resolve(2L, new AgentProject.Builder().job(JOB).build());
        assertThat(steps).hasSize(1);
        return steps.getFirst();
    }

    private void givenJob(ProcessingJobStatus status, Long chainJobId) {
        ProcessingJobResponse job = mock(ProcessingJobResponse.class);
        when(job.jobId()).thenReturn(JOB);
        when(job.status()).thenReturn(status);
        when(job.chainJobId()).thenReturn(chainJobId);
        when(job.inputMaterialId()).thenReturn(38L);
        when(job.inputQuantity()).thenReturn(5_000L);
        when(jobWorkflow.list(any())).thenReturn(List.of(job));
    }
}
