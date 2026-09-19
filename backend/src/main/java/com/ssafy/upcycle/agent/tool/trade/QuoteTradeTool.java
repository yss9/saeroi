package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.trade.service.TradeCommandService;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 구매 전 금액을 확인한다.
 *
 * <p>모델이 {@code pricePerKg × 수량} 을 스스로 계산해 말하지 않게 하려고 둔다. 반올림 규칙과
 * 단위 환산이 서버에 있고, 담당자에게 보여준 금액과 실제 청구액이 다르면 신뢰를 잃는다.
 */
@Component
public class QuoteTradeTool extends AbstractAgentTool {

    private final TradeCommandService tradeCommand;

    public QuoteTradeTool(ObjectMapper objectMapper, TradeCommandService tradeCommand) {
        super(objectMapper);
        this.tradeCommand = tradeCommand;
    }

    @Override
    public String name() {
        return "quote_trade";
    }

    @Override
    public String description() {
        return "판매 글에서 특정 수량을 살 때의 금액을 계산한다. "
                + "담당자에게 금액을 말하기 전에 반드시 이걸로 확인한다 — 직접 곱해서 말하지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "listingId": {"type": "integer"},
                    "quantityGram": {"type": "integer", "minimum": 1}
                  },
                  "required": ["listingId", "quantityGram"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.MATERIAL_SOURCING);
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        return AgentToolResult.of(json(tradeCommand.quote(
                requiredPositiveLong(arguments, "listingId"),
                requiredPositiveLong(arguments, "quantityGram"),
                MaterialQuantityUnit.G)));
    }
}
