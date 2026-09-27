package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.processing.CreateProcessingRequestTool;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialStatus;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.service.ProcessingRequestService;
import com.ssafy.upcycle.processing.service.ProcessorSearchService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 온체인에 발행되지 않은 소재는 <b>가공을 맡기기 전에</b> 막는다.
 *
 * <h2>왜 여기서 막아야 하나</h2>
 *
 * <p>체인에 가공 작업을 올리려면 소재가 토큰이어야 한다
 * ({@code ProcessingChainService.prepare}). 그 검사는 <b>소재를 인계하는 순간</b>에야 돈다.
 * 그 앞에 요청·결제·업체 수락이 전부 있다.
 *
 * <p>실제로 그렇게 새어 나갔다. 생산 자투리 500g 으로 가공을 요청하고, 가공비 2,500원을
 * 결제하고, 업체가 수락까지 한 뒤, 인계 서명에서 <b>"현재 상태에서는 가공 요청을 변경할 수
 * 없습니다"</b> 로 막혔다. 원인을 말해 주지 않는 문구라 담당자는 무엇이 잘못됐는지 알 수
 * 없었고, 낸 돈을 되돌릴 방법도 없었다.
 *
 * <p>막을 기회가 세 번 있었는데 아무 데서도 토큰을 보지 않았다. 카드를 띄우기 <b>전</b>이
 * 그 자리다 — 승인 뒤에 거절하면 담당자는 이미 누른 다음이다.
 */
class ProcessingMaterialTokenGuardTests {

    private static final long MATERIAL_ID = 34L;

    @Test
    void 토큰이_없는_소재는_카드를_띄우기_전에_막는다() {
        CreateProcessingRequestTool tool = toolWith(material(null));

        Optional<AgentToolResult> refusal = tool.refuseBeforeProposal(context(), arguments());

        assertThat(refusal).isPresent();
        assertThat(refusal.get().payloadJson())
                .contains("MATERIAL_NOT_ON_CHAIN")
                .contains("블록체인에 발행되지 않아")
                // 다시 시도하면 같은 실패를 반복한다. 모델에게 그러지 말라고 못 박는다.
                .contains("요청을 다시 시도하지 마세요");
    }

    @Test
    void 토큰이_있는_소재는_통과한다() {
        CreateProcessingRequestTool tool = toolWith(material(77L));

        assertThat(tool.refuseBeforeProposal(context(), arguments())).isEmpty();
    }

    /**
     * 체인을 쓰지 않는 환경은 보지 않는다.
     *
     * <p>거기서는 토큰이 애초에 없어서, 막으면 가공이 통째로 불가능해진다. 로컬·테스트에서
     * 흐름 전체가 죽는 것이 이 가드가 막으려는 문제보다 크다.
     */
    @Test
    void 체인을_쓰지_않는_환경은_막지_않는다() {
        CreateProcessingRequestTool tool = toolWith(material(null), unconfigured());

        assertThat(tool.refuseBeforeProposal(context(), arguments())).isEmpty();
    }

    private CreateProcessingRequestTool toolWith(MyMaterialResponse material) {
        return toolWith(material, configured());
    }

    private CreateProcessingRequestTool toolWith(
            MyMaterialResponse material, BlockchainProperties blockchain) {
        MaterialQueryService materialQuery = mock(MaterialQueryService.class);
        when(materialQuery.findMyMaterials(1L)).thenReturn(List.of(material));
        return new CreateProcessingRequestTool(
                new ObjectMapper(), mock(ProcessingRequestService.class), materialQuery,
                mock(ProcessorSearchService.class), blockchain, new com.ssafy.upcycle.agent.service.CompanyNames(org.mockito.Mockito.mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class)));
    }

    private MyMaterialResponse material(Long tokenId) {
        return new MyMaterialResponse(
                MATERIAL_ID, "MAT-000034", MaterialType.SILK, MaterialRootOrigin.VIRGIN,
                MaterialGenerationType.PRODUCTION_SCRAP, "실크 원단 자투리", "A",
                MaterialStatus.ACTIVE, tokenId, 500L, 500L, 0L, 500L,
                LocalDateTime.now(), List.of());
    }

    private BlockchainProperties configured() {
        return new BlockchainProperties(
                31221L, "https://rpc.test",
                "0xBE98f5A9aA8d0480B09C689e38806aCcD41a0f66",
                "0x53Fc91Be37720Fcb051004B7aab73639CA266853",
                "0x09C809D42eae5e7281da7b03e0a16bd5528f2b47",
                null);
    }

    private BlockchainProperties unconfigured() {
        return new BlockchainProperties(null, null, null, null, null, null);
    }

    private AgentToolContext context() {
        return new AgentToolContext(1L, AgentConversation.start(1L, "가공"));
    }

    private JsonNode arguments() {
        try {
            return new ObjectMapper().readTree("""
                    {"materialId":34,"processorId":24,"quantityGram":500,
                     "processTypes":["CUTTING"],"summary":"실크 자투리 절단"}""");
        } catch (Exception malformed) {
            throw new IllegalStateException(malformed);
        }
    }
}
