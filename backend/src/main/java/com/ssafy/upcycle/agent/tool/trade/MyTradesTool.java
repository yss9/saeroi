package com.ssafy.upcycle.agent.tool.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.dto.TradeSearchCondition;
import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 담당자의 구매 거래 현황.
 *
 * <p>두 가지에 쓴다.
 *
 * <ul>
 *   <li><b>대기 구간 안내</b> — "발송됐어?" 에 답하려면 지금 상태를 읽어야 한다. 알림이 DB
 *       inbox 뿐이라 대화가 먼저 알 방법이 없고, 담당자가 물을 때 조회하는 것이 유일한 길이다.
 *   <li><b>빠뜨린 후속 작업 찾기</b> — 결제는 끝났는데 온체인 기록이 안 된 거래처럼, 대화가
 *       중간에 끊겨 남은 일을 다시 집어낸다. {@code needsChainOrder} 가 그 표시다.
 * </ul>
 *
 * <p>화면에서 진행한 건도 함께 나온다. 에이전트가 자기가 만든 것만 아는 것이 아니라
 * <b>담당자의 현황 전체</b>를 본다.
 */
@Component
public class MyTradesTool extends AbstractAgentTool {

    private static final int SIZE = 5;

    private final TradeQueryService tradeQuery;
    private final ChainOrderStatusReader chainOrders;

    public MyTradesTool(
            ObjectMapper objectMapper,
            TradeQueryService tradeQuery,
            ChainOrderStatusReader chainOrders) {
        super(objectMapper);
        this.tradeQuery = tradeQuery;
        this.chainOrders = chainOrders;
    }

    @Override
    public String name() {
        return "get_my_trades";
    }

    @Override
    public String description() {
        return "담당자의 소재 구매 거래 현황. 결제·배송·수령 상태를 확인할 수 있다. "
                + "\"발송됐어?\" 같은 질문에 답하거나, 결제는 끝났는데 남은 일이 있는지 확인할 때 부른다. "
                + "needsChainOrder 가 true 면 결제는 됐지만 블록체인 기록이 아직이라 "
                + "request_chain_order 로 서명 카드를 띄워야 한다. "
                + "needsChainReceipt 가 true 면 수령은 확정됐지만 그것이 아직 체인에 "
                + "남지 않았다 — confirm_receipt 로 서명 카드를 띄워야 한다. "
                + "두 값은 별개다. 하나가 false 라고 다른 하나까지 끝난 것이 아니다.";
    }

    /** 수령은 확정됐는데 체인에는 아직 안 남은 거래인가. */
    private boolean needsReceiptSignature(TradeResponse trade) {
        if (trade.chainTradeId() == null || trade.receivedAt() == null) {
            return false;
        }
        return chainOrders.needsReceiptSignature(trade.chainTradeId()).orElse(false);
    }

    @Override
    public String parametersJsonSchema() {
        return NO_ARGUMENTS_SCHEMA;
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.MATERIAL_SOURCING, JourneyPhase.PROCESSING);
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        // side 를 BUY 로 고정한다. 브랜드는 여기서 사는 쪽이고, 판매 거래까지 섞이면
        // "내 구매 현황" 이라는 질문의 답이 흐려진다.
        var page = tradeQuery.findMyTrades(
                context.userId(),
                new TradeSearchCondition(TradeSearchCondition.TradeSide.BUY, null, 0, SIZE));

        if (page.content().isEmpty()) {
            return AgentToolResult.of(json(Map.of(
                    "trades", List.of(),
                    "hint", "진행 중인 구매 거래가 없습니다.")));
        }

        // 수령 서명 여부는 DB 로 알 수 없다. buyer_signed_at 이 서명 없이 채워지기 때문에
        // 체인에 직접 물어본다 — 체인이 정본이다. 못 읽으면 false 로 둔다.
        List<Item> items = page.content().stream()
                .map(trade -> Item.from(trade, needsReceiptSignature(trade)))
                .toList();
        return AgentToolResult.of(json(Map.of("trades", items)));
    }

    /**
     * 모델에게 줄 거래 한 건.
     *
     * <p>{@code TradeResponse} 를 그대로 내보내지 않는다. 필드가 많아 입력 토큰이 늘고,
     * 크레딧이 토큰당 차감이라 그 차이가 곧 비용이다.
     */
    private record Item(
            Long tradeId,
            String status,
            String materialName,
            long quantityGram,
            long totalPrice,
            boolean needsChainOrder,
            boolean needsChainReceipt) {

        /**
         * @param needsReceiptSignature 체인에서 읽은 값. 못 읽었으면 {@code false} 로 둔다 —
         *     모르는 것을 "필요하다" 로 말하면 모델이 revert 할 서명 카드를 띄운다
         */
        static Item from(TradeResponse trade, boolean needsReceiptSignature) {
            return new Item(
                    trade.tradeId(),
                    trade.status() == null ? null : trade.status().name(),
                    trade.materialName(),
                    trade.quantity(),
                    trade.totalPrice(),
                    // 결제는 끝났는데 온체인 주문이 아직 없는 상태. 대화가 끊겼을 때 여기서 드러난다.
                    trade.chainTradeId() == null && isPaidOrLater(trade),
                    needsReceiptSignature);
        }

        private static boolean isPaidOrLater(TradeResponse trade) {
            String status = trade.status() == null ? "" : trade.status().name();
            return switch (status) {
                case "PAID", "SHIPPED", "RECEIVED" -> true;
                default -> false;
            };
        }
    }
}
