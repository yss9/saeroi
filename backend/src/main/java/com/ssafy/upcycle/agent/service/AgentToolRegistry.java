package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.service.llm.ToolSpec;
import com.ssafy.upcycle.agent.tool.AgentTool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 지금 단계에서 모델에게 보여줄 tool 을 고른다.
 *
 * <p><b>왜 전부 주지 않는가.</b> 여정 전체의 tool 은 30개가 넘는다. 한꺼번에 넘기면 모델이
 * 엉뚱한 단계의 tool 을 부르기 시작한다 — 아직 소재도 안 샀는데 생산 배치를 열려 하는 식이다.
 * 단계별로 좁혀 주면 그런 선택지가 애초에 없다.
 *
 * <p>Spring 이 {@link AgentTool} 빈을 전부 주입하므로, tool 을 추가할 때 이 클래스는 손대지
 * 않는다. 새 tool 이 자기 {@code phases()} 로 스스로 위치를 선언한다.
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> byName = new LinkedHashMap<>();

    public AgentToolRegistry(List<AgentTool> tools) {
        for (AgentTool tool : tools) {
            AgentTool duplicate = byName.put(tool.name(), tool);
            if (duplicate != null) {
                // 이름이 겹치면 모델이 부른 tool 이 어느 쪽인지 알 수 없다. 기동 시 터뜨린다.
                throw new IllegalStateException("Duplicate agent tool name: " + tool.name());
            }
        }
    }

    /** 이 단계에서 쓸 수 있는 tool 의 명세. */
    public List<ToolSpec> specsFor(JourneyPhase phase) {
        return availableFor(phase).stream()
                .map(tool -> new ToolSpec(tool.name(), tool.description(), tool.parametersJsonSchema()))
                .toList();
    }

    public List<AgentTool> availableFor(JourneyPhase phase) {
        return byName.values().stream()
                .filter(tool -> tool.phases().isEmpty() || tool.phases().contains(phase))
                .toList();
    }

    /**
     * 이름으로 찾되 <b>단계를 함께 확인한다.</b>
     *
     * <p>단계를 보지 않으면, 모델이 이전 턴에서 본 tool 이름을 기억해 두었다가 단계가 바뀐
     * 뒤에도 부를 수 있다. 노출만 좁히고 실행을 안 막으면 좁힌 의미가 없다.
     */
    public Optional<AgentTool> find(String name, JourneyPhase phase) {
        AgentTool tool = byName.get(name);
        if (tool == null) {
            return Optional.empty();
        }
        if (!tool.phases().isEmpty() && !tool.phases().contains(phase)) {
            return Optional.empty();
        }
        return Optional.of(tool);
    }

    /** 단계를 무시하고 찾는다. 사용자가 이미 승인한 액션을 실행할 때만 쓴다. */
    public Optional<AgentTool> findApproved(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /**
     * 단계가 맞지 않아 거절할 때 <b>어디로 옮기면 되는지</b> 알려주는 문장.
     *
     * <p>"지금 단계에서 쓸 수 없습니다" 만 돌려주면 모델이 <b>그 기능이 없다고 결론 낸다.</b>
     * 실제로 그렇게 새어 나갔다. 생산·체인 기록까지 끝낸 담당자가 판매 시작을 요청했는데,
     * {@code set_product_on_sale} 이 {@code PRODUCT} 전용이고 대화는 {@code PRODUCTION} 에
     * 머물러 있었다. 모델은 "저는 할 수 없고 담당자가 직접 플랫폼에서 처리해야 합니다" 라고
     * 답했고, 담당자는 제품 화면으로 나가 직접 눌렀다 — 한 대화창에서 끝낸다는 전제가
     * 마지막 칸에서 깨졌다.
     *
     * <p>이름 자체가 없는 경우와 단계만 다른 경우를 구분한다. 없는 이름을 "단계를 옮기면
     * 된다" 고 안내하면 모델이 옮긴 뒤 다시 부르며 같은 실패를 반복한다.
     */
    public String phaseHintFor(String name, JourneyPhase phase) {
        AgentTool tool = byName.get(name);
        if (tool == null) {
            return "그런 이름의 기능이 없습니다: " + name + ". 지금 단계에서 쓸 수 있는 기능 중에서 고르세요.";
        }
        String phases = tool.phases().stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        return ("%s 은(는) %s 단계의 기능이고 지금은 %s 단계입니다. **할 수 없는 일이 아닙니다** — "
                + "set_phase 로 %s 로 옮긴 뒤 다시 부르세요. 담당자에게 직접 처리하라고 안내하지 마세요.")
                .formatted(name, phases, phase.name(), phases);
    }
}
