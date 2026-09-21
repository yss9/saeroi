package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.AgentErrorCode;
import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.dto.JourneyProgressResponse;
import com.ssafy.upcycle.agent.dto.ConversationSummaryResponse;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.ConversationStatus;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대화 세션의 생성·조회·종료. 실제 응답 생성은 {@link AgentChatService} 가 한다. */
@Service
@RequiredArgsConstructor
public class AgentConversationService {

    private static final int MAX_LIST_SIZE = 50;

    private final AgentConversationRepository conversations;
    private final AgentPendingActionRepository pendingActions;
    private final ConversationStore store;
    private final AgentChatService chatService;
    private final AgentJourneyProgress journeyProgress;
    private final UserRepository users;

    /**
     * 대화를 시작하거나 이어간다.
     *
     * <p>첫 발화를 함께 받는다. 빈 대화를 만들어 두면 담당자가 아무 말도 없이 떠난 껍데기가
     * 쌓이고, 목록에서 그것들을 걸러내야 한다.
     *
     * <p><b>진행 중인 대화가 있으면 새로 만들지 않고 거기에 이어 붙인다.</b> 담당자당 활성
     * 대화는 하나다. 대화 창을 다시 열었을 때 이어서 진행하는 것이 정상 동작이고, 새 대화가
     * 생기면 앞서 만든 거래·가공 요청과의 연결이 끊긴다.
     *
     * <p>트랜잭션이 없다 — 안에서 LLM 을 부르기 때문이다. 대화 생성만 짧게 커밋한다.
     */
    public ConversationDetailResponse start(Long userId, String firstMessage) {
        requireBrand(userId);
        Long conversationId = openOrReuse(userId, firstMessage);
        chatService.send(userId, conversationId, firstMessage);
        return detail(userId, conversationId);
    }

    /**
     * 진행 중인 대화를 찾거나 새로 연다.
     *
     * <p>{@code @Transactional} 을 붙이지 않는다. 같은 클래스에서 부르면 프록시를 거치지 않아
     * 어차피 걸리지 않고, 붙어 있으면 걸린 것처럼 보여 더 나쁘다. 저장소 메서드가 각자
     * 트랜잭션을 갖고 있어 커밋은 정상이다.
     *
     * <p>조회와 생성 사이의 경합(동시 요청 두 개가 같이 "없다" 로 통과)은 여기서 막지 않고
     * {@code uk_agent_conversation_active} 유니크 제약이 막는다.
     */
    Long openOrReuse(Long userId, String firstMessage) {
        return conversations
                .findByBrandUserIdAndStatus(userId, ConversationStatus.ACTIVE)
                .map(AgentConversation::getId)
                .orElseGet(() -> conversations
                        .save(AgentConversation.start(userId, AgentChatService.titleFrom(firstMessage)))
                        .getId());
    }

    public ConversationDetailResponse send(Long userId, Long conversationId, String message) {
        requireBrand(userId);
        chatService.send(userId, conversationId, message);
        return detail(userId, conversationId);
    }

    /** 진행 중인 대화. 없으면 {@code null} — 화면이 "새로 시작" 을 그릴 수 있게 한다. */
    @Transactional(readOnly = true)
    public ConversationDetailResponse active(Long userId) {
        requireBrand(userId);
        return conversations
                .findByBrandUserIdAndStatus(userId, ConversationStatus.ACTIVE)
                .map(conversation -> ConversationDetailResponse.of(
                        conversation,
                        store.history(conversation.getId()),
                        pendingActions.findByConversationIdOrderByIdAsc(conversation.getId())))
                .orElse(null);
    }

