package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.service.llm.ToolSpec;
import com.ssafy.upcycle.agent.tool.AgentTool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
}
