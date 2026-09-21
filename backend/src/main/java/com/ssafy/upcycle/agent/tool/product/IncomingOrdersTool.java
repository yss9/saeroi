package com.ssafy.upcycle.agent.tool.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.product.dto.BrandProductOrderItemResponse;
import com.ssafy.upcycle.product.dto.BrandProductOrderResponse;
import com.ssafy.upcycle.product.dto.ProductOrderSearchCondition;
import com.ssafy.upcycle.product.service.BrandProductOrderQueryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 내 제품에 들어온 주문. 여정의 끝이자 담당자가 가장 궁금해하는 것이다.
 *
 * <p>판매를 시작한 뒤에는 "팔렸나", "보낼 게 있나" 가 다음 물음이다. 그 답이 대화창에
 * 없으면 담당자는 결국 다른 화면을 열어야 하고, 그러면 여기까지 온 의미가 줄어든다.
 *
 * <h2>배송지를 담지 않는다</h2>
 *
 * <p>주문 응답에는 구매자의 배송지가 들어 있다. <b>남의 집 주소다.</b> 이 tool 의 결과는
 * 그대로 모델의 입력이 되어 외부 LLM 으로 나가고, 대화 이력에도 남는다. 주문이 왔다는
 * 사실과 무엇을 보내야 하는지를 아는 데 주소는 필요 없으므로 빼고 보낸다 — 실제 발송은
 * 주문 화면에서 하고, 주소는 거기서 본다.
 *
 * <p>구매자 이름도 같은 이유로 넣지 않는다. 대화에서 "김OO 님 주문" 이라고 부를 일이
 * 없고, 주문 번호로 충분하다.
 */
@Component
public class IncomingOrdersTool extends AbstractAgentTool {

    /** 한 번에 볼 주문 수. 전부 넣으면 입력 토큰만 늘고 읽히지 않는다. */
    private static final int PAGE_SIZE = 20;

    private final BrandProductOrderQueryService orderQuery;

    public IncomingOrdersTool(
            ObjectMapper objectMapper, BrandProductOrderQueryService orderQuery) {
        super(objectMapper);
        this.orderQuery = orderQuery;
    }

    @Override
    public String name() {
        return "get_incoming_orders";
    }

    @Override
    public String description() {
        return "내 제품에 들어온 주문을 본다. 결제·발송 상태와 무엇이 몇 개 팔렸는지 알 수 있다. "
                + "담당자가 판매 현황이나 보낼 주문을 물으면 부른다. "
                + "배송지와 구매자 이름은 나오지 않는다 — 발송은 주문 화면에서 한다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {"type": "object", "properties": {}, "additionalProperties": false}""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.PRODUCT);
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        PageResponse<BrandProductOrderResponse> page = orderQuery.findIncomingOrders(
                context.userId(), new ProductOrderSearchCondition(null, 0, PAGE_SIZE));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BrandProductOrderResponse order : page.content()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", order.orderId());
            row.put("orderCode", order.orderCode());
            row.put("status", order.status());
            row.put("totalPriceWon", order.totalPrice());
            row.put("orderedAt", order.orderedAt());
            row.put("shippedAt", order.shippedAt());
            row.put("products", productsOf(order));
            row.put("todo", todoOf(order));
            rows.add(row);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orders", rows);
        payload.put("totalOrders", page.totalElements());
        payload.put(
                "hint",
                "todo 가 담당자의 다음 행동이다. 발송은 이 대화가 아니라 주문 화면에서 한다 — "
                        + "배송지를 봐야 하고 그 주소는 대화에 싣지 않는다.");
        return AgentToolResult.of(json(payload));
    }

    /**
     * 무엇이 몇 개 팔렸는가.
     *
     * <p>주문 항목은 <b>개체 하나가 한 줄</b>이다(각자 일련번호를 갖는다). 그대로 넘기면
     * 같은 제품이 열 줄로 늘어서 모델이 열 종류를 샀다고 읽는다. 제품별로 세어서 준다.
     */
    private List<Map<String, Object>> productsOf(BrandProductOrderResponse order) {
        List<Map<String, Object>> products = new ArrayList<>();
        if (order.items() == null) {
            return products;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BrandProductOrderItemResponse item : order.items()) {
            counts.merge(item.productName(), 1, Integer::sum);
        }
        counts.forEach((name, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productName", name);
            row.put("quantity", count);
            products.add(row);
        });
        return products;
    }

    /**
     * 이 주문에서 <b>담당자만 할 수 있는</b> 다음 일.
     *
     * <p>{@code get_my_trades} 와 같은 방식이다. 상태 이름만 주면 모델이 뜻을 추측한다.
     */
    private String todoOf(BrandProductOrderResponse order) {
        return switch (order.status()) {
            case CREATED -> "구매자가 결제하기를 기다리는 중이다";
            case PAID -> "결제가 끝났다. 주문 화면에서 발송 처리를 해야 한다";
            case SHIPPED -> "보냈다. 구매자가 수령을 확인하기를 기다리는 중이다";
            case DELIVERED -> "구매자가 수령했다. 끝난 주문이다";
        };
    }
}
