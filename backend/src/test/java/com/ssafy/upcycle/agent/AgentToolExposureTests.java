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
     * 단계마다 열셋을 넘지 않는다.
     *
     * <p>{@code JourneyPhase} 가 적어 둔 이유가 있다 — 한꺼번에 넘기면 모델이 엉뚱한 단계의
     * tool 을 부르기 시작한다. 소재도 안 샀는데 생산 배치를 열려 하는 식이다.
     *
     * <p>열둘이었다가 생산 단계에 {@code cancel_production_batch} 를 넣으며 하나 올렸다.
     * 배치 취소는 <b>묶인 소재를 풀는 유일한 길</b>이고, 문제가 생기는 단계에서 닿을 수
     * 있어야 한다 — 다른 단계로 옮겨야만 정리할 수 있게 두면 담당자는 그 방법을 못 찾는다.
     * 수를 줄이려고 그것을 빼는 것은 안전장치를 빼는 것이다.
     */
    @ParameterizedTest
    @EnumSource(JourneyPhase.class)
    void 단계마다_노출이_과하지_않다(JourneyPhase phase) {
        assertThat(registry.specsFor(phase)).hasSizeLessThanOrEqualTo(13);
    }

    /** 묶인 소재를 풀 방법은 생산 단계에서 보여야 한다. */
    @Test
    void 배치_취소는_생산_단계에서_보인다() {
        assertThat(names(JourneyPhase.PRODUCTION)).contains("cancel_production_batch");
    }

    /**
     * 단계가 달라 거절할 때 <b>어디로 옮기면 되는지</b> 말해 준다.
     *
     * <p>"지금 단계에서 쓸 수 없습니다" 만 돌려주면 모델이 그 기능이 없다고 결론 낸다. 실제로
     * 생산 단계에서 판매 시작을 요청받고 "저는 할 수 없고 담당자가 직접 플랫폼에서 처리해야
     * 합니다" 라고 답했다 — 단계만 옮기면 되는 일이었고, 담당자는 제품 화면으로 나갔다.
     */
    @Test
    void 단계가_다르면_옮길_단계를_알려준다() {
        String hint = registry.phaseHintFor("set_product_on_sale", JourneyPhase.PRODUCTION);

        assertThat(hint).contains("PRODUCT");
        assertThat(hint).contains("set_phase");
        assertThat(hint).contains("할 수 없는 일이 아닙니다");
    }

    /** 없는 이름은 단계 문제가 아니다. 옮기라고 안내하면 옮긴 뒤 같은 실패를 반복한다. */
    @Test
    void 없는_이름은_단계를_옮기라고_하지_않는다() {
        String hint = registry.phaseHintFor("make_me_a_sandwich", JourneyPhase.PRODUCTION);

        assertThat(hint).contains("그런 이름의 기능이 없습니다");
        assertThat(hint).doesNotContain("set_phase");
    }

    private List<String> names(JourneyPhase phase) {
        return registry.specsFor(phase).stream().map(ToolSpec::name).toList();
    }
}
