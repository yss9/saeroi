package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.agent.service.AgentToolRegistry;
import com.ssafy.upcycle.agent.service.llm.ToolSpec;
import com.ssafy.upcycle.agent.tool.AgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ClassUtils;

/**
 * 모든 쓰기 도구는 <b>카드를 띄우기 전에 스스로 본다.</b>
 *
 * <p>카드는 담당자가 누르는 자리다. 지금 할 수 없는 일에 카드를 띄우면 담당자는 누른 뒤에야
 * 거절을 보고, 결제·서명 카드라면 지갑 팝업까지 거친 뒤다. 실제로 그렇게 새어 나갔다 — 반환
 * 발송 전에 결과 승인 서명 카드가 떠서 서명하자 컨트랙트가 되돌렸고, 결과를 올리지도 않은
 * 가공에 "되돌릴 수 없습니다" 경고가 붙은 생산 확정 카드가 떴다.
 *
 * <p>도구마다 사람이 기억해서 넣던 검사라 빠진 도구가 넷이었다(구매, 배치 열기, 배치 취소,
 * 제품 등록). 새 쓰기 도구가 같은 구멍으로 들어오지 않도록 여기서 막는다.
 */
@SpringBootTest
class WriteToolPreCheckCoverageTests {

    /**
     * 검사가 정말 필요 없는 쓰기 도구. 넣을 때는 <b>왜 필요 없는지</b>를 함께 적는다 — "나중에" 는
     * 이유가 아니다.
     */
    private static final Map<String, String> NO_PRE_CHECK_NEEDED = Map.of();

    @Autowired private AgentToolRegistry registry;

    @Test
    void 모든_쓰기_도구는_카드_전에_상태를_본다() {
        List<String> missing = registry.specs().stream()
                .map(ToolSpec::name)
                .map(name -> registry.find(name).orElseThrow())
                .filter(AgentTool::isWrite)
                .filter(tool -> !NO_PRE_CHECK_NEEDED.containsKey(tool.name()))
                .filter(tool -> !declares(tool, "refuseBeforeProposal") && !declares(tool, "stalenessReason"))
                .map(AgentTool::name)
                .toList();

        assertThat(missing)
                .as("쓰기 도구는 refuseBeforeProposal 이나 stalenessReason 으로 카드 전에 상태를 봐야 한다. "
                        + "정말 필요 없으면 NO_PRE_CHECK_NEEDED 에 이유와 함께 적는다")
                .isEmpty();
    }

    private boolean declares(AgentTool tool, String method) {
        try {
            ClassUtils.getUserClass(tool).getDeclaredMethod(method, AgentToolContext.class, JsonNode.class);
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }
}
