package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.trade.SearchListingsTool;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.service.ListingQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 잔량을 <b>담당자가 읽을 문장으로</b> 함께 돌려준다.
 *
 * <h2>왜 필요한가 — 환산을 모델에게 맡기면 틀린다</h2>
 *
 * <p>필드 이름은 {@code availableQuantityGram} 으로 단위가 적혀 있었다. 그런데도 94,000g 을
 * <b>"94g"</b> 이라고 말했다. 나눗셈은 맞았고 단위만 틀렸는데, 그 숫자로 "필요한 12.5kg 에
 * 한참 못 미친다" 는 결론을 내고 담당자에게 <b>주문 수량을 줄이라고 권했다.</b> 실제로는
 * 94kg 이라 넉넉했다.
 *
 * <p>프롬프트에는 "담당자에게 보여줄 때는 kg 으로 환산해 말한다" 는 규칙이 이미 있었다.
 * 규칙으로 막히지 않는 자리라, 읽어서 그대로 옮길 문장을 준다.
 */
class SearchListingsQuantityTextTests {

    private final ListingQueryService listingQuery = mock(ListingQueryService.class);
    private final SearchListingsTool tool =
            new SearchListingsTool(new ObjectMapper(), listingQuery, new com.ssafy.upcycle.agent.service.CompanyNames(org.mockito.Mockito.mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class)));

    @Test
    void 잔량을_kg_문장으로_함께_돌려준다() {
        givenListing(94_000);

        String payload = search();

        assertThat(payload).contains("\"availableQuantityGram\":94000");
        assertThat(payload).contains("\"availableText\":\"94kg\"");
    }

    /** 딱 떨어지지 않으면 소수점 한 자리까지. 그 아래는 읽히지 않는다. */
    @Test
    void 딱_떨어지지_않는_양은_소수점_한_자리로_말한다() {
        givenListing(2_500);

        assertThat(search()).contains("\"availableText\":\"2.5kg\"");
    }

    /** 1kg 미만도 kg 으로 말한다 — 화면의 다른 곳과 단위가 달라지면 비교가 안 된다. */
    @Test
    void 일_킬로그램_미만도_kg_으로_말한다() {
        givenListing(300);

        assertThat(search()).contains("\"availableText\":\"0.3kg\"");
    }

    /** 모델이 직접 환산하지 않도록 결과에 못 박는다. */
    @Test
    void 직접_환산하지_말라고_알린다() {
        givenListing(94_000);

        assertThat(search()).contains("availableText 를 그대로 씁니다");
    }

    private void givenListing(long availableGram) {
        ListingResponse listing = mock(ListingResponse.class);
        when(listing.listingId()).thenReturn(19L);
        when(listing.materialName()).thenReturn("깨끗한 데님");
        when(listing.grade()).thenReturn("A");
        when(listing.pricePerKg()).thenReturn(5_000L);
        when(listing.availableQuantity()).thenReturn(availableGram);
        when(listing.sellerName()).thenReturn("김아줌마");
        when(listingQuery.search(any()))
                .thenReturn(new PageResponse<>(List.of(listing), 0, 10, 1, 1, false));
    }

    private String search() {
        AgentToolResult result = tool.execute(
                new AgentToolContext(2L, mock(AgentConversation.class)),
                new ObjectMapper().createObjectNode().put("materialType", "DENIM"));
        return result.payloadJson();
    }
}
