package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.service.llm.ToolSpec;
import com.ssafy.upcycle.agent.tool.AgentTool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 모델이 부를 수 있는 tool 을 모아 둔다. <b>대화 단계와 상관없이 전부 보여준다.</b>
 *
 * <h2>단계로 가리지 않는 이유</h2>
 *
 * <p>예전에는 대화 단계마다 tool 을 좁혀 보여줬다. 소재도 안 샀는데 생산 배치를 여는 식의
 * 엉뚱한 호출을 막으려던 것인데, 실제로 새어 나간 사고는 전부 반대 방향이었다.
 *
 * <ul>
 *   <li>담당자가 "가공 진행하고싶어" 라고 했는데 단계가 소재 확보에 머물러
 *       {@code check_processing_plan} 을 부를 수 없었다. 모델은 기억으로 답했고, 필수 공정이
 *       "없다" 고 사실과 반대로 말했다. 그 규칙을 어기면 생산 확정에서 막히는데, 그때는 이미
 *       가공비를 낸 뒤다.
 *   <li>생산을 끝낸 담당자가 "판매 시작해줘" 라고 했는데 판매 도구가 제품 단계 전용이었다.
 *       모델은 "저는 할 수 없고 직접 플랫폼에서 처리하세요" 라고 답했고, 담당자는 대화창을
 *       떠났다.
 * </ul>
 *
 * <p>두 번 다 담당자는 할 수 있는 일을 요청했고, 모델은 단계 이름 하나 때문에 못 했다. 단계를
 * 먼저 옮기라고 프롬프트와 거절 문구로 거듭 일렀지만, 옮기는 것을 잊는 한 번이 곧 사고였다.
 *
 * <p>엉뚱한 호출은 이제 <b>tool 이 상태를 보고 막는다.</b> 쓰기 tool 은 카드를 띄우기 전에
 * 지금 그 일을 할 수 있는지 스스로 확인하고({@code refuseBeforeProposal}), 카드는 담당자가
 * 눌러야 실행되며, 인가는 도메인 서비스가 다시 본다. 대화 단계는 모델이 붙인 이름표일 뿐
 * 실제 상태가 아니라서, 그것으로 막는 것보다 실제 상태로 막는 편이 정확하다.
 *
 * <p>Spring 이 {@link AgentTool} 빈을 전부 주입하므로, tool 을 추가할 때 이 클래스는 손대지
 * 않는다.
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> byName = new LinkedHashMap<>();
    private final List<ToolSpec> specs;

    public AgentToolRegistry(List<AgentTool> tools) {
        for (AgentTool tool : tools) {
            AgentTool duplicate = byName.put(tool.name(), tool);
            if (duplicate != null) {
                // 이름이 겹치면 모델이 부른 tool 이 어느 쪽인지 알 수 없다. 기동 시 터뜨린다.
                throw new IllegalStateException("Duplicate agent tool name: " + tool.name());
            }
        }
        this.specs = byName.values().stream()
                .map(tool -> new ToolSpec(tool.name(), tool.description(), tool.parametersJsonSchema()))
                .toList();
    }

    /** 모델에게 보여줄 tool 명세. 매 턴 같은 목록이다. */
    public List<ToolSpec> specs() {
        return specs;
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /**
     * 없는 이름을 불렀을 때 모델에게 돌려줄 문장.
     *
     * <p>이름을 지어내 부른 것이라 다시 불러도 같다. 있는 이름에서 고르라고만 한다 — 단계를
     * 옮기라는 식으로 안내하면 옮긴 뒤 같은 이름을 다시 부르며 실패를 반복한다.
     */
    public String unknownToolHint(String name) {
        return "그런 이름의 기능이 없습니다: " + name + ". 도구 목록에 있는 이름 중에서 고르세요.";
    }
}
