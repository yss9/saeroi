package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 결제한 거래를 온체인 주문으로 만들도록 서명 카드를 띄운다.
 *
 * <h2>서버가 체인에 쓰지 않는다</h2>
 *
 * <p>서명은 브라우저의 개인키로만 가능하다. 실제 컨트랙트 호출은 프론트가 기존
 * {@code marketplaceContractService.createOrder} 로 하고, 그 결과를 기존 chain-link API
 * ({@code POST /api/v1/trades/{id}/chain}) 로 서버에 붙인다 — 화면에서 하던 것과 <b>같은
 * 경로</b>다. 서버가 체인 기록을 따로 만들면 두 벌이 되고, 어긋나는 순간 어느 쪽이 정본인지
 * 알 수 없게 된다.
 *
 * <p>그래서 여기서 하는 일은 "무엇에 서명해야 하는지" 를 카드로 전달하고, 담당자가 끝낸 뒤
 * 그 사실을 대화에 남기는 것뿐이다.
 *
 * <h2>온체인 등록된 판매 글에서만 가능하다</h2>
 *
 * <p>{@code createOrder} 의 첫 인자가 온체인 {@code listingId} 이고 그것은 <b>판매자가
 * 서명해야 생긴다.</b> 구매자 혼자서는 시작할 수 없다 ({@code useTradeOnChain} 주석).
 * 판매 글이 온체인에 없으면 이 tool 을 부르지 말고, 담당자에게 그 사실을 알린다.
 */
@Component
public class RequestChainOrderTool extends AbstractAgentTool {

    // 서버가 체인에 쓰지는 않지만, 카드가 아직 유효한지는 봐야 한다.
    private final MaterialTradeRepository trades;

    public RequestChainOrderTool(ObjectMapper objectMapper, MaterialTradeRepository trades) {
        super(objectMapper);
        this.trades = trades;
    }

    @Override
    public String name() {
        return "request_chain_order";
    }

    @Override
    public String description() {
        return "결제가 끝난 거래를 블록체인에 기록하도록 담당자에게 서명 카드를 띄운다. "
                + "판매 글이 온체인에 등록된 경우(search_listings 의 onChain=true)에만 부를 수 있다. "
                + "부른 뒤에는 결과를 기다린다 — 서명이 끝났다고 가정하지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "tradeId": {"type": "integer"},
                    "summary": {"type": "string",
                      "description": "담당자가 무엇에 서명하는지 한 줄로. 카드에 그대로 보인다"}
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
        return PendingActionKind.WALLET_SIGN;
    }

    /**
     * 이미 체인에 올라간 거래인가.
     *
     * <p>같은 거래를 두 번 기록할 수 없다. 담당자가 거래 상세 화면에서 먼저 기록했거나,
     * 모델이 완료된 거래에 다시 카드를 띄운 경우가 여기 걸린다 — 후자가 실제로 있었다.
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
        if (trade.getChainTradeId() != null) {
            return java.util.Optional.of(
                    "이 거래는 이미 블록체인에 기록돼 있어 다시 서명할 필요가 없습니다.");
        }
        if (trade.getStatus() != TradeStatus.PAID) {
            return java.util.Optional.of(
                    "이 거래는 온체인 기록을 남길 수 있는 상태가 아닙니다. 결제가 끝난 거래만 기록합니다.");
        }
        return java.util.Optional.empty();
    }

    /**
     * 담당자가 서명을 끝낸 뒤에만 불린다.
     *
     * <p>{@code txHash} 를 검증하지 않는다. 그 검증은 chain-link API 가 영수증을 RPC 로
     * 확인하며 이미 하고 있다 ({@code TradeChainLinkService}). 여기서 또 하면 같은 규칙이
     * 두 곳에 생긴다.
     */
    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long tradeId = requiredPositiveLong(arguments, "tradeId");
        String summary = requiredText(arguments, "summary");

        JsonNode userResult = arguments.path("_userResult");
        String txHash = userResult.path("txHash").asText(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tradeId", tradeId);
        payload.put("signed", txHash != null);
        payload.put("txHash", txHash);
        payload.put("summary", summary);

        String message = txHash == null
                ? "서명이 확인되지 않았습니다. 다시 시도해 주세요."
                : "주문이 블록체인에 기록됐습니다. (tx " + shorten(txHash) + ")";
        payload.put("message", message);

        return AgentToolResult.of(json(payload), message);
    }

    /** 해시 전체는 대화에서 읽히지 않는다. 앞뒤만 보여 사람이 대조할 수 있게 한다. */
    private String shorten(String txHash) {
        return txHash.length() <= 14
                ? txHash
                : txHash.substring(0, 10) + "…" + txHash.substring(txHash.length() - 4);
    }
}
