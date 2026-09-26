package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.trade.SearchListingsTool;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.service.ListingQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 판매 글을 찾으면 <b>후보를 보여 주라고</b> 모델에게 적는다 — 하나뿐이어도.
 *
 * <p>폐데님을 파는 곳이 한 곳뿐이자 모델은 비교할 게 없다고 보고 "폐데님 원단 10kg : 20,000원" 만 말했다. 담당자는
 * 누구에게서 어떤 등급을 사는지 모른 채 결제했다.
 */
class SearchListingsHintTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void 하나뿐이어도_판매자와_등급을_보여_주라고_한다() throws Exception {
        JsonNode payload = search(List.of(listing(21L, "김아줌마")));

        assertThat(payload.path("hint").asText())
                .contains("하나뿐입니다")
                .contains("판매자·등급·폐소재/신소재·kg당 단가·남은 양");
        assertThat(payload.path("listings").get(0).path("sellerName").asText()).isEqualTo("김아줌마");
    }

    @Test
    void 여럿이면_나란히_비교해_고르게_한다() throws Exception {
        JsonNode payload = search(List.of(listing(21L, "김아줌마"), listing(25L, "박사장")));

        assertThat(payload.path("hint").asText()).contains("나란히").contains("고른 뒤에 몇 kg");
    }

    /** 후보의 판매자도 업체명으로 보인다. 업체 정보가 없는 판매자는 원래 이름 그대로다. */
    @Test
    void 후보의_판매자는_업체명으로_보인다() throws Exception {
        com.ssafy.upcycle.user.entity.CompanyProfile profile = mock(com.ssafy.upcycle.user.entity.CompanyProfile.class);
        when(profile.getUserId()).thenReturn(7L);
        when(profile.getCompanyName()).thenReturn("enaak");
        com.ssafy.upcycle.user.repository.CompanyProfileRepository profiles =
                mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class);
        List<com.ssafy.upcycle.user.entity.CompanyProfile> found = List.of(profile);
        when(profiles.findAllById(any())).thenReturn(found);
        ListingResponse withCompany = listing(21L, "공병우");
        when(withCompany.sellerId()).thenReturn(7L);
        ListingResponse withoutCompany = listing(25L, "박사장");
        when(withoutCompany.sellerId()).thenReturn(8L);

        JsonNode payload = search(List.of(withCompany, withoutCompany),
                new com.ssafy.upcycle.agent.service.CompanyNames(profiles));

        assertThat(payload.path("listings").get(0).path("sellerName").asText()).isEqualTo("enaak");
        assertThat(payload.path("listings").get(1).path("sellerName").asText()).isEqualTo("박사장");
    }

    private JsonNode search(List<ListingResponse> found) throws Exception {
        return search(found, new com.ssafy.upcycle.agent.service.CompanyNames(
                mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class)));
    }

    private JsonNode search(List<ListingResponse> found, com.ssafy.upcycle.agent.service.CompanyNames names)
            throws Exception {
        ListingQueryService listingQuery = mock(ListingQueryService.class);
        @SuppressWarnings("unchecked")
        PageResponse<ListingResponse> page = mock(PageResponse.class);
        when(page.content()).thenReturn(found);
        when(page.totalElements()).thenReturn((long) found.size());
        when(listingQuery.search(any())).thenReturn(page);
        SearchListingsTool tool = new SearchListingsTool(JSON, listingQuery, names);

        return JSON.readTree(tool.execute(
                new AgentToolContext(2L, mock(AgentConversation.class)),
                JSON.readTree("{\"materialType\":\"DENIM\",\"rootOrigin\":\"WASTE\"}")).payloadJson());
    }

    private static ListingResponse listing(Long id, String seller) {
        ListingResponse listing = mock(ListingResponse.class);
        when(listing.listingId()).thenReturn(id);
        when(listing.materialName()).thenReturn("폐데님 원단");
        when(listing.rootOrigin()).thenReturn(MaterialRootOrigin.WASTE);
        when(listing.grade()).thenReturn("B");
        when(listing.pricePerKg()).thenReturn(2_000L);
        when(listing.availableQuantity()).thenReturn(85_000L);
        when(listing.sellerName()).thenReturn(seller);
        return listing;
    }
}
