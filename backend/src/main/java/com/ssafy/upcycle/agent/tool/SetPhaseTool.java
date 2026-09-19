package com.ssafy.upcycle.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 여정 단계를 옮긴다.
 *
 * <p>모델이 스스로 단계를 정하게 하는 이유는, 단계 전환의 조건이 규칙으로 쓰기 어렵기
 * 때문이다. "정보가 충분히 모였는가" 는 담당자가 무엇을 말했는지에 달려 있고, 코드로
 * 판정하려면 결국 같은 판단을 더 나쁘게 재현하게 된다.
 *
 * <p>대신 단계를 바꿔도 <b>할 수 있는 일이 늘어날 뿐 권한은 늘어나지 않는다.</b> 어느 단계든
 * 쓰기는 사용자 확인을 거치고, 인가는 기존 도메인 서비스가 본다. 그래서 모델이 단계를 잘못
 * 옮겨도 생기는 최악은 "엉뚱한 tool 이 보이는 것" 이다.
 */
@Component
public class SetPhaseTool extends AbstractAgentTool {

    public SetPhaseTool(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String name() {
        return "set_phase";
    }

    @Override
    public String description() {
        return "여정 단계를 옮긴다. INTAKE(요구사항 파악) → MATERIAL_SOURCING(소재 확보) → "
                + "PROCESSING(가공) → PRODUCTION(생산) → PRODUCT(제품 등록). "
                + "담당자가 이전 단계로 돌아가고 싶어 하면 되돌려도 된다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "phase": {"type": "string",
                      "enum": ["INTAKE","MATERIAL_SOURCING","PROCESSING","PRODUCTION","PRODUCT"]},
                    "reason": {"type": "string", "description": "이 단계로 옮기는 이유"}
                  },
                  "required": ["phase"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of();
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        String requested = requiredText(arguments, "phase");

        JourneyPhase phase;
        try {
            phase = JourneyPhase.valueOf(requested);
        } catch (IllegalArgumentException unknown) {
            throw new AgentToolArgumentException("알 수 없는 단계입니다: " + requested);
        }
        if (phase == JourneyPhase.DONE) {
            // 종료는 사용자만 할 수 있다. 모델이 대화를 닫아 버리면 담당자가 이어서 물을 수 없다.
            throw new AgentToolArgumentException("대화 종료는 담당자가 직접 합니다.");
        }

        context.conversation().moveTo(phase);
        return AgentToolResult.of(json(Map.of("phase", phase.name(), "changed", true)));
    }
}
