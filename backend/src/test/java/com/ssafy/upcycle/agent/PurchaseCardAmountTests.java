package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.trade.PurchaseMaterialTool;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.repository.ListingSearchQueryRepository;
import com.ssafy.upcycle.trade.service.PaymentService;
import com.ssafy.upcycle.trade.service.TradeCommandService;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 담당자가 승인 전에 보는 금액은 <b>서버가 계산한 것</b>이어야 한다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>카드의 payload 는 모델이 만든 인자 그대로였고, 거기에는 금액이 없었다. 그래서 카드의
 * "결제 금액" 줄이 그려지지 않았고, 담당자가 보는 금액은 <b>모델이 쓴 요약 한 줄</b>뿐이었다.
 *
 * <p>실제로 12.5kg × 2,000원/kg 을 <b>25,000,000원</b>이라고 적었다 — gram 수량(12,500)에 kg
 * 단가를 곱한 것이다. 서버는 금액을 모델에게서 받지 않으므로 실제 결제는 25,000원이었겠지만,
 * 담당자는 2,500만원짜리 카드를 보고 승인 여부를 정하게 된다.
 */
class PurchaseCardAmountTests {

    private final ListingSearchQueryRepository listingQuery = mock(ListingSearchQueryRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.ssafy.upcycle.user.repository.CompanyProfileRepository profiles =
            mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class);
    private final PurchaseMaterialTool tool = new PurchaseMaterialTool(
            objectMapper,
            mock(TradeCommandService.class),
            mock(PaymentService.class),
            listingQuery,
            mock(com.ssafy.upcycle.agent.service.ConversationListings.class),
            new com.ssafy.upcycle.agent.service.CompanyNames(profiles));

    @Test
    void 카드에_서버가_계산한_총액을_싣는다() {
        givenListing(2_000, "폐데님 원단");

        Map<String, Object> extras = tool.payloadExtras(context(), args(12_500));

        // 12.5kg × 2,000원/kg = 25,000원. 모델이 25,000,000원이라고 적은 자리다.
        assertThat(extras).containsEntry("totalPrice", 25_000L);
        assertThat(extras).containsEntry("pricePerKg", 2_000L);
        assertThat(extras).containsEntry("materialName", "폐데님 원단");
    }

    /** 판매 글이 한 곳뿐이자 모델이 판매자와 등급을 말하지 않았다. 카드에는 늘 있어야 담당자가 확인할 수 있다. */
    @Test
    void 카드에_판매자와_등급과_출처를_싣는다() {
        givenListing(2_000, "폐데님 원단");
        ListingResponse listing = listingQuery.findDetail(21L);
        when(listing.sellerName()).thenReturn("김아줌마");
        when(listing.grade()).thenReturn("B");
        when(listing.rootOrigin()).thenReturn(com.ssafy.upcycle.material.entity.MaterialRootOrigin.WASTE);

        assertThat(tool.payloadExtras(context(), args(10_000)))
                .containsEntry("sellerName", "김아줌마")
                .containsEntry("grade", "B")
                .containsEntry("originText", "폐소재");
    }

    /**
     * 판매자는 <b>업체명</b>으로 보인다. 판매 글이 주는 이름은 가입한 대표자 개인 이름이라, 카드에
     * "판매자 공병우" 처럼 사람 이름이 떴다. 업체 정보가 없으면 원래 이름을 쓴다.
     */
    @Test
    void 카드의_판매자는_대표자_이름이_아니라_업체명이다() {
        givenListing(2_000, "폐데님 원단");
        ListingResponse listing = listingQuery.findDetail(21L);
        when(listing.sellerId()).thenReturn(1L);
        when(listing.sellerName()).thenReturn("공병우");
        com.ssafy.upcycle.user.entity.CompanyProfile profile = mock(com.ssafy.upcycle.user.entity.CompanyProfile.class);
        when(profile.getCompanyName()).thenReturn("enaak");
        when(profiles.findById(1L)).thenReturn(java.util.Optional.of(profile));

        assertThat(tool.payloadExtras(context(), args(10_000))).containsEntry("sellerName", "enaak");
    }

    /** 1kg 미만도 정확히 낸다. */
    @Test
    void 킬로그램_미만_수량도_금액을_낸다() {
        givenListing(30_000, "실크 원단");

        assertThat(tool.payloadExtras(context(), args(500)))
                .containsEntry("totalPrice", 15_000L);
    }

    /**
     * 딱 떨어지지 않으면 <b>버린다.</b>
     *
     * <p>계산은 {@code TradePricing} 에만 있고 거기서 버림을 쓴다 — 프론트의
     * {@code Math.floor} 와 맞춘 것이다. 카드가 따로 반올림하면 보인 금액과 실제 청구가
     * 1원 어긋나고, 그 차이는 결제 승인 검증에서 실패로 나타난다.
     */
    @Test
    void 딱_떨어지지_않는_금액은_버린다() {
        givenListing(3_333, "혼방 원단");

        // 1,500g × 3,333원/kg = 4,999.5원 → 버림 4,999원 (반올림이면 5,000원)
        assertThat(tool.payloadExtras(context(), args(1_500)))
                .containsEntry("totalPrice", 4_999L);
    }

    /**
     * 판매 글을 못 읽으면 아무것도 싣지 않는다.
     *
     * <p>부가 정보가 없다고 카드를 막지 않는다 — 금액은 승인 뒤 서버가 다시 계산하므로 실제
     * 결제는 어차피 정확하다.
     */
    @Test
    void 판매_글을_못_읽으면_비워_둔다() {
        when(listingQuery.findDetail(anyLong())).thenReturn(null);

        assertThat(tool.payloadExtras(context(), args(12_500))).isEmpty();
    }

    private void givenListing(long pricePerKg, String name) {
        ListingResponse listing = mock(ListingResponse.class);
        when(listing.pricePerKg()).thenReturn(pricePerKg);
        when(listing.materialName()).thenReturn(name);
        when(listingQuery.findDetail(anyLong())).thenReturn(listing);
    }

    private AgentToolContext context() {
        return new AgentToolContext(2L, mock(AgentConversation.class));
    }

    private JsonNode args(long quantityGram) {
        return objectMapper.createObjectNode()
                .put("listingId", 21)
                .put("quantityGram", quantityGram);
    }
}
