package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.trade.dto.TradeReceiptResponse;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.service.TradeReceiptService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 수령을 확정한다. <b>소재 소유권이 실제로 옮겨가는 자리다.</b>
 *
 * <p>서버 쪽 잔액 이전({@code TradeReceiptService})은 순수 DB 작업이라 체인 없이도 완결된다.
 * 온체인 기록이 필요한 거래라면 프론트가 이어서 {@code confirmReceipt} 를 서명하고 chain-link
 * API 로 붙인다.
 *
 * <p><b>서명 카드가 아니다.</b> 서버 쪽 잔액 이전은 순수 DB 작업이라 지갑이 필요 없다.
 * 온체인 {@code confirmReceipt} 기록은 별개이며, 필요하면 그 뒤에 별도 서명 카드로 받는다.
 *
 * <p>되돌릴 수 없는 작업이라 확인 카드는 거친다 — 담당자가 물건을 실제로 받았는지는 서버가
 * 알 수 없고, 확정하는 순간 판매자에게 돈이 간다.
 */
@Component
public class ConfirmReceiptTool extends AbstractAgentTool {

    private final TradeReceiptService receiptService;
    // 카드가 아직 유효한지 보려고 거래를 읽는다.
    private final MaterialTradeRepository trades;

    public ConfirmReceiptTool(
            ObjectMapper objectMapper,
            TradeReceiptService receiptService,
            MaterialTradeRepository trades) {
        super(objectMapper);
        this.receiptService = receiptService;
        this.trades = trades;
    }

    @Override
    public String name() {
        return "confirm_receipt";
    }

    @Override
    public String description() {
        return "배송된 소재의 수령을 확정한다. 소유권이 이전되고 보유 소재에 들어온다. "
                + "되돌릴 수 없으므로 담당자가 실제로 물건을 받았는지 먼저 확인하고 부른다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "tradeId": {"type": "integer"},
                    "summary": {"type": "string", "description": "카드에 보일 한 줄 설명"}
                  },
                  "required": ["tradeId", "summary"],
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
        return PendingActionKind.CONFIRM;
    }

    /**
     * 이미 수령이 확정된 거래인가.
     *
     * <p>담당자가 거래 상세 화면에서 먼저 확정하면 이 카드는 할 일이 없어진다. 그대로
     * 실행하면 도메인이 거절하고, 담당자는 자기가 승인한 작업이 오류로 끝나는 것을 본다.
     */
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
        if (trade.getReceivedAt() != null) {
            return java.util.Optional.of("이 거래는 이미 수령이 확정돼 소재가 보유 목록에 들어와 있습니다.");
        }
        if (trade.getStatus() != TradeStatus.SHIPPED) {
            return java.util.Optional.of("아직 판매자가 발송하지 않아 수령을 확정할 수 없습니다.");
        }
        return java.util.Optional.empty();
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long tradeId = requiredPositiveLong(arguments, "tradeId");

        TradeReceiptResponse receipt = receiptService.confirmReceipt(context.userId(), tradeId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tradeId", tradeId);
        payload.put("receipt", receipt);

        String message = "수령이 확정되어 소재가 보유 목록에 들어왔습니다.";
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
