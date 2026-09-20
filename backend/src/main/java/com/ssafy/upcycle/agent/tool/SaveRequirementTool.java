package com.ssafy.upcycle.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 대화에서 모은 요구사항을 대화에 고정한다.
 *
 * <p>이력은 길어지면 앞부분이 잘려 나간다({@code ConversationStore}). "데님 2kg 으로 에코백"
 * 같은 핵심이 그때 함께 사라지면 모델이 같은 것을 다시 묻는다. 여기 저장된 값은 매 턴
 * 시스템 프롬프트로 다시 들어가므로 잘리지 않는다.
 *
 * <p>쓰기 tool 이 아니다. 대화의 메모를 갱신할 뿐 도메인 데이터를 건드리지 않아서, 사용자
 * 확인을 요구하면 대화만 번거로워진다.
 */
@Component
public class SaveRequirementTool extends AbstractAgentTool {

    public SaveRequirementTool(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String name() {
        return "save_requirement";
    }

    @Override
    public String description() {
        return "담당자에게서 파악한 요구사항(만들 제품, 소재 종류, 수량, 필요한 가공 공정, 납기, 예산)을 "
                + "대화에 저장한다. 새로 알게 될 때마다 전체를 다시 넘겨 갱신한다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "productDescription": {"type": "string", "description": "만들려는 제품"},
                    "materialType": {"type": "string", "enum": [%s],
                      "description": "실제 취급 섬유 7종 + 기타. 가죽·금속·부자재처럼 7종에 없는 소재는 모두 ETC 다. 담당자가 말한 소재가 7종에 없으면 ETC 로 적고, 원래 표현은 note 에 남긴다"},
                    "quantityGram": {"type": "integer", "description": "필요한 소재량(gram)"},
                    "processTypes": {"type": "array",
                      "items": {"type": "string", "enum": [%s]}},
                    "dueDate": {"type": "string", "description": "납기 (YYYY-MM-DD)"},
                    "budgetKrw": {"type": "integer", "description": "예산(원)"},
                    "note": {"type": "string", "description": "그 밖의 조건"}
                  },
                  "required": ["productDescription"],
                  "additionalProperties": false
                }"""
                .formatted(enumValues(MaterialType.class), enumValues(ProcessType.class));
    }

    @Override
    public Set<JourneyPhase> phases() {
        // 어느 단계에서든 요구사항이 바뀔 수 있다. "역시 3kg 으로 할게" 는 흔한 말이다.
        return Set.of();
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        // 모델이 준 JSON 을 그대로 보관한다. 필드를 하나씩 뜯어 저장하면 요구사항이 늘어날
        // 때마다 스키마를 고쳐야 하는데, 이 값은 우리가 질의하는 대상이 아니라 다음 턴에
        // 모델에게 돌려줄 메모일 뿐이다.
        requiredText(arguments, "productDescription");
        context.conversation().updateRequirement(arguments.toString());

        return AgentToolResult.of(json(Map.of(
                "saved", true,
                "requirement", arguments,
                "message", "요구사항을 저장했습니다. 다음 단계로 넘어가려면 set_phase 를 부르세요.")));
    }
}
