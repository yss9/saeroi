package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.project.AgentProjects;
import com.ssafy.upcycle.agent.service.AgentJourneyProgress;
import com.ssafy.upcycle.agent.service.step.AutoProposableStep;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.processing.MyProcessingTool;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 이 대화의 일을 가리고, 번호 대신 이름으로 부른다.
 *
 * <p>가공을 마친 뒤 모델이 지난 제품의 작업 7·6·5 까지 "완료" 로 늘어놓았고, 카드 제목은 "울 원단 5kg 가공 가공 결과
 * 승인" 처럼 말이 겹쳤다. 진행 막대는 가공이 끝나 0kg 이 된 원래 소재를 따라가 체크가 끝까지 켜지지 않았다.
 */
class ProjectScopedNamesTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void 가공_목록은_이_대화의_작업을_앞에_두고_이름을_붙인다() throws Exception {
        ProcessingJobWorkflowService workflow = mock(ProcessingJobWorkflowService.class);
        List<ProcessingJobResponse> jobs = List.of(job(7L, 20L), job(8L, 38L));
        when(workflow.list(any())).thenReturn(jobs);
        MaterialRepository materials = mock(MaterialRepository.class);
        when(materials.findById(anyLong())).thenReturn(Optional.empty());
        Material wool = mock(Material.class);
        when(wool.getName()).thenReturn("울 원단");
        when(materials.findById(38L)).thenReturn(Optional.of(wool));
        AgentProjects projects = mock(AgentProjects.class);
        when(projects.of(2L, 20L)).thenReturn(new AgentProject.Builder().job(8L).build());
        MyProcessingTool tool = new MyProcessingTool(JSON, workflow, materials, provider(projects));

        JsonNode rows = JSON.readTree(tool.execute(context(20L), JSON.createObjectNode()).payloadJson()).path("jobs");

