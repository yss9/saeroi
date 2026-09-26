package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.dto.JourneyProgressResponse.Spend;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.service.AgentSpending;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.entity.ProcessingRequestType;
import com.ssafy.upcycle.processing.repository.ProcessingRequestTypeRepository;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 오른쪽 패널의 "결제한 금액" — 소재 구매와 <b>가공비</b>.
 *
 * <p>화면이 거래만 더해 150,000원을 낸 가공비가 빠져 있었다. 가공은 작업 하나가 한 줄이다 — 한 업체에 공정을 여럿
 * 맡기면 공정이 한 줄에 함께 적히고, 업체가 다르면 따로 적힌다.
 */
class AgentSpendingTests {

    private TradeQueryService tradeQuery;
    private ProcessingJobWorkflowService jobWorkflow;
    private ProcessingRequestTypeRepository requestTypes;
    private MaterialRepository materials;
    private UserRepository users;
    private AgentSpending spending;

    @BeforeEach
    void setUp() {
        tradeQuery = mock(TradeQueryService.class);
        jobWorkflow = mock(ProcessingJobWorkflowService.class);
        requestTypes = mock(ProcessingRequestTypeRepository.class);
        materials = mock(MaterialRepository.class);
        users = mock(UserRepository.class);
        when(materials.findById(anyLong())).thenReturn(Optional.empty());
        spending = new AgentSpending(tradeQuery, jobWorkflow, requestTypes, materials, users, new com.ssafy.upcycle.agent.service.CompanyNames(org.mockito.Mockito.mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class)));
    }

    @Test
    void 소재_구매와_가공비를_함께_모은다() {
        TradeResponse denim = trade(33L, "폐데님 원단", 10_000, 20_000, TradeStatus.RECEIVED);
        TradeResponse canceled = trade(34L, "울 원단", 5_000, 50_000, TradeStatus.CANCELED);
        PageResponse<TradeResponse> page = page(List.of(denim, canceled));
        when(tradeQuery.findMyTrades(any(), any())).thenReturn(page);

        ProcessingJobResponse cleaning = job(10L, 36L, 10_000, 150_000, 24L, 7L, null);
        ProcessingJobResponse refunded = job(11L, 36L, 10_000, 30_000, 18L, 8L, LocalDateTime.now());
        List<ProcessingJobResponse> jobs = List.of(cleaning, refunded);
        when(jobWorkflow.list(any())).thenReturn(jobs);
        givenMaterial(36L, "폐데님 원단");
        givenProcesses(7L, ProcessType.CLEANING, ProcessType.DISASSEMBLY, ProcessType.SORTING);
        givenProcessor(24L, "pro");

        AgentProject project = new AgentProject.Builder().trade(33L).trade(34L).job(10L).job(11L).build();
        List<Spend> lines = spending.of(2L, project);

        assertThat(lines).containsExactly(
                new Spend("MATERIAL", "폐데님 원단 10kg", null, 20_000),
                // 공정 셋을 한 업체에 맡긴 작업은 한 줄이다. 돌려받은 가공비는 쓴 돈이 아니다.
                new Spend("PROCESSING", "폐데님 원단 10kg 가공", "세척·해체·선별 · pro", 150_000));
    }

    @Test
    void 업체가_다르면_작업마다_따로_적는다() {
        ProcessingJobResponse first = job(10L, 36L, 10_000, 150_000, 24L, 7L, null);
        ProcessingJobResponse second = job(12L, 45L, 9_500, 20_000, 18L, 9L, null);
        List<ProcessingJobResponse> jobs = List.of(second, first);
        when(jobWorkflow.list(any())).thenReturn(jobs);
        givenMaterial(36L, "폐데님 원단");
        givenMaterial(45L, "폐데님 가공 소재");
        givenProcesses(7L, ProcessType.CLEANING);
        givenProcesses(9L, ProcessType.CUTTING);
        givenProcessor(24L, "pro");
        givenProcessor(18L, "홍길동");

        List<Spend> lines = spending.of(2L, new AgentProject.Builder().job(10L).job(12L).build());

        assertThat(lines).extracting(Spend::detail).containsExactly("세척 · pro", "재단 · 홍길동");
        assertThat(lines).extracting(Spend::amount).containsExactly(150_000L, 20_000L);
    }

    @Test
    void 이_대화의_일이_아니면_세지_않는다() {
        ProcessingJobResponse other = job(9L, 36L, 5_000, 75_000, 24L, 6L, null);
        List<ProcessingJobResponse> jobs = List.of(other);
        when(jobWorkflow.list(any())).thenReturn(jobs);

        assertThat(spending.of(2L, new AgentProject.Builder().job(10L).build())).isEmpty();
    }

    private void givenMaterial(Long id, String name) {
        Material material = mock(Material.class);
        when(material.getName()).thenReturn(name);
        when(materials.findById(id)).thenReturn(Optional.of(material));
    }

    private void givenProcesses(Long requestId, ProcessType... types) {
        List<ProcessingRequestType> rows = new java.util.ArrayList<>();
        for (ProcessType type : types) {
            ProcessingRequestType row = mock(ProcessingRequestType.class);
            when(row.getProcessType()).thenReturn(type);
            rows.add(row);
        }
        when(requestTypes.findAllByProcessingRequestIdOrderBySequenceNoAsc(requestId)).thenReturn(rows);
    }

    private void givenProcessor(Long id, String name) {
        User processor = mock(User.class);
        when(processor.getName()).thenReturn(name);
        when(users.findById(id)).thenReturn(Optional.of(processor));
    }

    private static TradeResponse trade(Long id, String name, long quantity, long paid, TradeStatus status) {
        TradeResponse trade = mock(TradeResponse.class);
        when(trade.tradeId()).thenReturn(id);
        when(trade.materialName()).thenReturn(name);
        when(trade.quantity()).thenReturn(quantity);
        when(trade.paymentAmount()).thenReturn(paid);
        when(trade.status()).thenReturn(status);
        return trade;
    }

    private static ProcessingJobResponse job(
            Long id, Long materialId, long quantity, long paid, Long processorId, Long requestId,
            LocalDateTime refundedAt) {
        ProcessingJobResponse job = mock(ProcessingJobResponse.class);
        when(job.jobId()).thenReturn(id);
        when(job.status()).thenReturn(ProcessingJobStatus.CONFIRMED);
        when(job.inputMaterialId()).thenReturn(materialId);
        when(job.inputQuantity()).thenReturn(quantity);
        when(job.paymentAmount()).thenReturn(paid);
        when(job.processorId()).thenReturn(processorId);
        when(job.requestId()).thenReturn(requestId);
        when(job.refundedAt()).thenReturn(refundedAt);
        return job;
    }

    @SuppressWarnings("unchecked")
    private static PageResponse<TradeResponse> page(List<TradeResponse> content) {
        PageResponse<TradeResponse> page = mock(PageResponse.class);
        when(page.content()).thenReturn(content);
        return page;
    }
}
