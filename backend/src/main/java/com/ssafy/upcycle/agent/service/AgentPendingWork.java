package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.dto.TradeSearchCondition;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화 밖에서 사건이 보고될 때, <b>지금 담당자 손이 필요한 일</b>을 함께 붙여 준다.
 *
 * <h2>왜 필요한가 — 낡은 조회 결과를 모델이 믿는다</h2>
 *
 * <p>실제로 이렇게 새어 나갔다. 결제 전에 모델이 {@code get_my_trades} 를 불러
 * {@code needsChainOrder:false} 를 받았다(그 시점에는 맞는 값이다). 결제가 끝나 복귀
 * 이벤트가 들어왔지만 모델은 다시 조회하지 않고 이력에 남은 옛 결과를 근거로
 * "온체인 기록이 남지 않는 거래" 라고 <b>사실과 다른 설명</b>을 했다. 판매 글은 온체인에
 * 등록돼 있었고 거래는 서명만 기다리는 상태였다.
 *
 * <p>프롬프트에 "결제 완료를 알게 되면 곧바로 get_my_trades 를 부르세요" 라고 적어 두었지만
 * 지키지 않았다. <b>모델의 기억에 의존하는 규칙은 경량 모델에서 자주 깨진다.</b> 그래서
 * 사건을 알릴 때 서버가 현재 상태를 함께 넣는다 — 모델이 기억하지 않아도 눈앞에 있다.
 *
 * <p>여기서 LLM 을 부르지 않는다. 도메인 조회 한 번이라 크레딧이 들지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AgentPendingWork {

    /** 이벤트 보고에 함께 넣을 거래 수. 전부 넣으면 입력 토큰만 늘고 읽히지 않는다. */
    private static final int SIZE = 10;

    private final TradeQueryService tradeQuery;
    private final ProcessingJobWorkflowService jobWorkflow;
    private final ChainOrderStatusReader chainOrders;

    /**
     * 사건 보고에 덧붙일 문장. 할 일이 없으면 빈 문자열이다.
     *
     * <p>조회가 실패해도 <b>사건 보고 자체를 막지 않는다.</b> 결제가 끝났다는 사실을 대화에
     * 넣는 것이 더 중요하고, 상태는 모델이 직접 조회해 보완할 수 있다.
     */
    public String describe(Long userId) {
        List<String> lines = new ArrayList<>();
        // 거래와 가공을 따로 감싼다. 한쪽 조회가 흔들려도 다른 쪽 할 일은 살아 있어야 한다 —
        // 이 문장을 붙이는 이유가 모델이 낡은 값으로 지어내는 것을 막는 것이기 때문이다.
        try {
            lines.addAll(tradeLines(userId));
        } catch (RuntimeException ignored) {
            // 아래에서 가공만이라도 붙인다.
        }
        try {
            lines.addAll(processingLines(userId));
        } catch (RuntimeException ignored) {
            // 거래만 붙인다.
        }
        if (lines.isEmpty()) {
            return "";
        }
        return """

                ## 지금 담당자 손이 필요한 일 (서버가 방금 조회한 실제 상태)

                %s

                이 목록이 정본이다. 앞선 조회 결과가 이것과 다르면 이 목록을 따르고,
                해당하는 도구를 그 자리에서 부른다."""
                .formatted(String.join(System.lineSeparator(), lines));
    }

    /**
     * 가공 작업 중 담당자 차례인 것.
     *
     * <p><b>기다리는 중인 작업은 넣지 않는다.</b> 이 목록의 뜻은 "지금 손이 필요한 일" 이고,
     * 여기 적히면 모델이 무언가 하려 든다. 결제 직후 화면이 "다음은 소재를 보낼 차례" 라고
     * 알려 오던 것을 바로잡는 것이 이 목록을 가공까지 넓힌 이유다 — 실제로는 업체가
     * 수락하기 전이라 인계가 거절된다.
     *
     * <p>판정은 {@link ProcessingTodo} 가 한다. {@code get_my_processing} 과 경과 막대가
     * 같은 물음에 답하므로, 세 곳이 각자 판정하면 담당자가 받는 답이 서로 어긋난다.
     */
    @Transactional(readOnly = true)
    protected List<String> processingLines(Long userId) {
        List<String> lines = new ArrayList<>();
        for (ProcessingJobResponse job : jobWorkflow.list(userId)) {
            ProcessingTodo todo = ProcessingTodo.of(job.status());
            if (!todo.isActionable()) {
                continue;
            }
            lines.add("- 가공 작업 %d: %s".formatted(job.jobId(), todo.instruction()));
        }
        return lines;
    }

    @Transactional(readOnly = true)
    protected List<String> tradeLines(Long userId) {
        var page = tradeQuery.findMyTrades(
                userId, new TradeSearchCondition(TradeSearchCondition.TradeSide.BUY, null, 0, SIZE));

        List<String> lines = new ArrayList<>();
        for (TradeResponse trade : page.content()) {
            String todo = todoOf(trade);
            if (todo != null) {
                lines.add("- 거래 %d(%s): %s".formatted(trade.tradeId(), trade.materialName(), todo));
            }
        }
        return lines;
    }

    /**
     * 이 거래에서 담당자만 할 수 있는 다음 일. 없으면 상대를 기다리는 중이다.
     *
     * <p>판정 기준은 {@code get_my_trades} 의 {@code needsChainOrder} 와 같다. 두 곳에서
     * 다르게 계산하면 모델이 받은 두 값이 어긋난다.
     */
    private String todoOf(TradeResponse trade) {
        if (trade.status() == TradeStatus.PAID && trade.chainTradeId() == null) {
            return "결제는 끝났고 온체인 기록이 아직이다. request_chain_order 로 서명 카드를 띄운다";
        }
        if (trade.status() == TradeStatus.SHIPPED) {
            return "판매자가 발송했다. 물건을 받았는지 확인하고 confirm_receipt 를 부른다";
        }
        // 수령은 끝났는데 체인에 안 남은 경우. DB 로는 알 수 없어 체인에 물어본다 —
        // buyer_signed_at 이 서명 없이 채워지기 때문이다.
        if (trade.chainTradeId() != null
                && trade.receivedAt() != null
                && chainOrders.needsReceiptSignature(trade.chainTradeId()).orElse(false)) {
            return "수령은 확정됐지만 체인에 남지 않았다. 체인에서는 소재가 아직 판매자 잠금에 "
                    + "있다. request_chain_receipt 로 서명 카드를 띄운다";
        }
        if (trade.status() == TradeStatus.CREATED) {
            return "결제가 아직이다. 담당자가 결제창을 닫았다면 cancel_trade 로 정리한다";
        }
        return null;
    }
}