        assertThat(rows.get(0).path("jobId").asLong()).isEqualTo(8L);
        assertThat(rows.get(0).path("thisProject").asBoolean()).isTrue();
        assertThat(rows.get(0).path("materialName").asText()).isEqualTo("울 원단");
        assertThat(rows.get(1).path("thisProject").asBoolean()).isFalse();
    }

    @Test
    void 프로젝트를_모르면_모두_이_대화의_것이_아닌_것으로_본다() throws Exception {
        ProcessingJobWorkflowService workflow = mock(ProcessingJobWorkflowService.class);
        List<ProcessingJobResponse> jobs = List.of(job(7L, 20L));
        when(workflow.list(any())).thenReturn(jobs);
        AgentProjects projects = mock(AgentProjects.class);
        when(projects.of(any(), any())).thenThrow(new IllegalStateException("lookup failed"));
        MyProcessingTool tool = new MyProcessingTool(
                JSON, workflow, mock(MaterialRepository.class), provider(projects));

        JsonNode rows = JSON.readTree(tool.execute(context(20L), JSON.createObjectNode()).payloadJson()).path("jobs");

        // 조회가 실패해도 목록은 돌려준다.
        assertThat(rows.get(0).path("thisProject").asBoolean()).isFalse();
    }

    @Test
    void 가공_카드_제목에_말이_겹치지_않는다() {
        assertThat(AutoProposableStep.APPROVE_PROCESSING_RESULT.summaryFor("울 원단 5kg 가공"))
                .isEqualTo("울 원단 5kg 가공 — 결과 승인");
        assertThat(AutoProposableStep.REQUEST_CUSTODY_TRANSFER.summaryFor("울 원단 5kg 가공"))
                .isEqualTo("울 원단 5kg 가공 — 소재 보내기 서명");
        assertThat(AutoProposableStep.REGISTER_PROCESSING_CHAIN_JOB.summaryFor("울 원단 5kg 가공"))
                .isEqualTo("울 원단 5kg 가공 — 작업 등록 서명");
    }

    /** 소재를 보내려는데 "작업 등록 서명" 카드가 먼저 뜨자 담당자는 무슨 과정인지 물었다. 카드 안내에 이유를 붙인다. */
    @Test
    void 소재_보내기_서명_두_번은_안내에_이유가_붙는다() {
        assertThat(AutoProposableStep.REGISTER_PROCESSING_CHAIN_JOB.why())
                .hasValueSatisfying(why -> assertThat(why)
                        .contains("서명이 두 번")
                        .contains("이것만으로 소재가 나가지는 않습니다"));
        assertThat(AutoProposableStep.REQUEST_CUSTODY_TRANSFER.why())
                .hasValueSatisfying(why -> assertThat(why).contains("두 번째 서명"));
        // 담당자가 아는 절차에는 덧붙이지 않는다.
        assertThat(AutoProposableStep.CONFIRM_RECEIPT.why()).isEmpty();
    }

    /**
     * 결제 카드 승인은 결제창을 연 것일 뿐이다. 거기서 모델이 돌아 결제 전에 "✓ 결제 완료" 를 썼고, 결제창은 그만큼
     * 늦게 떴다. 결제 카드는 복귀 화면이 결과를 알린 뒤에 말한다.
     */
    @Test
    void 결제_카드는_승인_직후에_모델을_부르지_않는다() {
        assertThat(com.ssafy.upcycle.agent.service.AgentActionStore.resumesAfter(
                com.ssafy.upcycle.agent.entity.PendingActionType.PAYMENT)).isFalse();
        assertThat(com.ssafy.upcycle.agent.service.AgentActionStore.resumesAfter(
                com.ssafy.upcycle.agent.entity.PendingActionType.WALLET_SIGN)).isTrue();
        assertThat(com.ssafy.upcycle.agent.service.AgentActionStore.resumesAfter(
                com.ssafy.upcycle.agent.entity.PendingActionType.CONFIRM)).isTrue();
    }

    @Test
    void 가공이_끝나면_막대는_다_쓴_원래_소재가_아니라_결과물을_따라간다() {
        MyMaterialResponse processed = material(44L, 5_000,
                List.of(ProcessType.CLEANING, ProcessType.DISASSEMBLY, ProcessType.SORTING));
        MyMaterialResponse usedUp = material(38L, 0, List.of());

        assertThat(AgentJourneyProgress.pickSubject(List.of(processed, usedUp), null)).isSameAs(processed);
    }

    @Test
    void 가공_중이면_잠겨_있어도_그_작업의_소재를_따라간다() {
        MyMaterialResponse processed = material(44L, 5_000,
                List.of(ProcessType.CLEANING, ProcessType.DISASSEMBLY, ProcessType.SORTING));
        MyMaterialResponse locked = material(36L, 0, List.of());

        assertThat(AgentJourneyProgress.pickSubject(List.of(processed, locked), 36L)).isSameAs(locked);
    }

    @Test
    void 공정이_남은_소재가_있으면_그것을_먼저_따라간다() {
        MyMaterialResponse processed = material(44L, 5_000,
                List.of(ProcessType.CLEANING, ProcessType.DISASSEMBLY, ProcessType.SORTING));
        MyMaterialResponse raw = material(37L, 3_000, List.of());

        assertThat(AgentJourneyProgress.pickSubject(List.of(processed, raw), null)).isSameAs(raw);
    }

    private static MyMaterialResponse material(long id, long available, List<ProcessType> completed) {
        MyMaterialResponse material = mock(MyMaterialResponse.class);
        when(material.materialId()).thenReturn(id);
        when(material.available()).thenReturn(available);
        when(material.rootOrigin()).thenReturn(MaterialRootOrigin.WASTE);
        when(material.completedProcessTypes()).thenReturn(completed);
        return material;
    }

    private static ProcessingJobResponse job(Long jobId, Long materialId) {
        ProcessingJobResponse job = mock(ProcessingJobResponse.class);
        when(job.jobId()).thenReturn(jobId);
        when(job.status()).thenReturn(ProcessingJobStatus.CONFIRMED);
        when(job.inputMaterialId()).thenReturn(materialId);
        when(job.inputQuantity()).thenReturn(5_000L);
        return job;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AgentProjects> provider(AgentProjects projects) {
        ObjectProvider<AgentProjects> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(projects);
        return provider;
    }

    private static AgentToolContext context(Long conversationId) {
        AgentConversation conversation = mock(AgentConversation.class);
        when(conversation.getId()).thenReturn(conversationId);
        return new AgentToolContext(2L, conversation);
    }
}
