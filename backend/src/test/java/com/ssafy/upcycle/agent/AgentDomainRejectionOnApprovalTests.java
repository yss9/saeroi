package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.dto.PendingActionResponse;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.agent.service.PendingActionService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.agent.service.llm.LlmMessage;
import com.ssafy.upcycle.agent.service.llm.LlmRole;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 담당자가 승인했는데 도메인이 거절하는 경우.
 *
 * <p>{@code PendingActionService.complete} 는 이 경우를 잡아 대화에 이유를 남기고 정상
 * 응답으로 닫는다 — "예외를 그대로 던지면 사용자는 왜 안 되는지 모른 채 카드만 사라진
 * 화면을 보게 된다" 가 그 주석의 설명이다. 그 의도가 실제로 지켜지는지 본다.
 */
@SpringBootTest
class AgentDomainRejectionOnApprovalTests {

    @Autowired private AgentConversationService conversationService;
    @Autowired private PendingActionService pendingActionService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;

    @MockitoBean private LlmClient llmClient;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "reject-brand@test.com", "{noop}x", "brand", "010-0000-0001", UserRole.BRAND))
                .getId();
    }

    @AfterEach
    void clean() {
        pendingActions.deleteAll();
        messages.deleteAll();
        conversations.deleteAll();
        users.deleteAll();
    }

    @Test
    void 도메인이_거절하면_모델이_이유와_다음_할_일을_한_번만_전한다() {
        PendingActionResponse action = proposeProductRegistration();

        List<String> systemSeen = new ArrayList<>();
        when(llmClient.complete(anyList(), anyList())).thenAnswer(invocation -> {
            List<LlmMessage> prompt = invocation.getArgument(0);
            prompt.stream()
                    .filter(message -> message.role() == LlmRole.SYSTEM)
                    .forEach(message -> systemSeen.add(message.content()));
            return new LlmCompletion("상품 이미지를 찾지 못했어요. 이미지를 다시 올려 주세요.", List.of());
        });

        // 저장소에 없는 이미지 키다. ProductModelRegistrationService.register 가
        // requireStoredImage 에서 BusinessException 을 던진다.
        assertThatCode(() -> pendingActionService.complete(
                        brandId, action.id(), "{\"thumbnailUrl\":\"product-images/없는키.png\"}"))
                .doesNotThrowAnyException();

        assertThat(pendingActions.findById(action.id()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.REJECTED);

        // 이유는 모델에게 간다. 예전에는 이 문장이 실패한 트랜잭션과 함께 지워졌다.
        assertThat(systemSeen).anyMatch(block -> block.contains("요청을 처리하지 못했습니다"));

        // **실패했을 때야말로 다음에 뭘 해야 하는지 말해야 한다.** 그리고 한 번만 말한다 —
        // 서버 문장과 모델 문장이 나란히 뜨면 담당자는 같은 실패를 두 번 읽는다.
        List<String> visible = visibleAssistantLines();
        assertThat(visible)
                .as("모델이 이어 말해야 담당자가 다음 단계를 안다")
                .anyMatch(content -> content.contains("이미지를 다시 올려"));
        assertThat(visible)
                .as("모델이 말했으면 서버 문장은 나가지 않는다")
                .noneMatch(content -> content.contains("처리하지 못했습니다"));
    }

    /** 모델마저 말하지 못하면 서버 문장이 <b>한 번</b> 나간다. 아무 반응이 없으면 그것도 실패로 읽힌다. */
    @Test
    void 거절_뒤_모델이_응답하지_못하면_서버_문장이_한_번_남는다() {
        PendingActionResponse action = proposeProductRegistration();

        when(llmClient.complete(anyList(), anyList())).thenThrow(new IllegalStateException("llm down"));

        assertThatCode(() -> pendingActionService.complete(
                        brandId, action.id(), "{\"thumbnailUrl\":\"product-images/없는키.png\"}"))
                .doesNotThrowAnyException();

        assertThat(visibleAssistantLines())
                .filteredOn(content -> content.contains("처리하지 못했습니다"))
                .hasSize(1);
    }

    @Test
    void 승인_뒤_모델_호출은_트랜잭션_밖에서_일어난다() {
        PendingActionResponse action = proposeSignature();

        // LLM 호출은 20~30초가 걸린다. 그 사이 DB 커넥션을 쥐고 있으면 기본 풀 10개가
        // 동시 10명에서 고갈되어 앱 전체가 멈춘다. 모델을 부르는 순간을 붙잡아 확인한다.
        boolean[] insideTransaction = {true};
        when(llmClient.complete(anyList(), anyList())).thenAnswer(invocation -> {
            insideTransaction[0] = TransactionSynchronizationManager.isActualTransactionActive();
            return new LlmCompletion("기록했습니다.", List.of());
        });

        pendingActionService.complete(
                brandId, action.id(), "{\"txHash\":\"0xabc1234567890def1234567890abcdef12345678\"}");

        assertThat(insideTransaction[0])
                .as("승인 경로가 LLM 응답을 기다리는 동안 DB 커넥션을 쥐고 있으면 안 된다")
                .isFalse();
    }

    /** 서명 카드가 하나 떠 있는 상태를 만든다. 온체인 기록은 도메인을 거치지 않는다. */
    private PendingActionResponse proposeSignature() {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        ConversationDetailResponse started = conversationService.start(brandId, "소재 발행할게요");

        var conversation = conversations.findById(started.id()).orElseThrow();
        conversation.moveTo(JourneyPhase.MATERIAL_SOURCING);
        conversations.save(conversation);

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null,
                        List.of(new ToolCall(
                                "call-1",
                                "request_chain_order",
                                "{\"tradeId\":1,\"summary\":\"소재 1kg 온체인 기록\"}"))))
                .thenReturn(new LlmCompletion("확인해 주세요.", List.of()));

        return conversationService.send(brandId, started.id(), "진행해줘").actions().getFirst();
    }

    /** 제품 등록 카드가 하나 떠 있는 상태를 만든다. */
    private PendingActionResponse proposeProductRegistration() {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        ConversationDetailResponse started = conversationService.start(brandId, "제품 등록할게요");

        var conversation = conversations.findById(started.id()).orElseThrow();
        conversation.moveTo(JourneyPhase.PRODUCT);
        conversations.save(conversation);

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null,
                        List.of(new ToolCall(
                                "call-1",
                                "register_product_model",
                                """
                                {"name":"데님 토트백","category":"BAG","price":48000,\
                                "description":"폐데님으로 만든 토트백","summary":"제품 등록"}"""))))
                .thenReturn(new LlmCompletion("확인해 주세요.", List.of()));

        ConversationDetailResponse detail = conversationService.send(brandId, started.id(), "등록해줘");
        return detail.actions().getFirst();
    }

    /** 담당자 화면에 뜨는 모델 쪽 줄. */
    private List<String> visibleAssistantLines() {
        return conversationService
                .detail(brandId, conversations.findAll().getFirst().getId())
                .messages().stream()
                .filter(message -> message.role() == AgentMessageRole.ASSISTANT)
                .map(message -> message.content())
                .filter(content -> content != null)
                .toList();
    }
}
