package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.project.AgentProjects;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.dto.ProcessingOutputResponse;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputResponse;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 대화 하나가 맡은 제품 프로젝트({@link AgentProject}) — <b>이 대화가 시작한 일</b>만 담긴다.
 *
 * <p>대화가 먼저 꺼내는 안내·자동 카드·진행 막대가 이것을 읽는다. 예전에는 서버가 어떤 거래가
 * 어느 대화에서 생겼는지 몰라, 에코백을 만드는 대화에 홈페이지에서 산 다른 소재의 "수령 확인"
 * 카드가 떴다.
 */
@SpringBootTest
class AgentProjectTests {

    @Autowired private AgentProjects projects;
    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository cards;
    @Autowired private UserRepository users;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private LlmClient llmClient;
    @MockitoBean private TradeQueryService tradeQuery;
    @MockitoBean private ProcessingJobWorkflowService jobWorkflow;
    @MockitoBean private ProductionBatchService batchService;

    private Long brandId;
    private Long conversationId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "agent-project@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
                .getId();
        when(llmClient.complete(anyList(), anyList())).thenReturn(new LlmCompletion("네", List.of()));
        conversationId = conversationService.start(brandId, "에코백 만들고 싶어요").id();
    }

    @AfterEach
    void clean() {
        cards.deleteAll();
        messages.deleteAll();
        conversations.deleteAll();
        users.deleteAll();
    }

    @Test
    void 새_대화에는_시작한_일이_없다() {
        assertThat(projects.of(brandId, conversationId).isEmpty()).isTrue();
    }

    /** 카드가 떠 있거나 거절된 것은 아무것도 만들지 않았다. 승인된 카드만 센다. */
    @Test
    void 승인한_카드로_생긴_것만_이_대화의_일이다() {
        ProjectCards.boughtTrade(cards, conversationId, 27L);
        ProjectCards.openedBatch(cards, conversationId, 5L);
        pendingCard("create_processing_request", "{\"request\":{\"jobId\":9}}", false);
        pendingCard("register_product_model", "{\"productModel\":{\"productModelId\":70}}", true);

        AgentProject project = projects.claimed(conversationId);

        assertThat(project.tradeIds()).containsExactly(27L);
        assertThat(project.batchIds()).containsExactly(5L);
        assertThat(project.jobIds()).isEmpty();
        assertThat(project.productModelIds()).isEmpty();
    }

    /**
     * 이미 있는 일에 손댄 카드는 그 일을 들이지 않는다. 홈페이지에서 산 거래를 대화에서
     * 수령했다고 그 거래가 이 제품의 것이 되지는 않는다.
     */
    @Test
    void 홈페이지에서_시작한_거래를_대화에서_수령해도_프로젝트가_되지_않는다() {
        ProjectCards.completed(cards, conversationId, "confirm_receipt", "{\"tradeId\":88}",
                "{\"tradeId\":88,\"signed\":true}");

        assertThat(projects.claimed(conversationId).isEmpty()).isTrue();
    }

    @Test
    void 다른_대화의_카드는_섞이지_않는다() {
        ProjectCards.boughtTrade(cards, conversationId + 1000, 27L);

        assertThat(projects.claimed(conversationId).isEmpty()).isTrue();
    }

    /**
     * <b>소재는 따라온다.</b> 산 거래의 소재 → 그것을 맡긴 가공의 결과물 → 그 결과물을 넣은
     * 배치 → 그 배치에 연결된 제품. 가공 결과물은 승인 뒤 새 번호로 생기므로 카드만 봐서는 알 수
     * 없다 — 가공 전 번호를 쓰던 사고가 여기서 나왔다.
     */
    @Test
    void 산_소재에서_가공_결과물과_제품까지_따라간다() {
        ProjectCards.boughtTrade(cards, conversationId, 27L);
        ProjectCards.requestedProcessing(cards, conversationId, 9L, 301L);
        ProjectCards.completed(cards, conversationId, "add_production_inputs",
                "{\"batchId\":5,\"inputs\":[{\"materialId\":410}]}", "{}");
        givenTrades(trade(27L, 301L), trade(28L, 999L));
        givenJobs(job(9L, 301L, 410L), job(12L, 555L, 556L));
        givenBatches(batch(5L, 70L, 410L), batch(6L, 71L, 888L));

        AgentProject project = projects.of(brandId, conversationId);

        assertThat(project.materialIds()).containsExactlyInAnyOrder(301L, 410L);
        assertThat(project.batchIds()).containsExactly(5L);
        assertThat(project.productModelIds()).containsExactly(70L);
        // 홈페이지에서 산 거래(28)·맡긴 가공(12)·연 배치(6)의 소재와 제품은 들어오지 않는다.
        assertThat(project.materialIds()).doesNotContain(999L, 555L, 556L, 888L);
        assertThat(project.productModelIds()).doesNotContain(71L);
    }

    /**
     * 가진 소재로 시작해도 된다 — 따로 "가져오는" 단계 없이, 가공에 쓰는 순간 들어온다.
     */
    @Test
    void 가진_소재는_가공에_쓰는_순간_프로젝트에_들어온다() {
        ProjectCards.requestedProcessing(cards, conversationId, 9L, 301L);

        assertThat(projects.claimed(conversationId).materialIds()).containsExactly(301L);
    }

    /** 결과를 못 읽는 카드 하나 때문에 프로젝트 전체를 잃지 않는다. */
    @Test
    void 읽을_수_없는_카드는_건너뛴다() {
        ProjectCards.completed(cards, conversationId, "purchase_material", "{}", "not json");
        ProjectCards.boughtTrade(cards, conversationId, 27L);

        assertThat(projects.claimed(conversationId).tradeIds()).containsExactly(27L);
    }

    /**
     * 승인한 카드가 프로젝트를 다음 단계로 들이면 대화 단계가 따라간다 — <b>앞으로만.</b>
     * 생산 중에 소재를 더 사도 막대가 소재로 돌아가지 않는다.
     */
    @Test
    void 대화_단계는_앞으로만_간다() {
        AgentConversation conversation = AgentConversation.start(brandId, "테스트");

        conversation.advanceTo(JourneyPhase.PRODUCTION);
        assertThat(conversation.getPhase()).isEqualTo(JourneyPhase.PRODUCTION);

        conversation.advanceTo(JourneyPhase.MATERIAL_SOURCING);
        assertThat(conversation.getPhase()).isEqualTo(JourneyPhase.PRODUCTION);
    }

    @Test
    void 카드가_들인_가장_먼_단계를_안다() {
        var arguments = objectMapper.createObjectNode();

        assertThat(projects.claimedBy("purchase_material", arguments, "{\"tradeId\":1}").furthestStage())
                .contains(JourneyPhase.MATERIAL_SOURCING);
        assertThat(projects.claimedBy("open_production_batch", arguments,
                        "{\"batch\":{\"productionBatchId\":5}}").furthestStage())
                .contains(JourneyPhase.PRODUCTION);
        // 이미 있는 일에 손댄 카드는 단계를 옮기지 않는다.
        assertThat(projects.claimedBy("confirm_receipt", arguments, "{\"tradeId\":1}").furthestStage())
                .isEmpty();
    }

    private void pendingCard(String toolName, String resultJson, boolean rejected) {
        LocalDateTime now = LocalDateTime.now();
        AgentPendingAction card = AgentPendingAction.propose(
                conversationId, 1L, PendingActionType.CONFIRM, toolName, "테스트", "{}", 1, now.plusHours(1));
        if (rejected) {
            card.reject(now);
        } else {
            card.replaceResult(resultJson);
        }
        cards.save(card);
    }

    private void givenTrades(TradeResponse... trades) {
        var page = new PageResponse<>(List.of(trades), 0, 100, trades.length, 1, false);
        when(tradeQuery.findMyTrades(any(), any())).thenReturn(page);
    }

    private void givenJobs(ProcessingJobResponse... jobs) {
        List<ProcessingJobResponse> list = List.of(jobs);
        when(jobWorkflow.list(any())).thenReturn(list);
    }

    private void givenBatches(ProductionBatchResponse... batches) {
        List<ProductionBatchResponse> list = List.of(batches);
        when(batchService.findMyBatches(any())).thenReturn(list);
    }

    private TradeResponse trade(Long tradeId, Long materialId) {
        TradeResponse trade = mock(TradeResponse.class);
        when(trade.tradeId()).thenReturn(tradeId);
        when(trade.materialId()).thenReturn(materialId);
        return trade;
    }

    private ProcessingJobResponse job(Long jobId, Long inputMaterialId, Long outputMaterialId) {
        ProcessingOutputResponse output = mock(ProcessingOutputResponse.class);
        when(output.materialId()).thenReturn(outputMaterialId);
        ProcessingJobResponse job = mock(ProcessingJobResponse.class);
        when(job.jobId()).thenReturn(jobId);
        when(job.inputMaterialId()).thenReturn(inputMaterialId);
        List<ProcessingOutputResponse> outputs = List.of(output);
        when(job.outputs()).thenReturn(outputs);
        return job;
    }

    private ProductionBatchResponse batch(Long batchId, Long productModelId, Long inputMaterialId) {
        ProductionInputResponse input = mock(ProductionInputResponse.class);
        when(input.materialId()).thenReturn(inputMaterialId);
        ProductionBatchResponse batch = mock(ProductionBatchResponse.class);
        when(batch.productionBatchId()).thenReturn(batchId);
        when(batch.productModelId()).thenReturn(productModelId);
        List<ProductionInputResponse> inputs = List.of(input);
        when(batch.inputs()).thenReturn(inputs);
        return batch;
    }
}
