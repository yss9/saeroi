package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.production.AddProductionInputsTool;
import com.ssafy.upcycle.agent.tool.production.InputBreakdown;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputRequest;
import com.ssafy.upcycle.production.dto.ProductionInputsRequest;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 투입 내역은 <b>담당자가 말한 숫자 그대로</b> 받는다.
 *
 * <p>담당자는 "5kg 넣었고 0.5kg 남고 0.5kg 버렸어" 처럼 투입량으로 말하기도 하고, "4kg 썼고 …" 처럼 제품에 들어간
 * 양으로 말하기도 한다. 어느 쪽이든 모델이 계산하지 않게 한다 — 예전에 모델이 손실을 투입에 더해 버렸다. 빠진
 * 자투리·폐기는 0 으로 채우지 않는다 — 묻지도 않은 "제품 5kg + 자투리 0 + 폐기 0" 이 카드에 올라간 일이 있었다.
 */
class ProductionInputBreakdownTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ProductionBatchService batchService;
    private MaterialQueryService materialQuery;
    private AddProductionInputsTool tool;

    @BeforeEach
    void setUp() {
        batchService = mock(ProductionBatchService.class);
        materialQuery = mock(MaterialQueryService.class);
        MyMaterialResponse wool = mock(MyMaterialResponse.class);
        when(wool.materialId()).thenReturn(44L);
        when(wool.name()).thenReturn("가공 울 원단");
        when(wool.available()).thenReturn(5_000L);
        when(materialQuery.findMyMaterials(any())).thenReturn(List.of(wool));
        tool = new AddProductionInputsTool(JSON, batchService, materialQuery);
    }

    @Test
    void 투입량을_들었으면_제품_사용은_서버가_뺀다() {
        InputBreakdown line = read("{\"materialId\":44,\"inputQuantity\":5000,\"reusableScrapQuantity\":500,\"wasteQuantity\":500}");

        assertThat(line.inputQuantity()).isEqualTo(5_000);
        assertThat(line.productQuantity()).isEqualTo(4_000);
    }

    @Test
    void 제품_사용을_들었으면_투입량은_서버가_더한다() {
        InputBreakdown line = read("{\"materialId\":44,\"productQuantity\":4000,\"reusableScrapQuantity\":500,\"wasteQuantity\":500}");

        assertThat(line.inputQuantity()).isEqualTo(5_000);
        assertThat(line.productQuantity()).isEqualTo(4_000);
    }

    @Test
    void 둘_다_들었고_맞으면_그대로다() {
        InputBreakdown line = read(
                "{\"materialId\":44,\"inputQuantity\":5000,\"productQuantity\":4000,\"reusableScrapQuantity\":500,\"wasteQuantity\":500}");

        assertThat(line.productQuantity()).isEqualTo(4_000);
    }

    @Test
    void 둘_다_들었는데_합이_다르면_맞추지_않고_다시_묻게_한다() {
        Optional<AgentToolResult> refusal = tool.refuseBeforeProposal(context(), args(
                "{\"materialId\":44,\"inputQuantity\":5000,\"productQuantity\":4000,\"reusableScrapQuantity\":500,\"wasteQuantity\":0}"));

        assertThat(refusal).hasValueSatisfying(result -> assertThat(result.payloadJson())
                .contains(InputBreakdown.MISMATCH)
                .contains("투입 5kg 인데 제품 4kg + 자투리 0.5kg + 폐기 0kg = 4.5kg")
                .contains("계산해서 맞추지 말고"));
    }

    @Test
    void 자투리나_폐기가_빠지면_0으로_채우지_않고_묻게_한다() {
        Optional<AgentToolResult> refusal = tool.refuseBeforeProposal(context(), args(
                "{\"materialId\":44,\"inputQuantity\":5000,\"reusableScrapQuantity\":0}"));

        assertThat(refusal).hasValueSatisfying(result -> assertThat(result.payloadJson())
                .contains(InputBreakdown.NOT_ASKED)
                .contains("자투리 — 생산을 확정하면 새 소재로 돌아옵니다")
                .contains("폐기 — 사라집니다"));
    }

    @Test
    void 투입량도_제품_사용도_없으면_묻게_한다() {
        assertThat(tool.refuseBeforeProposal(context(), args(
                        "{\"materialId\":44,\"reusableScrapQuantity\":0,\"wasteQuantity\":0}")))
                .hasValueSatisfying(result -> assertThat(result.payloadJson()).contains(InputBreakdown.NOT_ASKED));
    }

    @Test
    void 자투리와_폐기가_투입보다_많으면_막는다() {
        assertThat(tool.refuseBeforeProposal(context(), args(
                        "{\"materialId\":44,\"inputQuantity\":1000,\"reusableScrapQuantity\":800,\"wasteQuantity\":500}")))
                .hasValueSatisfying(result -> assertThat(result.payloadJson())
                        .contains(InputBreakdown.LEFTOVER_EXCEEDS_INPUT)
                        .contains("더하는 양이 아닙니다"));
    }

    @Test
    void 제품_사용으로_들은_양도_가용량과_비교한다() {
        // 제품 4kg + 자투리 1kg + 폐기 1kg = 투입 6kg 인데 가진 것은 5kg 이다.
        assertThat(tool.refuseBeforeProposal(context(), args(
                        "{\"materialId\":44,\"productQuantity\":4000,\"reusableScrapQuantity\":1000,\"wasteQuantity\":1000}")))
                .hasValueSatisfying(result -> assertThat(result.payloadJson())
                        .contains("MATERIAL_NOT_AVAILABLE").contains("\"requested\":6000"));
    }

    @Test
    void 인자가_비어도_검사는_터지지_않는다() {
        // 검사가 터지면 실행기는 막을 이유가 없다고 보고 카드를 띄운다.
        assertThat(tool.refuseBeforeProposal(context(), JSON.createObjectNode()))
                .hasValueSatisfying(result -> assertThat(result.payloadJson()).contains("INPUTS_REQUIRED"));
    }

    @Test
    void 카드에는_서버가_푼_네_숫자와_이름이_실린다() {
        Map<String, Object> extras = tool.payloadExtras(context(), args(
                "{\"materialId\":44,\"productQuantity\":4000,\"reusableScrapQuantity\":500,\"wasteQuantity\":500,\"wasteReason\":\"오염\"}"));

        assertThat(JSON.valueToTree(extras).toString())
                .contains("\"materialName\":\"가공 울 원단\"")
                .contains("\"inputQuantity\":5000")
                .contains("\"productQuantity\":4000")
                .contains("\"reusableScrapQuantity\":500")
                .contains("\"wasteQuantity\":500")
                .contains("\"wasteReason\":\"오염\"");
    }

    @Test
    void 카드_제목에_이름과_투입량과_자투리_폐기가_보인다() {
        assertThat(tool.serverSummary(context(), args(
                        "{\"materialId\":44,\"inputQuantity\":5000,\"reusableScrapQuantity\":500,\"wasteQuantity\":500}")))
                .contains("소재 투입 — 가공 울 원단 5kg (자투리 0.5kg · 폐기 0.5kg)");
    }

    @Test
    void 승인하면_담당자가_본_숫자_그대로_투입한다() {
        ProductionBatchResponse updated = mock(ProductionBatchResponse.class);
        when(batchService.addInputs(any(), eq(8L), any())).thenReturn(updated);

        tool.execute(context(), args(
                "{\"materialId\":44,\"productQuantity\":4000,\"reusableScrapQuantity\":500,\"wasteQuantity\":500,\"wasteReason\":\"오염\"}"));

        ArgumentCaptor<ProductionInputsRequest> request = ArgumentCaptor.forClass(ProductionInputsRequest.class);
        verify(batchService).addInputs(any(), eq(8L), request.capture());
        ProductionInputRequest input = request.getValue().inputs().getFirst();
        assertThat(input.inputQuantity()).isEqualTo(5_000L);
        assertThat(input.productUsedQuantity()).isEqualTo(4_000L);
        assertThat(input.reusableScrapQuantity()).isEqualTo(500L);
        assertThat(input.wasteQuantity()).isEqualTo(500L);
        assertThat(input.wasteReason()).isEqualTo("오염");
    }

    @Test
    void 폐기가_없으면_사유는_보내지_않는다() {
        InputBreakdown line = read(
                "{\"materialId\":44,\"inputQuantity\":5000,\"reusableScrapQuantity\":0,\"wasteQuantity\":0,\"wasteReason\":\"없음\"}");

        assertThat(line.wasteReason()).isNull();
    }

    @Test
    void 실행_때도_빠진_값은_0으로_채우지_않는다() {
        assertThatThrownBy(() -> tool.execute(context(), args("{\"materialId\":44,\"inputQuantity\":5000}")))
                .isInstanceOf(AgentToolArgumentException.class);
    }

    private static InputBreakdown read(String input) {
        InputBreakdown.Read read = InputBreakdown.read(node(input));
        assertThat(read.ok()).as(read.problem()).isTrue();
        return read.line();
    }

    private static JsonNode args(String input) {
        return node("{\"batchId\":8,\"summary\":\"투입\",\"inputs\":[" + input + "]}");
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
