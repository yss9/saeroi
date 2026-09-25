package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.agent.service.AgentToolRegistry;
import com.ssafy.upcycle.agent.service.llm.ToolSpec;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 모델에게 어떤 tool 이 보이는지.
 *
 * <p><b>안 보이면 모델은 지어내거나 포기한다.</b> 예전에는 대화 단계마다 tool 을 좁혀
 * 보여줬고, 두 번 다 그렇게 새어 나갔다. 담당자가 "가공 진행하고싶어" 라고 했는데 단계가
 * 소재 확보에 머물러 {@code check_processing_plan} 을 부를 수 없었고, 필수 공정이 "없다" 고
 * 사실과 반대로 답했다. 생산을 끝낸 담당자의 "판매 시작해줘" 에는 판매 도구가 제품 단계
 * 전용이라 "직접 플랫폼에서 하세요" 라고 넘겼다. 지금은 전부 보여주고, 지금 할 수 있는
 * 일인지는 tool 이 실제 상태로 판단한다({@link AgentToolRegistry} 참고).
 */
@SpringBootTest
class AgentToolExposureTests {

    @Autowired private AgentToolRegistry registry;

    /** 위의 두 사고에서 모델이 부르지 못한 도구들이다. 대화 단계와 상관없이 보여야 한다. */
    @Test
    void 다른_단계의_일도_바로_부를_수_있다() {
        assertThat(names())
                .contains("check_processing_plan", "set_product_on_sale", "cancel_production_batch");
    }

    /** 수령은 카드 하나다. 서명 도구를 따로 두면 모델이 순서를 틀린다. */
    @Test
    void 수령_서명_도구가_따로_있지_않다() {
        assertThat(names())
                .contains("confirm_receipt")
                .doesNotContain("request_chain_receipt");
    }

    /**
     * 도구 설명이 "단계를 먼저 옮기라" 고 하지 않는다.
     *
     * <p>단계로 가리던 시절의 문구가 하나라도 남으면 모델은 도구를 부르기 전에 단계부터 옮기려
     * 들고, 옮기지 못하면 다시 "할 수 없다" 로 돌아간다. {@code set_phase} 자신만 예외다.
     */
    @Test
    void 도구_설명이_단계_이동을_선행_조건으로_말하지_않는다() {
        List<String> offenders = registry.specs().stream()
                .filter(spec -> !spec.name().equals("set_phase"))
                .filter(spec -> spec.description().contains("set_phase")
                        || spec.parametersJsonSchema().contains("set_phase"))
                .map(ToolSpec::name)
                .toList();

        assertThat(offenders).isEmpty();
    }

    /** 없는 이름은 단계 문제가 아니다. 옮기라고 안내하면 옮긴 뒤 같은 실패를 반복한다. */
    @Test
    void 없는_이름은_단계를_옮기라고_하지_않는다() {
        String hint = registry.unknownToolHint("make_me_a_sandwich");

        assertThat(hint).contains("그런 이름의 기능이 없습니다");
        assertThat(hint).doesNotContain("set_phase");
    }

    private List<String> names() {
        return registry.specs().stream().map(ToolSpec::name).toList();
    }
}
