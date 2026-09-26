package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.service.ConversationListings;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.repository.ListingSearchQueryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 이 대화에서 모델이 <b>실제로 찾아 본 판매 글</b>을 저장된 도구 결과에서 다시 읽는다.
 *
 * <p>모델에게 보내는 이력은 최근 것만 남아, 판매 글 번호가 담긴 검색 결과가 구매 전에 잘려 나갔다.
 * 모델은 번호를 짐작했고 폐데님 원단(21번)으로 합의해 놓고 19번(깨끗한 데님)을 샀다. 저장된 도구
 * 결과는 잘리지 않는다.
 */
class ConversationListingsTests {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 25, 2, 40);

    private AgentMessageRepository messages;
    private ListingSearchQueryRepository listings;
    private ConversationListings conversationListings;

    @BeforeEach
    void setUp() {
        messages = mock(AgentMessageRepository.class);
        listings = mock(ListingSearchQueryRepository.class);
        conversationListings = new ConversationListings(messages, listings, new ObjectMapper());
    }

    @Test
    void 검색과_견적에서_본_번호를_최근_것부터_중복_없이_모은다() {
        givenStored(
                tool(1, "search_listings", "{\"listings\":[{\"listingId\":21},{\"listingId\":22}]}"),
                tool(2, "get_my_materials", "{\"materials\":[{\"listingId\":99}]}"),
                tool(3, "search_listings", "{\"listings\":[{\"listingId\":20}]}"),
                tool(4, "quote_trade", "{\"listingId\":21,\"quantity\":3000}"));

        // 소재 조회 같은 다른 도구의 번호는 판매 글 번호가 아니다.
        assertThat(conversationListings.seen(19L)).containsExactly(21L, 20L, 22L);
    }

    @Test
    void 읽을_수_없는_결과는_건너뛴다() {
        givenStored(
                tool(1, "search_listings", "not json"),
                tool(2, "search_listings", "{\"listings\":[{\"listingId\":20}]}"));

        assertThat(conversationListings.seen(19L)).containsExactly(20L);
    }

    @Test
    void 지금_모습을_다시_읽고_없어진_판매_글은_뺀다() {
        givenStored(tool(1, "search_listings", "{\"listings\":[{\"listingId\":21},{\"listingId\":22}]}"));
        ListingResponse denim = mock(ListingResponse.class);
        when(listings.findDetail(anyLong())).thenReturn(null);
        when(listings.findDetail(21L)).thenReturn(denim);

        assertThat(conversationListings.current(19L, 10)).containsExactly(denim);
    }

    private void givenStored(AgentMessage... stored) {
        List<AgentMessage> list = List.of(stored);
        when(messages.findByConversationIdOrderBySequenceNoAsc(19L)).thenReturn(list);
    }

    private static AgentMessage tool(int sequence, String toolName, String payload) {
        return AgentMessage.toolResult(19L, sequence, toolName, "call-" + sequence, payload, AT);
    }
}
