package com.ssafy.upcycle.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 지금 떠 있는 카드가 <b>어떤 일에 대한 것인지</b> 한 곳에서 판정한다.
 *
 * <h2>왜 필요한가 — 카드를 만드는 문이 둘이 되었다</h2>
 *
 * <p>모델이 띄우는 문({@link AgentToolExecutor})과 서버가 스스로 띄우는 문
 * ({@code BrandStepAutoProposer})이 있다. 처음에는 중복 방지를 <b>새 문에만</b> 달았다.
 * 그랬더니 자동으로 띄운 카드가 떠 있는데 모델이 80초 뒤 같은 일에 대해 카드를 하나 더
 * 만들었다 — 담당자는 둘 중 하나만 누르고, 나머지 하나는 <b>영원히 남는다.</b>
 *
 * <p>실제로 그랬다. 소재 세 건을 한 번에 살 때 온체인 기록 카드가 자동으로 두 장 떴고
 * (64·65번), 모델이 다시 세 장을 띄웠다(66·67·68번). 담당자는 모델 것을 눌렀고 오른쪽
 * 패널에는 "지갑 서명이 필요합니다" 가 두 건 남았다. 서명은 다 끝났는데도.
 *
 * <p>한 문만 막아서는 안 된다. 카드를 만드는 <b>모든 길이 여기를 지나게</b> 해서, 어느 쪽이
 * 먼저 띄웠든 같은 일에 두 장이 생기지 않게 한다.
 *
 * <h2>무엇을 "같은 일" 로 보는가</h2>
 *
 * <p>도구 이름이 같고 <b>대상이 같을 때</b>다. 도구 이름만 보면 거래 27의 서명 카드가 떠
 * 있다고 거래 28의 카드를 막는다 — 소재를 여러 건 사는 경우가 정확히 그렇다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenCards {

    /**
     * 인자에서 대상 번호를 찾을 때 볼 필드들.
     *
     * <p>도구마다 이름이 다르지만 한 인자에 둘이 함께 오지는 않는다. 순서대로 보고 처음
     * 찾은 것을 쓴다.
     */
    private static final List<String> REFERENCE_FIELDS =
            List.of("tradeId", "jobId", "batchId", "productModelId", "listingId");

    private final AgentPendingActionRepository pendingActions;
    private final ObjectMapper objectMapper;

    /** 이 대화에서 아직 담당자를 기다리는 카드들. */
    public List<AgentPendingAction> of(Long conversationId) {
        return pendingActions.findByConversationIdAndStatus(
                conversationId, PendingActionStatus.PENDING);
    }

    /**
     * 이 대화에서 담당자가 <b>취소한</b> 카드들. 서버가 같은 일을 다시 띄우지 않으려고 본다.
     */
    public List<AgentPendingAction> rejected(Long conversationId) {
        return pendingActions.findByConversationIdAndStatus(
                conversationId, PendingActionStatus.REJECTED);
    }

    /**
     * 같은 일에 대해 이미 떠 있는 카드.
     *
     * @param open      이 대화에서 떠 있는 카드들. 한 번 읽어 여러 번 묻는다
     * @param toolName  띄우려는 도구
     * @param arguments 띄우려는 인자. 여기서 대상 번호를 꺼낸다
     */
    public Optional<AgentPendingAction> sameWork(
            List<AgentPendingAction> open, String toolName, JsonNode arguments) {
        Optional<Long> reference = referenceOf(arguments);
        if (reference.isEmpty()) {
            // 대상을 모르면 중복으로 보지 않는다. 카드가 하나 더 뜨는 편이, 다른 일에 대한
            // 카드를 막아 담당자가 아무것도 못 하게 되는 것보다 낫다.
            return Optional.empty();
        }
        return open.stream()
                .filter(card -> card.getToolName().equals(toolName))
                .filter(card -> payloadOf(card)
                        .flatMap(this::referenceOf)
                        .map(reference.get()::equals)
                        .orElse(false))
                .findFirst();
    }

    /** 인자에 적힌 대상 번호. 거래·작업·배치·제품 중 하나다. */
    public Optional<Long> referenceOf(JsonNode arguments) {
        for (String field : REFERENCE_FIELDS) {
            if (arguments.hasNonNull(field) && arguments.get(field).isNumber()) {
                return Optional.of(arguments.get(field).asLong());
            }
        }
        return Optional.empty();
    }

    private Optional<JsonNode> payloadOf(AgentPendingAction card) {
        try {
            return Optional.of(objectMapper.readTree(card.getPayloadJson()));
        } catch (JsonProcessingException malformed) {
            // 모델이 만든 인자라 깨질 수 있다. 판정하지 못하면 중복으로 보지 않는다.
            log.debug("Pending action payload unreadable: id={}", card.getId());
            return Optional.empty();
        }
    }
}
