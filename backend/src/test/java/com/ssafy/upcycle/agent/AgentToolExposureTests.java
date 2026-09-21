package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.service.AgentToolRegistry;
import com.ssafy.upcycle.agent.service.llm.ToolSpec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 단계마다 어떤 tool 이 보이는지.
 *
 * <p><b>안 보이면 모델은 지어낸다.</b> 실제로 그랬다. 담당자가 "가공 진행하고싶어" 라고 했는데
 * 단계가 소재 확보에 머물러 {@code check_processing_plan} 을 부를 수 없었고, 필수 공정을 묻는
 * 질문에 "필수 가공은 없습니다" 라고 사실과 반대로 답했다. 그 규칙을 어기면 생산 확정에서
 * 막히는데, 그때는 이미 가공비를 낸 뒤다.
 */
@SpringBootTest
class AgentToolExposureTests {

    @Autowired private AgentToolRegistry registry;

    /**
     * 필수 공정 안내는 <b>소재를 받은 직후부터</b> 보여야 한다.
     *
     * <p>"이거 가공해야 해?" 는 소재를 받고 바로 나오는 물음이다. 가공 단계로 옮긴 뒤에만
     * 답할 수 있게 두면, 옮기지 않은 모델이 기억으로 답한다.
     */
    @Test
    void 필수_공정_안내는_소재_단계에서도_보인다() {
        assertThat(names(JourneyPhase.MATERIAL_SOURCING)).contains("check_processing_plan");
        assertThat(names(JourneyPhase.PROCESSING)).contains("check_processing_plan");
        assertThat(names(JourneyPhase.PRODUCTION)).contains("check_processing_plan");
    }

    /** 수령은 카드 하나다. 서명 도구를 따로 두면 모델이 순서를 틀린다. */
    @Test
    void 수령_서명_도구가_따로_있지_않다() {
        assertThat(names(JourneyPhase.MATERIAL_SOURCING))
                .contains("confirm_receipt")
                .doesNotContain("request_chain_receipt");
    }

    /**
     * 단계마다 열둘을 넘지 않는다.
     *
     * <p>{@code JourneyPhase} 가 적어 둔 이유가 있다 — 한꺼번에 넘기면 모델이 엉뚱한 단계의
     * tool 을 부르기 시작한다. 소재도 안 샀는데 생산 배치를 열려 하는 식이다.
     */
    @ParameterizedTest
    @EnumSource(JourneyPhase.class)
    void 단계마다_노출이_과하지_않다(JourneyPhase phase) {
        assertThat(registry.specsFor(phase)).hasSizeLessThanOrEqualTo(12);
    }

    private List<String> names(JourneyPhase phase) {
        return registry.specsFor(phase).stream().map(ToolSpec::name).toList();
    }
}
