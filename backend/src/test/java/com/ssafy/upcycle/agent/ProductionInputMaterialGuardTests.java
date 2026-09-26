package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.production.AddProductionInputsTool;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 투입할 소재가 <b>실제로 있는 것인지</b> 카드 이전에 본다.
 *
 * <h2>왜 필요한가 — 가공을 거치면 소재 번호가 바뀐다</h2>
 *
 * <p>가공 결과는 새 소재로 발행된다. 폴리에스터 원단을 세척에 보내면 돌아오는 것은
 * "세척 폴리에스터 원단" 이라는 다른 번호의 소재이고, 원래 소재는 줄어든다. 그런데 모델은
 * 대화 앞부분에서 본 <b>가공 전 번호</b>를 그대로 쓰는 일이 있다.
 *
 * <p>실제로 그렇게 막혔다. 보유는 40·41·42 인데 모델이 34·35·36 을 넘겼고, 승인 뒤 도메인이
 * "사용 가능한 수량이 부족합니다" 로 거절했다. 그 문장에는 어느 소재가 문제인지도 무엇을
 * 써야 하는지도 없어서, 모델은 수량만 바꿔 같은 번호로 다시 시도했다 — 담당자는 같은 오류를
 * 두 번 봤다.
 */
class ProductionInputMaterialGuardTests {

