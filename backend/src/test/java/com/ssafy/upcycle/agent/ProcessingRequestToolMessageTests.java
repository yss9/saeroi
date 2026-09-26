package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.processing.CreateProcessingRequestTool;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.dto.ProcessingRequestResponse;
import com.ssafy.upcycle.processing.service.ProcessingRequestService;
import com.ssafy.upcycle.processing.service.ProcessorSearchService;
import org.junit.jupiter.api.Test;

/**
 * 가공 요청 도구가 <b>모델에게 돌려주는 문장</b>.
 *
 * <p>도메인을 태우지 않는다. 여기서 확인할 것은 요청이 만들어지는지가 아니라, 만들어진 뒤
 * <b>모델이 무엇을 다음 단계로 이해하는가</b>이기 때문이다. 서비스는 목으로 둔다.
 *
 * <h2>왜 이 테스트가 생겼나</h2>
 *
 * <p>도구가 돌려주던 문장이 <b>"가공 요청을 보냈습니다. 업체가 견적을 내면 알려드리겠습니다"</b>
 * 였다. 이 플랫폼에는 견적을 기다리는 단계가 없다 — 요청하는 순간 업체의 등록 단가로
 * 가공비가 정해지고 작업이 함께 생긴다.
 *
 * <p>모델은 그 문장을 그대로 담당자에게 옮겼다. 담당자는 기다리면 되는 줄 알고 결제를 하지
 * 않았고, 작업은 {@code CREATED} 로 멈춘 채 결제 기한만 흘렀다. <b>모델이 지어낸 말이 아니라
 * 우리가 준 문장이 사실과 반대였다.</b> 시스템 프롬프트에는 "견적을 기다리지 않습니다" 라고
 * 적혀 있었는데도 도구가 돌려준 문장이 이겼다 — 프롬프트의 규칙보다 눈앞의 tool 결과가
 * 강하다는 뜻이다.
 */
class ProcessingRequestToolMessageTests {

    @Test
    void 요청_결과는_견적이_아니라_결제를_가리킨다() {
        ProcessingRequestService requestService = mock(ProcessingRequestService.class);
        ProcessingRequestResponse created = mock(ProcessingRequestResponse.class);
        when(created.jobId()).thenReturn(5L);
        when(created.fee()).thenReturn(2_500L);
        when(requestService.create(any(), any())).thenReturn(created);

        // 체인 설정을 비워 둔다 — 여기서 볼 것은 토큰 가드가 아니라 결과 문장이다.
        CreateProcessingRequestTool tool = new CreateProcessingRequestTool(
                new ObjectMapper(), requestService, mock(MaterialQueryService.class), mock(ProcessorSearchService.class),
                new BlockchainProperties(null, null, null, null, null, null), new com.ssafy.upcycle.agent.service.CompanyNames(org.mockito.Mockito.mock(com.ssafy.upcycle.user.repository.CompanyProfileRepository.class)));

        AgentToolResult result = tool.execute(
                new AgentToolContext(1L, AgentConversation.start(1L, "가공")),
                arguments());

        // 담당자가 읽는 한 줄 — 금액과 다음 할 일이 보여야 한다.
        assertThat(result.userVisibleSummary())
                .contains("작업 5번")
                .contains("2,500원")
                .contains("이어서 결제가 필요합니다");

        // 모델이 읽는 payload — 다음 도구와 인자를 지목한다.
        assertThat(result.payloadJson())
                .contains("pay_processing_fee")
                .contains("견적을 기다리는 단계는 없다");

        // 되풀이하지 않도록 못 박는다.
        assertThat(result.userVisibleSummary()).doesNotContain("견적");
    }

    private com.fasterxml.jackson.databind.JsonNode arguments() {
        try {
            return new ObjectMapper().readTree("""
                    {"materialId":34,"processorId":24,"quantityGram":500,
                     "processTypes":["CUTTING"],"summary":"실크 자투리 절단"}""");
        } catch (Exception malformed) {
            throw new IllegalStateException(malformed);
        }
    }

    /** 단계 판정에 쓰이지 않지만, 문맥이 생산이 아니라 가공임을 분명히 해 둔다. */
    @SuppressWarnings("unused")
    private static final JourneyPhase PHASE = JourneyPhase.PROCESSING;
}
