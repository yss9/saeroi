package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.processing.CreateProcessingRequestTool;
import com.ssafy.upcycle.agent.tool.production.OpenProductionBatchTool;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.dto.ProcessorSearchResponse;
import com.ssafy.upcycle.processing.service.ProcessingRequestService;
import com.ssafy.upcycle.processing.service.ProcessorSearchService;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 카드 제목은 <b>담당자가 동의하는 내용</b>을 서버가 쓴다.
 *
 * <p>모델이 쓴 요약에는 가공 업체와 가공비가 없었고("울 원단 5kg 세척·분해·선별 가공"), 생산 배치 카드에는 실제로 열릴
 * 배치 이름이 없었다. 수동 화면은 둘 다 담당자가 보고 정한다.
 */
class ToolCardSummaryTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MaterialQueryService materialQuery;
    private ProcessorSearchService processorSearch;
    private CreateProcessingRequestTool processing;

    @BeforeEach
    void setUp() {
        materialQuery = mock(MaterialQueryService.class);
        processorSearch = mock(ProcessorSearchService.class);
        MyMaterialResponse wool = mock(MyMaterialResponse.class);
        when(wool.materialId()).thenReturn(38L);
        when(wool.name()).thenReturn("울 원단");
        when(wool.materialType()).thenReturn(MaterialType.WOOL);
        when(materialQuery.findMyMaterials(any())).thenReturn(List.of(wool));
        processing = new CreateProcessingRequestTool(
                JSON, mock(ProcessingRequestService.class), materialQuery, processorSearch,
                new BlockchainProperties(null, null, null, null, null, null), new com.ssafy.upcycle.agent.service.CompanyNames(org.mockito.Mockito.mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class)));
    }

    @Test
    void 가공_요청_카드에_업체와_예상_금액과_소요일이_보인다() {
        ProcessorSearchResponse found = mock(ProcessorSearchResponse.class);
        when(found.items()).thenReturn(List.of(
                new ProcessorSearchResponse.Item(18L, "홍길동", "경기", 2_000, 3_000, 15_000L, 9, List.of()),
                new ProcessorSearchResponse.Item(24L, "pro", "부산", 100_000, 15_000, 75_000L, 3, List.of())));
        when(processorSearch.search(any(), eq("WOOL"), eq("5000"), anyList(), anyString(), anyString()))
                .thenReturn(found);

        assertThat(processing.serverSummary(context(), args(24)))
                .contains("울 원단 5kg 가공 — 세척·해체·선별 · pro · 예상 75,000원 · 3일");
    }

    @Test
    void 업체를_찾지_못하면_금액을_짐작하지_않는다() {
        ProcessorSearchResponse found = mock(ProcessorSearchResponse.class);
        when(found.items()).thenReturn(List.of());
        when(processorSearch.search(any(), any(), any(), anyList(), anyString(), anyString())).thenReturn(found);

        assertThat(processing.serverSummary(context(), args(24))).contains("울 원단 5kg 가공 — 세척·해체·선별");
    }

    /** 검색이 거절할 인자로는 부르지 않는다 — 카드 트랜잭션 안에서 예외가 나면 턴이 통째로 실패한다. */
    @Test
    void 공정이_잘못되면_검색을_부르지_않는다() {
        JsonNode broken = node("{\"materialId\":38,\"processorId\":24,\"quantityGram\":5000,"
                + "\"processTypes\":[\"CLEANING\",\"POLISHING\"],\"summary\":\"가공\"}");

        assertThat(processing.serverSummary(context(), broken)).isEmpty();
        verify(processorSearch, never()).search(any(), any(), any(), anyList(), anyString(), anyString());
    }

    @Test
    void 생산_배치_카드에_열릴_이름과_개수가_보인다() {
        OpenProductionBatchTool batch = new OpenProductionBatchTool(
                JSON, mock(ProductionBatchService.class), mock(UserWalletRepository.class));

        assertThat(batch.serverSummary(context(), node(
                        "{\"batchName\":\"울·데님 지갑 10개\",\"producedQuantity\":10,\"summary\":\"지갑 생산\"}")))
                .contains("생산 배치 열기 — \"울·데님 지갑 10개\" · 10개 생산");
    }

    private static JsonNode args(long processorId) {
        return node("{\"materialId\":38,\"processorId\":%d,\"quantityGram\":5000,"
                .formatted(processorId)
                + "\"processTypes\":[\"CLEANING\",\"DISASSEMBLY\",\"SORTING\"],\"summary\":\"가공\"}");
    }

    private static JsonNode node(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static AgentToolContext context() {
        return new AgentToolContext(2L, mock(AgentConversation.class));
    }
}
