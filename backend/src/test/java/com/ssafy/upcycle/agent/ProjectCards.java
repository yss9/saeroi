package com.ssafy.upcycle.agent;

import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import java.time.LocalDateTime;

/**
 * 테스트에서 "이 대화가 시작한 일" 을 만든다 — <b>승인된 카드 한 장</b>을 남긴다.
 *
 * <p>대화의 프로젝트는 승인된 카드에서 읽는다({@code AgentProjects}). 그래서 거래를 이 대화의
 * 것으로 만들려면 그 거래를 만든 구매 카드가 승인돼 있어야 한다.
 */
final class ProjectCards {

    private ProjectCards() {}

    /** 이 대화에서 산 거래. */
    static void boughtTrade(AgentPendingActionRepository cards, Long conversationId, long tradeId) {
        completed(cards, conversationId, "purchase_material", "{}", "{\"tradeId\":%d}".formatted(tradeId));
    }

    /** 이 대화에서 맡긴 가공. */
    static void requestedProcessing(
            AgentPendingActionRepository cards, Long conversationId, long jobId, long materialId) {
        completed(cards, conversationId, "create_processing_request",
                "{\"materialId\":%d}".formatted(materialId),
                "{\"request\":{\"jobId\":%d,\"materialId\":%d}}".formatted(jobId, materialId));
    }

    /** 이 대화에서 연 배치. */
    static void openedBatch(AgentPendingActionRepository cards, Long conversationId, long batchId) {
        completed(cards, conversationId, "open_production_batch", "{}",
                "{\"batch\":{\"productionBatchId\":%d}}".formatted(batchId));
    }

    /** 이 대화에서 등록한 제품. */
    static void registeredProduct(AgentPendingActionRepository cards, Long conversationId, long productModelId) {
        completed(cards, conversationId, "register_product_model", "{}",
                "{\"productModel\":{\"productModelId\":%d}}".formatted(productModelId));
    }

    static AgentPendingAction completed(
            AgentPendingActionRepository cards,
            Long conversationId,
            String toolName,
            String payloadJson,
            String resultJson) {
        LocalDateTime now = LocalDateTime.now();
        AgentPendingAction card = AgentPendingAction.propose(
                conversationId, 1L, PendingActionType.CONFIRM, toolName, "테스트 카드", payloadJson, 1,
                now.plusHours(1));
        card.complete(resultJson, now);
        return cards.save(card);
    }
}