    /**
     * 담당자가 완료 처리한다.
     *
     * <p>단계가 끝까지 가지 않아도 끝낼 수 있다. <b>그때까지 만들어진 거래·가공 요청은 그대로
     * 남는다</b> — 대화는 진행을 돕는 창구일 뿐 도메인 데이터의 소유자가 아니다. 종료 후에도
     * 담당자는 기존 화면에서 그 건들을 이어서 처리할 수 있다.
     */
    @Transactional
    public void complete(Long userId, Long conversationId) {
        requireBrand(userId);
        AgentConversation conversation = require(userId, conversationId);
        if (!conversation.isActive()) {
            return; // 이미 끝난 대화를 다시 끝내는 것은 오류가 아니다.
        }
        closePendingCards(conversationId);
        conversation.complete();
    }

    /**
     * 대화 밖에서 일어난 일을 대화에 알리고 모델이 이어 말하게 한다.
     *
     * <p>결제 복귀가 대표적이다. 담당자가 카카오페이에서 돌아오면 결제는 끝났는데 대화는
     * 그 사실을 모른다. 이 경로로 사실을 넣어 주면 모델이 현황을 확인해 안내한다.
     *
     * <p>사용자 발화가 아니라 <b>상황 보고</b>다. 그래서 화면에 말풍선으로 그리지 않아도
     * 되도록 assistant 역할로 남긴다 — 담당자가 하지 않은 말이 자기 말풍선으로 보이면
     * 대화 기록이 거짓이 된다.
     */
    public ConversationDetailResponse reportEvent(Long userId, Long conversationId, String note) {
        requireBrand(userId);
        chatService.reportEvent(userId, conversationId, note);
        return detail(userId, conversationId);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> list(Long userId, int size) {
        requireBrand(userId);
        int capped = Math.clamp(size, 1, MAX_LIST_SIZE);
        return conversations.findByBrandUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, capped)).stream()
                .map(ConversationSummaryResponse::from)
                .toList();
    }

    /** 대화 위 경과 표시. 도메인만 읽으므로 크레딧이 들지 않는다. */
    @Transactional(readOnly = true)
    public JourneyProgressResponse progress(Long userId, Long conversationId) {
        return journeyProgress.of(userId, require(userId, conversationId));
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse detail(Long userId, Long conversationId) {
        requireBrand(userId);
        AgentConversation conversation = require(userId, conversationId);
        List<AgentMessage> messages = store.history(conversationId);
        List<AgentPendingAction> actions =
                pendingActions.findByConversationIdOrderByIdAsc(conversationId);
        return ConversationDetailResponse.of(conversation, messages, actions);
    }

    @Transactional
    public void abandon(Long userId, Long conversationId) {
        requireBrand(userId);
        AgentConversation conversation = require(userId, conversationId);
        if (!conversation.isActive()) {
            return; // 이미 끝난 대화를 다시 끝내는 것은 오류가 아니다.
        }
        closePendingCards(conversationId);
        conversation.abandon();
    }

    /** 떠 있던 카드를 닫는다. 남겨 두면 대화는 끝났는데 카드만 살아 있는 상태가 된다. */
    private void closePendingCards(Long conversationId) {
        pendingActions
                .findByConversationIdAndStatus(conversationId, PendingActionStatus.PENDING)
                .forEach(AgentPendingAction::expire);
    }

    AgentConversation require(Long userId, Long conversationId) {
        return conversations
                .findByIdAndBrandUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_CONVERSATION_NOT_FOUND));
    }

    /**
     * BRAND 인지 토큰이 아니라 DB 로 확인한다.
     *
     * <p>이 저장소의 인가 정본은 URL 규칙이나 토큰 claim 이 아니라 서비스 계층의 DB 재조회다
     * ({@code docs/superpowers/specs/2026-09-08-role-permission-matrix.md}). 관리자가 역할을
     * 내린 직후에도 만료 전 토큰이 남아 있을 수 있어서다.
     */
    private void requireBrand(Long userId) {
        boolean isBrand = users.findById(userId)
                .map(user -> user.getRole() == UserRole.BRAND)
                .orElse(false);
        if (!isBrand) {
            throw new BusinessException(AgentErrorCode.AGENT_FORBIDDEN);
        }
    }
}
