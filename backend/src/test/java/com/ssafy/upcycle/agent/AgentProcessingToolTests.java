package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 가공 단계의 필수 공정 안내.
 *
 * <p>여기서 지키려는 것은 <b>담당자가 무엇에 동의했는지 아는 것</b>이다. 필수 공정을 tool 이
 * 조용히 채워 넣으면 결과는 같아 보여도 담당자는 자기 돈이 어디에 쓰이는지 모른 채 진행한다.
 * 반대로 빠뜨리면 한참 뒤인 생산 확정에서 막혀, 이미 가공비를 내고 물건을 받은 뒤가 된다.
 */
@SpringBootTest
class AgentProcessingToolTests {

    private static final String WALLET = "0x1111111111111111111111111111111111111111";

    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;
    @Autowired private MaterialRepository materials;
    @Autowired private MaterialBalanceRepository balances;

    @MockitoBean private LlmClient llmClient;

    private Long brandId;
    private Long wasteMaterialId;
    private Long virginMaterialId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "agent-proc@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
                .getId();

        // 아무 가공도 거치지 않은 폐데님. 세척·해체·선별이 모두 남아 있다.
        Material material = materials.save(Material.register(
                "MAT-900001", MaterialType.DENIM, MaterialRootOrigin.WASTE, "폐데님", null, "B",
                50_000L, null, null, brandId));
        wasteMaterialId = material.getId();
        balances.save(MaterialBalance.of(
                material.getId(), brandId, WALLET, material.getTotalQuantity()));

        // 새 원단. 씻을 것도 해체할 것도 없어 필수 공정이 없다.
        Material virgin = materials.save(Material.register(
                "MAT-900002", MaterialType.SILK, MaterialRootOrigin.VIRGIN, "실크 원단", null, "A",
                30_000L, null, null, brandId));
        virginMaterialId = virgin.getId();
        balances.save(MaterialBalance.of(
                virgin.getId(), brandId, WALLET, virgin.getTotalQuantity()));
    }

    @AfterEach
    void clean() {
        pendingActions.deleteAll();
        messages.deleteAll();
        conversations.deleteAll();
        balances.deleteAll();
        materials.deleteAll();
        users.deleteAll();
    }

    @Test
    void 남은_필수_공정을_알려준다() {
        callTool("check_processing_plan", "{\"materialId\":" + wasteMaterialId + "}");

        String result = lastToolResult();
        assertThat(result).contains("CLEANING").contains("DISASSEMBLY").contains("SORTING");
        assertThat(result).contains("\"readyForProduction\":false");
    }

    /**
     * 신소재는 <b>필수 공정이 없다</b>고 답해야 한다. "다 거쳤다" 와 다른 말이다.
     *
     * <p>예전에는 출처를 보지 않고 늘 세척·해체·선별을 필수로 넣었다. 그래서 신소재에 대해
     * 스스로 모순된 응답이 나갔다 — 필수 셋, 거친 것 없음, 빠진 것 없음, "모두 거쳤습니다".
     * 모델은 그 hint 를 그대로 옮겨 담당자에게 "필수 공정을 모두 거쳤으니 생산 확정할 준비가
     * 됐습니다" 라고 말했다. 아무 공정도 거치지 않은 새 원단이었다.
     */
    @Test
    void 신소재에는_필수_공정이_없다고_답한다() {
        callTool("check_processing_plan", "{\"materialId\":" + virginMaterialId + "}");

        String result = lastToolResult();
        assertThat(result).contains("\"requiredProcessTypes\":[]");
        assertThat(result).contains("\"readyForProduction\":true");
        assertThat(result).contains("필수 공정이 없습니다");
        // "모두 거쳤다" 는 거치지 않은 소재에 쓸 말이 아니다.
        assertThat(result).doesNotContain("모두 거쳤습니다");
    }

    @Test
    void 필수_공정이_빠지면_카드를_띄우지_않고_무엇이_빠졌는지_알려준다() {
        // 재단만 맡기려는 요청. 폐소재라 세척·해체·선별이 먼저다.
        ConversationDetailResponse detail = callTool(
                "create_processing_request",
                """
                {"materialId":%d,"processorId":7,"quantityGram":10000,\
                "processTypes":["CUTTING"],"summary":"재단 요청"}"""
                        .formatted(wasteMaterialId));

        assertThat(lastToolResult()).contains("REQUIRED_PROCESSING_MISSING");
        // 승인 뒤에 거절하면 담당자는 이미 누른 다음이다. 카드 자체가 생기면 안 된다.
        assertThat(detail.actions()).isEmpty();
        assertThat(pendingActions.count()).isZero();
    }

    @Test
    void 빠진_공정을_담당자가_동의해_넣으면_카드가_뜬다() {
        ConversationDetailResponse detail = callTool(
                "create_processing_request",
                """
                {"materialId":%d,"processorId":7,"quantityGram":10000,\
                "processTypes":["CLEANING","DISASSEMBLY","SORTING","CUTTING"],\
                "summary":"세척·해체·선별·재단 요청"}"""
                        .formatted(wasteMaterialId));

        assertThat(detail.actions()).hasSize(1);
        assertThat(detail.actions().getFirst().actionType()).isEqualTo(PendingActionType.CONFIRM);
        assertThat(lastToolResult()).contains("AWAITING_USER_CONFIRMATION");
    }

    @Test
    void 가공_결과_승인은_서명_카드로_뜬다() {
        // 승인하는 순간 투입 소재가 소비되고 결과물이 새 소재로 발행된다. 되돌릴 수 없어
        // 서명을 받는다 — 확인 카드로 낮추면 실수로 누르는 것과 서명하는 것이 같아진다.
        ConversationDetailResponse detail = callTool(
                "approve_processing_result",
                "{\"jobId\":1,\"summary\":\"가공 결과 승인\"}");

        assertThat(detail.actions()).hasSize(1);
        assertThat(detail.actions().getFirst().actionType()).isEqualTo(PendingActionType.WALLET_SIGN);
        assertThat(lastToolResult()).contains("AWAITING_USER_CONFIRMATION");
    }

    /** 가공 단계로 옮긴 대화에서 tool 하나를 부르게 한다. */
    private ConversationDetailResponse callTool(String toolName, String argumentsJson) {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        conversationService.start(brandId, "가공 맡기고 싶어요");
        Long conversationId = conversations.findAll().getFirst().getId();
        var conversation = conversations.findById(conversationId).orElseThrow();
        conversation.moveTo(JourneyPhase.PROCESSING);
        conversations.save(conversation);

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(null, List.of(new ToolCall("c1", toolName, argumentsJson))))
                .thenReturn(new LlmCompletion("확인했습니다.", List.of()));

        return conversationService.send(brandId, conversationId, "진행해줘");
    }

    private String lastToolResult() {
        return messages.findAll().stream()
                .filter(message -> message.getRole() == AgentMessageRole.TOOL)
                .reduce((first, second) -> second)
                .orElseThrow()
                .getToolPayloadJson();
    }
}
