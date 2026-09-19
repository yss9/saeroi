package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.processing.dto.ProcessingQuoteResponse;
import com.ssafy.upcycle.processing.service.ProcessingQuoteService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 가공 요청에 들어온 견적.
 *
 * <p>견적에는 가격과 납기가 함께 온다. <b>둘을 같이 보여준다</b> — 가장 싼 곳이 2주 늦으면
 * 그건 싼 것이 아니다. 담당자가 납기를 모르고 가격만 보고 고르면 나중에 되돌릴 수 없다.
 *
 * <p>업체 한 곳에 요청 하나다. 여러 곳을 비교하려면 요청을 여러 건 내고 각각 이 tool 을
 * 부른다.
 */
@Component
public class ListQuotesTool extends AbstractAgentTool {

    private final ProcessingQuoteService quoteService;

    public ListQuotesTool(ObjectMapper objectMapper, ProcessingQuoteService quoteService) {
        super(objectMapper);
        this.quoteService = quoteService;
    }

    @Override
    public String name() {
        return "list_quotes";
    }

    @Override
    public String description() {
        return "가공 요청에 들어온 견적 목록. 업체명·가격·납기(일)를 담당자에게 함께 보여주고 "
                + "무엇을 왜 추천하는지 이유를 붙인다. 가장 싼 곳이 항상 정답은 아니다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "requestId": {"type": "integer", "description": "가공 요청 id"}
                  },
                  "required": ["requestId"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.PROCESSING);
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long requestId = requiredPositiveLong(arguments, "requestId");

        List<ProcessingQuoteResponse> quotes = quoteService.list(context.userId(), requestId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", requestId);
        payload.put("quotes", quotes);
        payload.put("hint", quotes.isEmpty()
                ? "아직 견적이 없습니다. 업체가 견적을 낼 때까지 기다려야 합니다."
                : "가격(price)은 이 요청 전체의 가공비이고 leadDays 는 소요 일수입니다. "
                        + "둘을 함께 설명하세요.");
        return AgentToolResult.of(json(payload));
    }
}
