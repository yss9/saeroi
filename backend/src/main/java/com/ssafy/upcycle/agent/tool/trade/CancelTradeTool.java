package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.service.PaymentService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 진행하다 만 거래를 정리한다.
 *
 * <p><b>왜 필요한가.</b> 결제 카드를 띄워 놓고 담당자가 마음을 바꾸거나 결제창을 닫으면
 * 거래는 {@code CREATED} 로 남고, 그만큼의 판매 수량이 계속 묶인다. 대화 안에 정리 수단이
 * 없으면 담당자는 거래 목록 화면으로 나가야 하고, 그건 이 기능이 없애려던 이동이다.
 *
 * <p>취소된 거래도 행으로 남는다. 같은 판매 글에서 몇 건이 취소됐는지가 분쟁 때 근거가
 * 되기 때문이다({@code TradeController#cancel}).
 *
 * <p><b>체인의 예약은 여기서 풀리지 않는다.</b> 온체인 {@code cancelOrder} 는 당사자 서명을
 * 요구해 서버가 대신할 수 없다. 이미 체인에 기록된 거래라면 서버 수량만 돌아오고 예약은
 * 남으므로, 담당자가 거래 상세에서 직접 해제해야 한다. 그 경우 결과 메시지로 알린다 —
 * 모르고 지나가면 체인에만 예약이 남아 다음 구매에서 잔량이 어긋난다.
 */
@Component
public class CancelTradeTool extends AbstractAgentTool {

    private final PaymentService paymentService;
    // 카드가 아직 유효한지 보려고 거래를 읽는다.
    private final MaterialTradeRepository trades;

    public CancelTradeTool(
            ObjectMapper objectMapper, PaymentService paymentService, MaterialTradeRepository trades) {
        super(objectMapper);
        this.paymentService = paymentService;
        this.trades = trades;
    }

    @Override
    public String name() {
        return "cancel_trade";
    }

    @Override
    public String description() {
        return "진행 중인 소재 거래를 취소한다. 판매 글의 잔여 수량이 복구된다. "
                + "결제 전(CREATED)이거나 결제만 한(PAID) 거래에만 쓸 수 있고, "
                + "판매자가 발송한 뒤에는 불가능하다. 결제한 거래는 환불된다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "tradeId": {"type": "integer"},
                    "reason": {"type": "string", "description": "담당자가 말한 취소 사유. 없으면 생략"},
                    "summary": {"type": "string", "description": "카드에 보일 한 줄 설명"}
                  },
                  "required": ["tradeId", "summary"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public PendingActionKind pendingKind() {
        return PendingActionKind.CONFIRM;
    }

    /** 이미 취소됐거나, 발송돼 취소할 수 없는 거래인가. */
    /**
     * <b>리포지토리로 직접 읽는다.</b> {@code TradeQueryService} 같은 {@code @Transactional}
     * 서비스를 부르면, 거기서 던진 예외를 여기서 잡아도 Spring 이 바깥 트랜잭션을
     * rollback-only 로 표시해 승인 전체가 커밋되지 않는다 — 없는 거래 id 로 카드가 떠 있을
     * 때 실제로 그렇게 깨졌다. 이 검사는 실패해도 정상 흐름을 막지 않아야 한다.
     */
    @Override
    public java.util.Optional<String> stalenessReason(
            AgentToolContext context, JsonNode arguments) {
        MaterialTrade trade = trades
                .findById(requiredPositiveLong(arguments, "tradeId"))
                .filter(candidate -> candidate.getBuyerId().equals(context.userId()))
                .orElse(null);
        // 없거나 남의 거래면 판정하지 않는다. 그건 승인 시점에 도메인이 거절할 일이다.
        if (trade == null) {
            return java.util.Optional.empty();
        }
        if (trade.getStatus() == TradeStatus.CANCELED) {
            return java.util.Optional.of("이 거래는 이미 취소돼 판매 글의 수량이 복구된 상태입니다.");
        }
        if (trade.getStatus() != TradeStatus.CREATED && trade.getStatus() != TradeStatus.PAID) {
            return java.util.Optional.of(
                    "판매자가 이미 발송해 취소할 수 없습니다. 수령 후 반품 절차로 처리해야 합니다.");
        }
        return java.util.Optional.empty();
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long tradeId = requiredPositiveLong(arguments, "tradeId");
        String reason = optionalText(arguments, "reason");

        TradeResponse trade = paymentService.cancel(context.userId(), tradeId, reason);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tradeId", tradeId);
        payload.put("trade", trade);

        String message = trade.chainTradeId() == null
                ? "거래를 취소했습니다. 판매 글의 수량이 복구되었습니다."
                : "거래를 취소했고 판매 글의 수량이 복구되었습니다. 다만 이 거래는 블록체인에 "
                        + "기록돼 있어 체인의 예약은 아직 남아 있습니다 — 거래 상세 화면에서 "
                        + "직접 해제해야 합니다(서명이 필요해 서버가 대신할 수 없습니다).";
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
