package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
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
 * tool 이 실패를 돌려줄 때 <b>다음에 할 일까지</b> 주는가.
 *
 * <p>JSON Schema 의 {@code enum} 은 강제가 아니라 지시여서 모델이 없는 값을 낼 수 있다.
 * 실제로 소재 종류에서 {@code LEATHER} 를 냈다 — 지금 분류에 없고 {@code ETC} 로 옮겨진
 * 값이다. 그때 "알 수 없는 값" 만 돌려주면 모델이 또 다른 없는 값을 지어내 왕복이 늘고,
 * 왕복은 그대로 크레딧이다.
 */
@SpringBootTest
class AgentToolHintTests {

    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;

    @MockitoBean private LlmClient llmClient;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "agent-hint@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
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
    void 없는_소재종류를_내면_가능한_값과_대안을_알려준다() {
        callTool("search_listings", "{\"materialType\":\"LEATHER\"}");

        String result = lastToolResult();
        // 가능한 값을 함께 줘야 다음 호출에서 고친다.
        assertThat(result).contains("COTTON").contains("ETC");
        // 분류에 없는 개념이라면 필터를 버리고 이름으로 찾는 길을 알려준다.
        assertThat(result).contains("keyword");
    }

    @Test
    void 검색이_0건이면_무엇을_바꿀지_지목해_준다() {
        callTool("search_listings", "{\"materialType\":\"SILK\"}");

        // "소재 종류를 넓혀보세요" 같은 모호한 안내로는 모델이 무엇을 바꿔야 할지 모른다.
        assertThat(lastToolResult()).contains("materialType 을 비우고");
    }

    private void callTool(String toolName, String argumentsJson) {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        conversationService.start(brandId, "소재 찾아줘");
        var conversation = conversations.findAll().getFirst();
        conversation.moveTo(JourneyPhase.MATERIAL_SOURCING);
        conversations.save(conversation);

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(null, List.of(new ToolCall("c1", toolName, argumentsJson))))
                .thenReturn(new LlmCompletion("확인했습니다.", List.of()));

        conversationService.send(brandId, conversation.getId(), "찾아줘");
    }

    private String lastToolResult() {
        return messages.findAll().stream()
                .filter(message -> message.getRole() == AgentMessageRole.TOOL)
                .reduce((first, second) -> second)
                .orElseThrow()
                .getToolPayloadJson();
    }
}
