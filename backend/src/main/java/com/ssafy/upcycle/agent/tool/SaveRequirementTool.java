package com.ssafy.upcycle.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 대화에서 모은 요구사항을 대화에 고정한다.
 *
 * <p>이력은 길어지면 앞부분이 잘려 나간다({@code ConversationStore}). "데님 2kg 으로 에코백"
 * 같은 핵심이 그때 함께 사라지면 모델이 같은 것을 다시 묻는다. 여기 저장된 값은 매 턴
 * 시스템 프롬프트로 다시 들어가므로 잘리지 않는다.
 *
 * <p><b>실제로 잊었다.</b> "지갑 10개, 울과 데님을 섞어서" 로 시작한 대화가 192개 메시지까지 길어졌는데 이 도구를
 * 한 번도 부르지 않아, 가공을 마친 뒤 모델이 "무엇을 만들고 싶으신가요?", "몇 개요?" 를 다시 물었다. 그래서 설명에
 * <b>언제 부르는지</b>를 적고, 저장된 것이 없으면 시스템 프롬프트가 그 사실을 알린다({@code AgentPromptBuilder}).
 * 소재도 여러 가지를 담는다 — 두 소재를 섞는 제품은 흔한데 예전에는 한 가지만 적을 수 있었다.
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
        return "담당자가 정한 것(만들 제품, 만들 개수, 쓸 소재와 양, 가공 공정, 납기, 예산)을 대화에 저장한다. "
                + "**담당자가 말하는 그 턴에 부른다** — 제품이나 개수, 소재가 처음 나왔을 때, 그리고 바뀔 때마다. "
                + "대화가 길어지면 앞부분은 잊히고 여기 저장된 것만 매 턴 다시 보인다. "
                + "부를 때마다 지금까지 정해진 것 전체를 다시 넘긴다(빠진 항목은 지워진다). 담당자가 말하지 않은 것은 넣지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "productDescription": {"type": "string", "description": "만들려는 제품. 예: '울·데님을 섞은 업사이클링 지갑'"},
                    "productQuantity": {"type": "integer", "minimum": 1, "description": "만들 개수"},
                    "materials": {"type": "array",
                      "description": "쓸 소재. 섞어 쓰면 소재마다 한 줄",
                      "items": {
                        "type": "object",
                        "properties": {
                          "materialType": {"type": "string", "enum": [%s],
                            "description": "실제 취급 섬유 7종 + 기타. 가죽·금속·부자재처럼 7종에 없는 소재는 모두 ETC 다. 원래 표현은 note 에 남긴다"},
                          "rootOrigin": {"type": "string", "enum": ["WASTE", "VIRGIN"],
                            "description": "폐소재(WASTE)인지 신소재(VIRGIN)인지. 담당자가 정했을 때만"},
                          "quantityGram": {"type": "integer", "minimum": 1, "description": "필요한 양(gram)"},
                          "note": {"type": "string", "description": "소재에 붙은 조건. 예: '개당 0.5kg'"}
                        },
                        "required": ["materialType"],
                        "additionalProperties": false
                      }},
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
                // 담당자에게 알릴 일이 아니다 — 대화의 메모다. 예전 문구("요구사항을 저장했습니다. 이제 맞는 소재를
                // 찾으면 됩니다")를 모델이 그대로 옮겨, 결제 뒤에 "좋습니다! 요구사항이 저장되었습니다." 가 떴다.
                "message", "대화 메모를 갱신했습니다. 담당자에게 저장했다고 말하지 말고 하던 이야기를 이어 가세요.")));
    }
}
