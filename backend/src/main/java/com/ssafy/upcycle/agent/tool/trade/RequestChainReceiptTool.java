package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 수령을 체인에 기록하도록 서명 카드를 띄운다. <b>소유권이 실제로 옮겨가는 자리다.</b>
 *
 * <h2>왜 수령 확정과 따로인가</h2>
 *
 * <p>거래 상세 화면이 이 순서를 쓴다 — 서버 수령 확정({@code POST /receive/confirm})이
 * 먼저, 그 성공 뒤에 체인 서명({@code Marketplace.confirmReceipt}). 대화에서 순서를
 * 뒤집으면 같은 작업이 두 경로에서 다르게 동작하고, 한쪽이 실패했을 때 남는 상태도
 * 달라진다. 그래서 {@code confirm_receipt}(확인 카드)를 서명 카드로 바꾸지 않고, 그
 * 뒤를 잇는 카드를 따로 둔다.
 *
 * <p>이 서명이 <b>체인에서 소유권을 옮기는 유일한 자리다.</b> 컨트랙트의
 * {@code confirmReceipt} 가 ERC1155 잔액을 판매자에서 구매자로 이동시킨다. 놓치면 DB 는
 * 구매자 소유인데 체인은 판매자 잠금에 남아 두 기록이 영구히 어긋난다.
 *
 * <h2>판매자 발송 서명이 선행 조건이다</h2>
 *
 * <p>컨트랙트가 순서를 강제한다 — {@code confirmReceipt} 는 주문이 체인에서
 * {@code SHIPPED} 일 때만 받는다({@code Marketplace.sol}). 판매자가 발송 서명을 하지
 * 않았으면 이 서명은 revert 한다. 서버 상태만으로는 알 수 없어 프론트가 체인을 읽고
 * 담당자에게 이유를 알린다.
 *
 * <h2>서버가 체인에 쓰지 않는다</h2>
 *
 * <p>{@code request_chain_order} 와 같다. 다만 이쪽은 <b>서버에 알릴 것이 없다</b> —
 * 새 번호를 채번하지 않고 이미 있는 주문의 상태를 바꿀 뿐이라 chain-link API 가 없다
 * ({@code useTradeChainAction} 주석). 그래서 여기서는 서명 사실만 대화에 남긴다.
 */
@Component
public class RequestChainReceiptTool extends AbstractAgentTool {

    // 카드가 아직 유효한지 보려고 거래를 읽는다.
    private final MaterialTradeRepository trades;

    public RequestChainReceiptTool(ObjectMapper objectMapper, MaterialTradeRepository trades) {
        super(objectMapper);
        this.trades = trades;
    }

    @Override
    public String name() {
        return "request_chain_receipt";
    }

    @Override
    public String description() {
        return "수령 확정이 끝난 거래를 블록체인에 기록하도록 담당자에게 서명 카드를 띄운다. "
                + "이 서명으로 체인에서 소재 소유권이 판매자에서 담당자에게 넘어온다. "
                + "confirm_receipt 로 수령을 확정한 **직후에** 부른다. "
                + "온체인에 기록된 거래(chainTradeId 가 있는 거래)에만 쓸 수 있다. "
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
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.MATERIAL_SOURCING);
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
     * 체인에 없는 거래이거나 수령 확정 전인가.
     *
     * <p>리포지토리로 직접 읽는다 — {@code @Transactional} 서비스를 부르면 그 안의 예외를
     * 잡아도 Spring 이 바깥 트랜잭션을 rollback-only 로 표시한다.
     *
     * <p>체인 상태 자체는 서버가 모른다. 이미 서명이 끝난 주문인지는 프론트가 체인을 읽어
     * 판단하고, 그 경우 카드를 완료로 닫는다.
     */
    @Override
    public Optional<String> stalenessReason(AgentToolContext context, JsonNode arguments) {
        MaterialTrade trade = trades
                .findById(requiredPositiveLong(arguments, "tradeId"))
                .filter(candidate -> candidate.getBuyerId().equals(context.userId()))
                .orElse(null);
        if (trade == null) {
            return Optional.empty();
        }
        if (trade.getChainTradeId() == null) {
            return Optional.of(
                    "이 거래는 블록체인에 기록되지 않아 체인에 남길 수령 기록이 없습니다. "
                            + "온체인에 등록되지 않은 판매 글에서 산 거래입니다.");
        }
        if (trade.getReceivedAt() == null) {
            return Optional.of(
                    "아직 수령이 확정되지 않았습니다. confirm_receipt 로 수령을 먼저 확정하세요.");
        }
        return Optional.empty();
    }

    /**
     * 담당자가 서명을 끝낸 뒤에만 불린다.
     *
     * <p>서버에 붙일 것이 없어 기록만 남긴다. 체인 상태의 정본은 체인이고, 다음에 무엇을
     * 할지는 프론트가 체인을 읽어 정한다.
     */
    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long tradeId = requiredPositiveLong(arguments, "tradeId");
        JsonNode userResult = arguments.path("_userResult");
        String txHash = userResult.path("txHash").asText(null);
        boolean alreadyReceived = userResult.path("alreadyReceived").asBoolean(false);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tradeId", tradeId);
        payload.put("signed", txHash != null || alreadyReceived);
        payload.put("txHash", txHash);

        String message;
        if (alreadyReceived) {
            message = "이 거래의 수령은 이미 블록체인에 기록돼 있습니다. 추가 서명이 필요하지 않습니다.";
        } else if (txHash == null) {
            message = "서명이 확인되지 않았습니다. 다시 시도해 주세요.";
        } else {
            message = "수령이 블록체인에 기록됐습니다. 소재 소유권이 담당자에게 넘어왔습니다.";
        }
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