    private AddProductionInputsTool tool;
    private MaterialQueryService materialQuery;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        materialQuery = mock(MaterialQueryService.class);
        tool = new AddProductionInputsTool(
                objectMapper, mock(ProductionBatchService.class), materialQuery);
    }

    /** 보유하지 않은 번호면 카드를 만들지 않고, <b>진짜 번호를 알려준다.</b> */
    @Test
    void 보유하지_않은_소재_번호는_카드_이전에_막고_실제_보유를_알려준다() {
        givenHeld(material(42L, "세척 폴리에스터 원단", 2000));

        Optional<AgentToolResult> refusal = tool.refuseBeforeProposal(
                context(), args(35L, 2000, 0, 100));

        assertThat(refusal).isPresent();
        String payload = refusal.get().payloadJson();
        assertThat(payload).contains("MATERIAL_NOT_AVAILABLE");
        assertThat(payload).contains("보유하지 않은 소재입니다");
        // 고칠 수 있는 형태로 알려주지 않으면 모델이 할 수 있는 일은 지어내는 것뿐이다.
        assertThat(payload).contains("42").contains("세척 폴리에스터 원단");
    }

    /** 번호는 맞는데 양이 모자라면, 얼마가 남았는지 숫자로 말한다. */
    @Test
    void 수량이_모자라면_남은_양을_알려준다() {
        givenHeld(material(42L, "세척 폴리에스터 원단", 1500));

        Optional<AgentToolResult> refusal = tool.refuseBeforeProposal(
                context(), args(42L, 2000, 0, 100));

        assertThat(refusal).isPresent();
        assertThat(refusal.get().payloadJson())
                .contains("사용 가능한 수량이 모자랍니다")
                .contains("1500");
    }

    /** 보유한 소재를 가용량 안에서 부르면 그대로 통과한다. */
    @Test
    void 보유한_소재는_막지_않는다() {
        givenHeld(material(42L, "세척 폴리에스터 원단", 2000));

        assertThat(tool.refuseBeforeProposal(context(), args(42L, 2000, 0, 100)))
                .isEmpty();
    }

    /**
     * 조회가 흔들려도 정상 흐름을 세우지 않는다.
     *
     * <p>사전 점검은 도움일 뿐이고, 못 본 것은 승인 뒤 도메인이 거절한다. 조회 실패로 투입
     * 자체를 막으면 담당자는 아무것도 할 수 없게 된다.
     */
    @Test
    void 보유_조회가_실패하면_막지_않는다() {
        when(materialQuery.findMyMaterials(any())).thenThrow(new IllegalStateException("조회 실패"));

        assertThat(tool.refuseBeforeProposal(context(), args(35L, 2000, 0, 100)))
                .isEmpty();
    }

    /**
     * 손실을 투입에 <b>더해</b> 버린 경우를 잡는다.
     *
     * <p>담당자가 "전부 쓰고 500g 손실" 이라고 하자 모델이 투입을 6,000 → 6,500 으로 올렸다.
     * 손실은 투입에서 빠지는 양이지 더하는 양이 아니다. 자투리+폐기가 투입을 넘으면 그런
     * 경우다.
     */
    @Test
    void 자투리와_폐기가_투입을_넘으면_막는다() {
        givenHeld(material(42L, "세척 폴리에스터 원단", 3000));

        Optional<AgentToolResult> refusal = tool.refuseBeforeProposal(
                context(), args(42L, 100, 0, 200));

        assertThat(refusal).isPresent();
        assertThat(refusal.get().payloadJson())
                .contains("LEFTOVER_EXCEEDS_INPUT")
                .contains("더하는 양이 아닙니다");
    }

    /**
     * 필수 공정이 남은 폐소재는 카드 이전에 막는다.
     *
     * <p>생산 확정이 폐소재의 세척·해체·선별을 본다. 여기서 넘기면 배치를 열고 소재를 묶은 뒤 확정에서야
     * 막힌다 — 폐울과 폐데님을 산 직후 모델이 가공 없이 생산 카드부터 띄운 일이 있었다.
     */
    @Test
    void 필수_공정이_남은_폐소재는_막고_남은_공정을_알려준다() {
        MyMaterialResponse wool = material(38L, "울 원단", 5000);
        when(wool.rootOrigin()).thenReturn(MaterialRootOrigin.WASTE);
        when(wool.completedProcessTypes()).thenReturn(List.of(ProcessType.CLEANING));
        givenHeld(wool);

        Optional<AgentToolResult> refusal = tool.refuseBeforeProposal(context(), args(38L, 5000, 0, 0));

        assertThat(refusal).isPresent();
        assertThat(refusal.get().payloadJson())
                .contains("MATERIAL_NOT_PROCESSED")
                .contains("울 원단")
                .contains("DISASSEMBLY").contains("SORTING")
                .doesNotContain("\"CLEANING\"")
                .contains("create_processing_request");
    }

    @Test
    void 필수_공정을_마친_폐소재와_신소재는_막지_않는다() {
        MyMaterialResponse refined = material(40L, "세척 해체 선별 울", 5000);
        when(refined.rootOrigin()).thenReturn(MaterialRootOrigin.WASTE);
        when(refined.completedProcessTypes())
                .thenReturn(List.of(ProcessType.CLEANING, ProcessType.DISASSEMBLY, ProcessType.SORTING));
        MyMaterialResponse virgin = material(41L, "깨끗한 데님", 5000);
        when(virgin.rootOrigin()).thenReturn(MaterialRootOrigin.VIRGIN);
        givenHeld(refined, virgin);

        assertThat(tool.refuseBeforeProposal(context(), args(40L, 5000, 0, 0))).isEmpty();
        assertThat(tool.refuseBeforeProposal(context(), args(41L, 5000, 0, 0))).isEmpty();
    }

    private void givenHeld(MyMaterialResponse... materials) {
        when(materialQuery.findMyMaterials(any())).thenReturn(List.of(materials));
    }

    private MyMaterialResponse material(Long id, String name, long available) {
        MyMaterialResponse held = mock(MyMaterialResponse.class);
        when(held.materialId()).thenReturn(id);
        when(held.name()).thenReturn(name);
        when(held.available()).thenReturn(available);
        return held;
    }

    private AgentToolContext context() {
        return new AgentToolContext(2L, mock(AgentConversation.class));
    }

    /** 제품이 된 양은 넘기지 않는다 — 서버가 투입에서 자투리와 폐기를 빼서 정한다. */
    private JsonNode args(long materialId, long input, long scrap, long waste) {
        try {
            return objectMapper.readTree("""
                    {"batchId":7,"summary":"투입","inputs":[
                      {"materialId":%d,"inputQuantity":%d,
                       "reusableScrapQuantity":%d,"wasteQuantity":%d}]}"""
                    .formatted(materialId, input, scrap, waste));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
