package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.entity.AgentMessageRole;
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
 * 구매 카드는 <b>살 수 있을 때만</b> 뜬다. 결제 카드라 누른 뒤에 실패하면 가장 아프다.
 *
 * <h2>없는 판매 글 번호가 턴 전체를 죽였다</h2>
 *
 * <p>카드 제목과 금액을 만들려고 판매 글을 조회하는데, 그 조회는 없는 번호에 예외를 던지는
 * {@code @Transactional} 서비스였다. 도구 호출은 한 트랜잭션 안에서 도는데, 그 안에서 예외가
 * 트랜잭션 경계를 지나면 잡아도 트랜잭션이 rollback-only 로 표시된다. 그래서 모델이 번호를
 * 잘못 넣는 것만으로 그 턴의 기록이 통째로 사라지고 담당자는 오류 화면을 봤다. 모델에게
 * "그런 판매 글이 없다" 고 알려 다시 찾게 하면 되는 일이었다.
 */
@SpringBootTest
class PurchaseMaterialPreCheckTests {

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
                        "agent-purchase@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
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
    void 없는_판매_글이면_카드_없이_다시_찾으라고_알리고_턴은_살아_있다() {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null,
                        List.of(new ToolCall(
                                "call-1",
                                "purchase_material",
                                "{\"listingId\":999999,\"quantityGram\":12500,\"summary\":\"폐데님 구매\"}"))))
                .thenReturn(new LlmCompletion("그 판매 글을 찾지 못했어요. 다시 찾아볼게요.", List.of()));

        assertThatCode(() -> conversationService.start(brandId, "데님 12.5kg 사줘"))
                .doesNotThrowAnyException();

        assertThat(pendingActions.count()).as("살 수 없는 글에 결제 카드를 띄우면 안 된다").isZero();
        assertThat(lastToolResult()).contains("LISTING_NOT_FOUND").contains("search_listings");
        assertThat(messages.findAll())
                .as("턴의 기록이 롤백되지 않고 남아야 한다")
                .anyMatch(message -> "그 판매 글을 찾지 못했어요. 다시 찾아볼게요.".equals(message.getContent()));
    }

    private String lastToolResult() {
        return messages.findAll().stream()
                .filter(message -> message.getRole() == AgentMessageRole.TOOL)
                .reduce((first, second) -> second)
                .orElseThrow()
                .getToolPayloadJson();
    }
}
