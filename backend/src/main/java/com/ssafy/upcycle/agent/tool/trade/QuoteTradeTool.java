package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.service.CompanyNames;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.trade.service.TradeCommandService;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.dto.TradeQuoteResponse;
import com.ssafy.upcycle.trade.repository.ListingSearchQueryRepository;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import java.util.Map;
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
    /** 견적에 어느 판매 글인지(판매자·등급)를 붙이려고 읽는다. 없는 글이면 null 을 돌려주어 터지지 않는다. */
    private final ListingSearchQueryRepository listings;
    private final CompanyNames companyNames;

    public QuoteTradeTool(
            ObjectMapper objectMapper,
            TradeCommandService tradeCommand,
            ListingSearchQueryRepository listings,
            CompanyNames companyNames) {
        super(objectMapper);
        this.listings = listings;
        this.tradeCommand = tradeCommand;
        this.companyNames = companyNames;
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
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        TradeQuoteResponse quote = tradeCommand.quote(
                requiredPositiveLong(arguments, "listingId"),
                requiredPositiveLong(arguments, "quantityGram"),
                MaterialQuantityUnit.G);
        // 금액은 서버가 계산한 것을 문장으로 준다. 모델이 gram 에 kg 단가를 곱해
        // 25,000,000원이라고 말한 적이 있다.
        Map<String, Object> payload = fields(quote);
        payload.put("quantityText", Quantities.text(quote.quantity()));
        payload.put("availableText", Quantities.text(quote.availableQuantity()));
        payload.put("totalPriceText", "%,d원".formatted(quote.totalPrice()));
        // 견적만 보고 말하면 "폐데님 원단 10kg : 20,000원" 이 된다 — 누구에게서 사는지가 빠진다.
        ListingResponse listing = listings.findDetail(quote.listingId());
        if (listing != null) {
            payload.put("sellerName", companyNames.of(listing.sellerId(), listing.sellerName()));
            payload.put("grade", listing.grade());
            payload.put("originText", listing.rootOrigin() == MaterialRootOrigin.VIRGIN ? "신소재" : "폐소재");
        }
        payload.put("hint", "금액을 말할 때 어느 판매 글인지(판매자·등급·폐소재/신소재·kg당 단가)도 함께 말합니다.");
        return AgentToolResult.of(json(payload));
    }
}
