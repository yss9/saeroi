package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.MyMaterialsTool;
import com.ssafy.upcycle.agent.tool.SearchProcessorsTool;
import com.ssafy.upcycle.agent.tool.production.MyProductionBatchesTool;
import com.ssafy.upcycle.agent.tool.trade.MyTradesTool;
import com.ssafy.upcycle.agent.tool.trade.QuoteTradeTool;
import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.dto.ProcessorSearchResponse;
import com.ssafy.upcycle.processing.service.ProcessorSearchService;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputResponse;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.dto.TradeQuoteResponse;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.service.TradeCommandService;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 읽기 도구의 수량·금액에는 <b>서버가 쓴 문장</b>이 함께 실린다.
 *
 * <p>수량은 gram 정수이고 단가는 kg 당 원이다. 모델에게 환산을 맡기면 틀렸다 — 잔량 94,000g 을
 * "94g" 이라고 말하며 주문을 줄이라고 권했고, 12.5kg × 2,000원/kg 을 25,000,000원이라고 했다.
 * 판매 글 검색만 문장을 주고 있었고, 보유 소재·거래·배치·견적·업체 검색은 숫자만 줬다.
 *
 * <p>DTO 에 필드를 더하지 않고 도구가 옆에 붙인다 — 그 DTO 는 화면과 다른 기능도 쓴다.
 */
class ReadToolQuantityTextTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AgentToolContext context =
            new AgentToolContext(1L, AgentConversation.start(1L, "테스트"));

    @Test
    void 보유_소재에_읽을_수_있는_양이_붙는다() throws Exception {
        MaterialQueryService materialQuery = mock(MaterialQueryService.class);
        List<MyMaterialResponse> held = List.of(new MyMaterialResponse(
                42L, "MAT-42", null, MaterialRootOrigin.WASTE, null, "세척 폴리에스터 원단", "A", null,
                7L, 100_000L, 96_000L, 2_000L, 94_000L, null, List.of()));
        when(materialQuery.findMyMaterials(any())).thenReturn(held);

        JsonNode material = run(new MyMaterialsTool(objectMapper, materialQuery)).path("materials").get(0);

        assertThat(material.path("availableText").asText()).isEqualTo("94kg");
        assertThat(material.path("heldText").asText()).isEqualTo("96kg");
        assertThat(material.path("lockedText").asText()).isEqualTo("2kg");
        // 원래 값은 그대로 남는다. 도구에 넘기는 수량은 gram 이다.
        assertThat(material.path("available").asLong()).isEqualTo(94_000L);
    }

    @Test
    void 구매_거래에_읽을_수_있는_양이_붙는다() throws Exception {
        TradeResponse trade = mock(TradeResponse.class);
        when(trade.tradeId()).thenReturn(16L);
        when(trade.materialName()).thenReturn("폐데님 원단");
        when(trade.quantity()).thenReturn(12_500L);
        when(trade.totalPrice()).thenReturn(25_000L);
        TradeQueryService tradeQuery = mock(TradeQueryService.class);
        PageResponse<TradeResponse> page = new PageResponse<>(List.of(trade), 0, 10, 1, 1, false);
        when(tradeQuery.findMyTrades(any(), any())).thenReturn(page);

        JsonNode row = run(new MyTradesTool(objectMapper, tradeQuery, mock(ChainOrderStatusReader.class)))
                .path("trades").get(0);

        assertThat(row.path("quantityText").asText()).isEqualTo("12.5kg");
    }

    /** 확정 전 배치의 totalUsedQuantity 는 0 이다. 그것을 보고 "투입한 소재가 없다" 고 읽은 적이 있다. */
    @Test
    void 생산_배치에_투입량_문장이_붙는다() throws Exception {
        ProductionInputResponse input = new ProductionInputResponse(
                1L, 42L, "MAT-42", "세척 폴리에스터 원단", 9_000L, 8_500L, 400L, 100L, null, null, null);
        ProductionBatchResponse batch = new ProductionBatchResponse(
                3L, null, "PB-3", "에코백 1차", null, 10, 1L, null, ProductionBatchStatus.DRAFT,
                0L, 0L, 0L, BigDecimal.ZERO, null, null, null, null, List.of(input));
        ProductionBatchService batchService = mock(ProductionBatchService.class);
        when(batchService.findMyBatches(any())).thenReturn(List.of(batch));

        JsonNode payload = run(new MyProductionBatchesTool(objectMapper, batchService, noProjects()));
        JsonNode row = payload.path("batches").get(0);

        assertThat(row.path("inputTotalText").asText()).isEqualTo("9kg");
        assertThat(row.path("inputs").get(0).path("inputText").asText()).isEqualTo("9kg");
        assertThat(row.path("inputs").get(0).path("productUsedText").asText()).isEqualTo("8.5kg");
        assertThat(row.path("inputs").get(0).path("reusableScrapText").asText()).isEqualTo("0.4kg");
        assertThat(payload.path("hint").asText()).contains("totalUsedQuantity");
    }

    @Test
    void 견적에_양과_금액_문장이_붙는다() throws Exception {
        TradeCommandService tradeCommand = mock(TradeCommandService.class);
        when(tradeCommand.quote(anyLong(), anyLong(), eq(MaterialQuantityUnit.G)))
                .thenReturn(new TradeQuoteResponse(5L, 12_500L, 2_000L, 25_000L, 40_000L, true));

        JsonNode payload = run(new QuoteTradeTool(objectMapper, tradeCommand, mock(com.ssafy.upcycle.trade.repository.ListingSearchQueryRepository.class), new com.ssafy.upcycle.agent.service.CompanyNames(org.mockito.Mockito.mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class))),
                "{\"listingId\":5,\"quantityGram\":12500}");

        assertThat(payload.path("quantityText").asText()).isEqualTo("12.5kg");
        assertThat(payload.path("availableText").asText()).isEqualTo("40kg");
        assertThat(payload.path("totalPriceText").asText()).isEqualTo("25,000원");
    }

    /** 견적만 보고 "폐데님 원단 10kg : 20,000원" 이라고만 했다. 누구에게서 어떤 등급을 사는지 함께 준다. */
    @Test
    void 견적에_판매자와_등급을_붙인다() throws Exception {
        TradeCommandService tradeCommand = mock(TradeCommandService.class);
        when(tradeCommand.quote(anyLong(), anyLong(), eq(MaterialQuantityUnit.G)))
                .thenReturn(new TradeQuoteResponse(21L, 10_000L, 2_000L, 20_000L, 85_000L, true));
        com.ssafy.upcycle.trade.repository.ListingSearchQueryRepository listings =
                mock(com.ssafy.upcycle.trade.repository.ListingSearchQueryRepository.class);
        com.ssafy.upcycle.trade.dto.ListingResponse listing = mock(com.ssafy.upcycle.trade.dto.ListingResponse.class);
        when(listing.sellerName()).thenReturn("김아줌마");
        when(listing.grade()).thenReturn("B");
        when(listing.rootOrigin()).thenReturn(com.ssafy.upcycle.material.entity.MaterialRootOrigin.WASTE);
        when(listings.findDetail(21L)).thenReturn(listing);

        JsonNode payload = run(new QuoteTradeTool(objectMapper, tradeCommand, listings, new com.ssafy.upcycle.agent.service.CompanyNames(org.mockito.Mockito.mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class))),
                "{\"listingId\":21,\"quantityGram\":10000}");

        assertThat(payload.path("sellerName").asText()).isEqualTo("김아줌마");
        assertThat(payload.path("grade").asText()).isEqualTo("B");
        assertThat(payload.path("originText").asText()).isEqualTo("폐소재");
        assertThat(payload.path("hint").asText()).contains("판매자");
    }

    @Test
    void 가공업체_검색에_처리량과_금액_문장이_붙는다() throws Exception {
        ProcessorSearchService processorSearch = mock(ProcessorSearchService.class);
        when(processorSearch.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ProcessorSearchResponse(List.of(new ProcessorSearchResponse.Item(
                        9L, "세척 공방", "서울", 50_000L, 1_500L, 18_750L, 3, List.of())), 1, 0, 10));

        JsonNode item = run(new SearchProcessorsTool(objectMapper, processorSearch, new com.ssafy.upcycle.agent.service.CompanyNames(org.mockito.Mockito.mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class))),
                "{\"materialType\":\"DENIM\",\"quantityGram\":12500,\"processTypes\":[\"CLEANING\"]}")
                .path("items").get(0);

        assertThat(item.path("dailyCapacityText").asText()).isEqualTo("50kg/일");
        assertThat(item.path("pricePerKgText").asText()).isEqualTo("kg당 1,500원");
        assertThat(item.path("estimatedPriceText").asText()).isEqualTo("18,750원");
    }

    /** 프로젝트 판정은 여기서 보지 않는다 — 없는 것으로 둔다. */
    @SuppressWarnings("unchecked")
    private static org.springframework.beans.factory.ObjectProvider<com.ssafy.upcycle.agent.project.AgentProjects> noProjects() {
        return mock(org.springframework.beans.factory.ObjectProvider.class);
    }

    private JsonNode run(com.ssafy.upcycle.agent.tool.AgentTool tool) throws Exception {
        return run(tool, "{}");
    }

    private JsonNode run(com.ssafy.upcycle.agent.tool.AgentTool tool, String arguments) throws Exception {
        return objectMapper.readTree(tool.execute(context, objectMapper.readTree(arguments)).payloadJson());
    }
}
