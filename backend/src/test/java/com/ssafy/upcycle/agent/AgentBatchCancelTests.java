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
import com.ssafy.upcycle.agent.service.PendingActionService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.production.dto.ProductionBatchOpenRequest;
import com.ssafy.upcycle.production.dto.ProductionInputRequest;
import com.ssafy.upcycle.production.dto.ProductionInputsRequest;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화에서 생산 배치를 정리할 수 있는가.
 *
 * <p><b>묶인 소재를 푸는 유일한 길이다.</b> 배치에 소재를 투입하면 그만큼 잠금이 걸리는데
 * 그 잠금에는 만료가 없다 — 기한이 지나 풀리면 확정 시점에 소재가 사라진 배치가 되기
 * 때문이다. 그래서 진행하지 않을 배치를 그냥 두면 그 소재를 다른 배치에도, 판매 글에도 쓸
 * 수 없다. 판매 등록이 같은 가용량 계산을 쓴다.
 *
 * <p>도메인 쪽 {@code cancel} 과 엔드포인트는 이미 있었는데 <b>화면도 에이전트도 부르지
 * 않아 닿을 수 없는 기능이었다.</b> 담당자에게 남은 길은 원하지 않는 제품을 확정해 버리거나
 * DB 를 직접 고치는 것뿐이었다.
 */
@SpringBootTest
@Transactional
class AgentBatchCancelTests {

    private static final String WALLET = "0x2222222222222222222222222222222222222222";

    @Autowired private AgentConversationService conversationService;
    @Autowired private PendingActionService pendingActionService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;
    @Autowired private MaterialRepository materials;
    @Autowired private MaterialBalanceRepository balances;
    @Autowired private MaterialLockRepository locks;
    @Autowired private ProductionBatchService batchService;
    @Autowired private ProductionBatchRepository batches;
    @Autowired private UserWalletRepository wallets;

    @MockitoBean private LlmClient llmClient;

    private Long brandId;
    private Long batchId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "agent-cancel@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
                .getId();

        Material material = materials.save(Material.register(
                "MAT-910001", MaterialType.SILK, MaterialRootOrigin.VIRGIN, "실크 원단", null, "A",
                10_000L, null, null, brandId));
        balances.save(MaterialBalance.of(material.getId(), brandId, WALLET, 10_000L));
        // 배치를 열 때 확정에 쓸 서명 주소를 고정하므로 지갑이 연결돼 있어야 한다.
        wallets.save(UserWallet.link(brandId, WALLET, 31221L));

        batchId = batchService
                .open(brandId, new ProductionBatchOpenRequest("버릴 배치", null, 10))
                .productionBatchId();
        // 9kg 을 투입해 둔다. 확정하지 않으면 그만큼 가용량이 줄어든 채 남는다.
        batchService.addInputs(brandId, batchId, new ProductionInputsRequest(List.of(
                new ProductionInputRequest(material.getId(), 9_000L, 9_000L, 0L, 0L, null))));
    }

    @Test
    void 배치를_취소하면_묶인_소재가_풀린다() {
        assertThat(activeLockCount()).isEqualTo(1);

        ConversationDetailResponse proposed = callTool(
                "cancel_production_batch",
                "{\"batchId\":%d,\"summary\":\"버릴 배치 정리 - 실크 9kg 잠금 해제\"}".formatted(batchId));

        // 되돌리기 어려운 쓰기라 바로 실행되지 않는다. 확인 카드가 먼저다.
        assertThat(proposed.actions()).hasSize(1);
        assertThat(proposed.actions().getFirst().actionType()).isEqualTo(PendingActionType.CONFIRM);
        assertThat(activeLockCount()).as("승인 전에는 아무것도 바뀌지 않는다").isEqualTo(1);

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("정리했습니다.", List.of()));
        pendingActionService.complete(brandId, proposed.actions().getFirst().id(), null);

        assertThat(batches.findById(batchId).orElseThrow().getStatus())
                .isEqualTo(ProductionBatchStatus.CANCELED);
        assertThat(activeLockCount()).as("잠금이 풀려야 그 소재를 다시 쓸 수 있다").isZero();
    }

    /**
     * 편집이 닫힌 배치는 취소할 수 없다. 거절은 읽을 수 있는 문장으로 담당자에게 간다.
     *
     * <p>{@code SIGNING} 으로 만들어 확인한다 — 서명한 내용과 저장된 내용이 달라지면 온체인
     * 기록이 무엇을 증명하는지 알 수 없게 되므로 그 뒤로는 손댈 수 없다. 확정된 배치도 같은
     * 문으로 막힌다.
     */
    @Test
    void 편집이_닫힌_배치는_취소되지_않고_이유가_대화에_남는다() {
        var batch = batches.findById(batchId).orElseThrow();
        batch.startSigning();
        batches.save(batch);

        ConversationDetailResponse proposed = callTool(
                "cancel_production_batch",
                "{\"batchId\":%d,\"summary\":\"정리\"}".formatted(batchId));

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("확인했습니다.", List.of()));
        ConversationDetailResponse after = pendingActionService.complete(
                brandId, proposed.actions().getFirst().id(), null);

        assertThat(batches.findById(batchId).orElseThrow().getStatus())
                .isEqualTo(ProductionBatchStatus.SIGNING);
        assertThat(activeLockCount()).as("거절됐으니 잠금도 그대로다").isEqualTo(1);
        assertThat(after.messages()).anyMatch(message -> message.content() != null
                && message.content().contains("요청을 처리하지 못했습니다"));
    }

    private long activeLockCount() {
        return locks.findAll().stream()
                .filter(lock -> lock.getStatus() == com.ssafy.upcycle.material.entity.MaterialLockStatus.ACTIVE)
                .count();
    }

    /** 생산 단계로 옮긴 대화에서 tool 하나를 부르게 한다. */
    private ConversationDetailResponse callTool(String toolName, String argumentsJson) {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        conversationService.start(brandId, "생산 진행할게요");
        Long conversationId = conversations.findAll().getFirst().getId();
        var conversation = conversations.findById(conversationId).orElseThrow();
        conversation.moveTo(JourneyPhase.PRODUCTION);
        conversations.save(conversation);

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(null, List.of(new ToolCall("c1", toolName, argumentsJson))))
                .thenReturn(new LlmCompletion("확인했습니다.", List.of()));

        return conversationService.send(brandId, conversationId, "진행해줘");
    }

    @SuppressWarnings("unused")
    private String lastToolResult() {
        return messages.findAll().stream()
                .filter(message -> message.getRole() == AgentMessageRole.TOOL)
                .reduce((first, second) -> second)
                .orElseThrow()
                .getToolPayloadJson();
    }
}
