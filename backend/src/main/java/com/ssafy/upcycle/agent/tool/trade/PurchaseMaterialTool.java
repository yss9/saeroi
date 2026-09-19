package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.trade.dto.PaymentReadyResponse;
import com.ssafy.upcycle.trade.dto.TradeCreateRequest;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.service.PaymentService;
import com.ssafy.upcycle.trade.service.TradeCommandService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 소재를 구매한다. <b>거래 생성과 결제를 한 카드로 묶는다.</b>
 *
 * <p>거래 생성 자체는 돈이 나가지 않으니 원칙대로면 카드 없이 실행할 수 있다. 그런데 거래를
 * 만들면 <b>판매 잔여 수량이 즉시 줄어들어</b> 다른 구매자가 못 사게 된다
 * ({@code TradeController} 주석). 그리고 담당자 입장에서 "산다" 는 하나의 행동이다.
 *
 * <p>묶으면 카드를 거절했을 때 거래 자체가 만들어지지 않는다 — 결제되지 않은 유령 거래가
 * 남지 않는다. (만료 워커가 60초마다 정리하긴 하지만, 애초에 안 만드는 편이 낫다.)
 *
 * <p>담당자가 승인하면 {@link #execute} 가 불려 거래를 만들고 결제 세션을 연다. 화면은
 * 응답의 {@code redirectUrl} 로 카카오페이를 띄운다.
 */
@Component
public class PurchaseMaterialTool extends AbstractAgentTool {

    private final TradeCommandService tradeCommand;
    private final PaymentService paymentService;

    public PurchaseMaterialTool(
            ObjectMapper objectMapper, TradeCommandService tradeCommand, PaymentService paymentService) {
        super(objectMapper);
        this.tradeCommand = tradeCommand;
        this.paymentService = paymentService;
    }

    @Override
    public String name() {
        return "purchase_material";
    }

    @Override
    public String description() {
        return "판매 글에서 소재를 구매한다. 담당자에게 결제 확인 카드를 띄운다. "
                + "부르기 전에 수량과 배송지를 대화로 합의하고, get_shipping_default 로 기본 배송지를 확인하라. "
                + "카드를 띄운 뒤에는 결과를 기다린다 — 결제됐다고 가정하고 다음 단계로 넘어가면 안 된다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "listingId": {"type": "integer", "description": "구매할 판매 글"},
                    "quantityGram": {"type": "integer", "minimum": 1,
                      "description": "구매 수량(gram). 판매 글의 전량이 아니어도 된다"},
                    "receiverName": {"type": "string"},
                    "receiverPhone": {"type": "string"},
                    "zipCode": {"type": "string"},
                    "address": {"type": "string"},
                    "addressDetail": {"type": "string"}
                  },
                  "required": ["listingId","quantityGram","receiverName","receiverPhone","zipCode","address"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.MATERIAL_SOURCING);
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public PendingActionKind pendingKind() {
        return PendingActionKind.PAYMENT;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long listingId = requiredPositiveLong(arguments, "listingId");
        long quantityGram = requiredPositiveLong(arguments, "quantityGram");

        // 금액을 인자로 받지 않는다. TradeCreateRequest 도 같은 이유로 totalPrice 를 받지
        // 않는다 — 클라이언트(여기서는 모델)가 금액을 정하면 1원짜리 거래를 만들 수 있다.
        TradeCreateRequest request = new TradeCreateRequest(
                listingId,
                quantityGram,
                MaterialQuantityUnit.G,
                PaymentMethod.KAKAO_PAY,
                requiredText(arguments, "receiverName"),
                requiredText(arguments, "receiverPhone"),
                requiredText(arguments, "zipCode"),
                requiredText(arguments, "address"),
                optionalText(arguments, "addressDetail"));

        TradeResponse trade = tradeCommand.create(context.userId(), request);
        // 복귀 주소를 대화창으로 잡는다. 기본값(거래 상세)으로 보내면 결제 후 대화가 사라진다.
        PaymentReadyResponse payment =
                paymentService.ready(context.userId(), trade.tradeId(), true);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tradeId", trade.tradeId());
        payload.put("totalPrice", trade.totalPrice());
        payload.put("quantityGram", quantityGram);
        // 화면이 이 주소로 카카오페이를 띄운다.
        payload.put("redirectUrl", payment.redirectUrl());

        String summary = "소재 구매 결제를 시작했습니다. %,d원".formatted(trade.totalPrice());
        return AgentToolResult.of(json(payload), summary);
    }

    @Override
    protected String requiredText(JsonNode arguments, String field) {
        String value = super.requiredText(arguments, field);
        if (value.length() > 255) {
            throw new AgentToolArgumentException(field + " 이(가) 너무 깁니다.");
        }
        return value;
    }
}
