package com.ssafy.upcycle.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.repository.ListingSearchQueryRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이 대화에서 모델이 <b>실제로 찾아 본 판매 글</b>.
 *
 * <h2>왜 필요한가 — 판매 글 번호를 짐작해 엉뚱한 소재를 샀다</h2>
 *
 * <p>모델에게 보내는 이력은 최근 것만 남는다({@code ConversationStore}). 그런데 판매 글 번호는
 * 검색 결과에만 있고, 모델이 담당자에게 한 말에는 이름과 단가만 있다. 구매할 즈음 그 검색 결과가
 * 잘려 나가자 모델은 번호를 짐작했다 — 폐데님 원단(21번)을 보여 주고 합의까지 해 놓고, 본 적도
 * 없는 19번(깨끗한 데님)으로 결제 카드를 띄웠다. 담당자는 6,000원짜리에 동의했는데 15,000원이
 * 결제됐다. 면(20번)도 23번(폴리에스터)으로 샀다.
 *
 * <p>그래서 이 대화에서 받은 검색 결과를 이력이 아니라 <b>저장된 도구 결과</b>에서 다시 읽는다. 잘려도
 * 사라지지 않는다. 시스템 프롬프트의 상태 블록이 이것을 보여 주고({@code AgentPendingWork}), 구매
 * 도구는 여기 없는 번호를 받지 않는다({@code PurchaseMaterialTool}).
 */
@Component
@RequiredArgsConstructor
public class ConversationListings {

    /** 판매 글 번호를 알려 주는 도구들. */
    private static final Set<String> LISTING_TOOLS = Set.of("search_listings", "quote_trade");

    private final AgentMessageRepository messages;
    private final ListingSearchQueryRepository listings;
    private final ObjectMapper objectMapper;

    /** 이 대화의 도구 결과에 나온 판매 글 번호. 가장 최근에 본 것부터. */
    public List<Long> seen(Long conversationId) {
        if (conversationId == null) {
            return List.of();
        }
        List<AgentMessage> stored = messages.findByConversationIdOrderBySequenceNoAsc(conversationId);
        Set<Long> ids = new LinkedHashSet<>();
        for (int index = stored.size() - 1; index >= 0; index--) {
            AgentMessage message = stored.get(index);
            if (message.getRole() != AgentMessageRole.TOOL || !LISTING_TOOLS.contains(message.getToolName())) {
                continue;
            }
            ids.addAll(listingIdsIn(message.getToolPayloadJson()));
        }
        return List.copyOf(ids);
    }

    /**
     * 이 대화에서 본 판매 글의 <b>지금</b> 모습. 단가와 남은 양은 그 사이 바뀌었을 수 있어 다시 읽는다.
     * 없어진 판매 글은 빠진다.
     */
    public List<ListingResponse> current(Long conversationId, int limit) {
        List<ListingResponse> found = new ArrayList<>();
        for (Long id : seen(conversationId)) {
            if (found.size() >= limit) {
                break;
            }
            ListingResponse listing = listings.findDetail(id);
            if (listing != null) {
                found.add(listing);
            }
        }
        return found;
    }

    private List<Long> listingIdsIn(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            List<Long> ids = new ArrayList<>();
            // search_listings 는 목록을, quote_trade 는 한 건을 돌려준다.
            for (JsonNode listing : root.path("listings")) {
                ids.add(idOf(listing));
            }
            ids.add(idOf(root));
            return ids.stream().filter(Objects::nonNull).toList();
        } catch (Exception unreadable) {
            // 읽지 못한 결과 하나 때문에 나머지를 잃지 않는다.
            return List.of();
        }
    }

    private static Long idOf(JsonNode node) {
        JsonNode id = node.path("listingId");
        return id.isIntegralNumber() && id.asLong() > 0 ? id.asLong() : null;
    }
}
