package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.dto.TradeSearchCondition;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputResponse;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
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
    private final ProductionBatchService batchService;
    private final ProductModelQueryService productQuery;
    private final ChainOrderStatusReader chainOrders;

    /**
     * 사건 보고에 덧붙일 문장. 할 일이 없으면 빈 문자열이다.
     *
     * <p>조회가 실패해도 <b>사건 보고 자체를 막지 않는다.</b> 결제가 끝났다는 사실을 대화에
     * 넣는 것이 더 중요하고, 상태는 모델이 직접 조회해 보완할 수 있다.
     */
    public String describe(Long userId, LocalDateTime conversationStartedAt) {
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
        try {
            lines.addAll(productionLines(userId, conversationStartedAt));
        } catch (RuntimeException ignored) {
            // 나머지만 붙인다.
        }
        try {
            lines.addAll(productLines(userId));
        } catch (RuntimeException ignored) {
            // 나머지만 붙인다.
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
                    + "있다. confirm_receipt 로 서명 카드를 띄운다 — 서명하면 수령까지 확정된다";
        }
        if (trade.status() == TradeStatus.CREATED) {
            return "결제가 아직이다. 담당자가 결제창을 닫았다면 cancel_trade 로 정리한다";
        }
        return null;
    }

    /**
     * 열려 있는 생산 배치. <b>번호를 여기서 알려주는 것이 요점이다.</b>
     *
     * <p>카드가 승인되면 실행 결과({@code payloadJson})는 액션 행에 저장되고, 대화 이력에는
     * 사람이 읽을 요약만 들어간다({@code AgentActionStore.apply}). 그래서 모델은 방금 자기가
     * 열게 한 배치의 번호를 <b>본 적이 없다.</b>
     *
     * <p>그 상태에서 유일하게 본 숫자가 카드를 띄울 때 받은 {@code actionId} 라서, 그것을
     * 배치 번호로 썼다. 실제로 카드 44번을 승인해 배치 3번이 열렸는데 모델이
     * {@code add_production_inputs} 에 {@code batchId:44} 를 넘겨 "생산 배치를 찾을 수
     * 없습니다" 로 끝났다 — 담당자는 자기가 승인한 작업이 오류로 끝나는 것을 봤다.
     *
     * <p>{@code DRAFT} 만 넣는다. 확정된 배치는 담당자 손이 필요하지 않고, 다 넣으면 이
     * 목록이 이력처럼 길어져 읽히지 않는다.
     */
    /**
     * 열려 있는 생산 배치. <b>번호를 알려주는 것과 재촉하지 않는 것을 함께 해야 한다.</b>
     *
     * <p>번호가 필요한 이유는 따로 있다. 카드가 승인되면 실행 결과는 액션 행에만 저장되고
     * 대화 이력에는 사람이 읽을 요약만 들어가므로, 모델은 방금 자기가 열게 한 배치의 번호를
     * 본 적이 없다. 그래서 유일하게 본 숫자인 카드 번호를 배치 번호로 썼다 — 카드 44번을
     * 승인해 배치 3번이 열렸는데 {@code batchId:44} 를 넘겨 "생산 배치를 찾을 수 없습니다"
     * 로 끝났다.
     *
     * <h2>버려진 배치를 할 일로 올리지 않는다</h2>
     *
     * <p>처음에는 열려 있는 배치를 모두 할 일로 올렸다. 그러자 지난 테스트에서 열어 두고 만
     * 빈 배치가 영구 숙제가 됐다 — 폐기할 수단이 없어 지워지지도 않으므로 모델이 매번
     * "배치 3과 4의 소재 투입과 생산 확정이 남았습니다. 이어서 진행할까요?" 라고 졸랐다.
     *
     * <p>다음 시도는 <b>"가장 최근 배치 하나만" 이었고 그것도 틀렸다.</b> 번호 순서는 진행
     * 여부를 말해 주지 않는다. 실제로 방치된 배치 3·4 중 번호가 큰 4번이 "할 일" 로 뽑혔고,
     * 정작 그 대화가 만들고 확정까지 끝낸 배치 5는 {@code DRAFT} 가 아니라 목록에서 빠졌다.
     * 모델은 여전히 졸랐다.
     *
     * <p>그래서 기준을 시간으로 바꿨다. <b>이 대화가 시작된 뒤에 열린 배치</b>이거나
     * <b>소재가 이미 투입된 배치</b>만 담당자 손이 필요한 일이다. 전자는 지금 진행 중인
     * 것이고, 후자는 누군가 실제로 작업을 해 둔 것이라 확정하지 않으면 소재가 묶인 채
     * 남는다. 그 외의 빈 배치는 지난 작업의 흔적이다.
     */
    @Transactional(readOnly = true)
    protected List<String> productionLines(Long userId, LocalDateTime conversationStartedAt) {
        List<ProductionBatchResponse> drafts = batchService.findMyBatches(userId).stream()
                .filter(batch -> batch.status() == ProductionBatchStatus.DRAFT)
                .sorted(Comparator.comparing(ProductionBatchResponse::productionBatchId))
                .toList();
        if (drafts.isEmpty()) {
            return List.of();
        }

        List<ProductionBatchResponse> active = drafts.stream()
                .filter(batch -> isActive(batch, conversationStartedAt))
                .toList();
        List<ProductionBatchResponse> leftovers = drafts.stream()
                .filter(batch -> !isActive(batch, conversationStartedAt))
                .toList();

        List<String> lines = new ArrayList<>();
        for (ProductionBatchResponse batch : active) {
            lines.add("- 생산 배치 %d(%s, %d개 예정): %s".formatted(
                    batch.productionBatchId(), batch.batchName(), batch.producedQuantity(),
                    batchTodo(batch)));
        }

        // 흔적도 번호는 알려 둔다. 담당자가 그 배치를 직접 말할 수 있고, 그때 모델이 번호를
        // 지어내면 없는 배치에 투입을 시도한다. 다만 먼저 꺼내지는 말라고 못 박는다.
        if (!leftovers.isEmpty()) {
            String rest = leftovers.stream()
                    .map(batch -> "%d(%s)".formatted(batch.productionBatchId(), batch.batchName()))
                    .collect(Collectors.joining(", "));
            lines.add("- 지난 작업의 흔적인 빈 배치: " + rest
                    + " — **할 일이 아니다.** 담당자가 이 배치를 먼저 말하지 않으면 "
                    + "언급하지도, 진행을 권유하지도 않는다. 진행 상황을 물으면 이것을 빼고 답한다. "
                    + "담당자가 정리하겠다고 하면 cancel_production_batch 로 취소한다");
        }
        return lines;
    }

    /**
     * 지금 진행 중인 배치인가.
     *
     * <p>소재가 투입된 배치는 시점과 무관하게 할 일이다 — 확정하지 않으면 그 소재가 묶인 채
     * 남는다. 빈 배치는 이 대화가 시작된 뒤에 열린 것만 진행 중으로 본다.
     */
    private boolean isActive(ProductionBatchResponse batch, LocalDateTime conversationStartedAt) {
        if (lockedInput(batch) > 0) {
            return true;
        }
        return conversationStartedAt != null
                && batch.createdAt() != null
                && !batch.createdAt().isBefore(conversationStartedAt);
    }

    /** 이 배치에서 다음에 할 일. 투입이 없으면 투입부터, 있으면 확정이다. */
    private String batchTodo(ProductionBatchResponse batch) {
        long locked = lockedInput(batch);
        if (locked == 0) {
            return "아직 소재를 투입하지 않았다. add_production_inputs 에 이 배치 번호를 넘긴다";
        }
        // 투입된 소재는 잠겨 있어 다른 곳에 쓸 수 없다. 확정하든 취소하든 결정이 나야
        // 풀리므로, 확정만 알려주면 담당자가 "그만두겠다" 고 할 때 답할 길이 없다.
        return "소재 %,dg 이 투입돼 잠겨 있다. confirm_production 으로 확정하거나, ".formatted(locked)
                + "담당자가 그만두겠다고 하면 cancel_production_batch 로 취소해 소재를 풀어 준다";
    }

    /**
     * 이 배치에 잠겨 있는 투입량.
     *
     * <p><b>{@code totalUsedQuantity} 를 쓰면 안 된다.</b> 그 값은 확정 시점에 제품에 실제로
     * 쓰인 양으로 채워지고({@code ProductionBatch.confirm}), 그 전까지는 <b>언제나 0</b> 이다.
     * 처음에 그것으로 판정했다가 두 곳이 같이 틀렸다 — 소재를 넣어 둔 배치에도 "아직 소재를
     * 투입하지 않았다" 고 안내해 같은 배치에 투입을 또 권했고, 오래된 배치에 9kg 이 잠겨
     * 있어도 "지난 작업의 흔적인 빈 배치" 로 분류해 모델에게 <b>언급하지 말라</b>고 했다.
     * 묶인 소재를 드러내려고 만든 목록이 바로 그것을 감췄다.
     *
     * <p>투입 목록의 합이 실제 잠긴 양이다. {@code findMyBatches} 가 배치마다 투입을 함께
     * 읽어 오므로 여기서 더 조회하지 않는다.
     */
    private long lockedInput(ProductionBatchResponse batch) {
        if (batch.inputs() == null) {
            return 0L;
        }
        return batch.inputs().stream()
                .mapToLong(ProductionInputResponse::inputQuantity)
                .sum();
    }

    /**
     * 만들어 두고 <b>판매를 시작하지 않은 제품.</b>
     *
     * <p>이 줄이 없으면 여정의 마지막 칸이 대화 밖으로 떨어진다. 실제로 그랬다. 생산 확정과
     * 체인 기록까지 끝낸 담당자가 "판매시작해줘" 라고 했는데, {@code set_product_on_sale} 이
     * {@code PRODUCT} 단계 전용이고 대화는 {@code PRODUCTION} 에 머물러 있어서 모델이
     * <b>"저는 할 수 없고 담당자가 직접 플랫폼에서 처리해야 합니다"</b> 라고 답했다. 사실이
     * 아니다 — 단계를 옮기면 할 수 있는 일이다. 담당자는 제품 화면으로 나가 직접 눌렀고,
     * 한 대화창에서 끝낸다는 전제가 마지막 단계에서 깨졌다.
     *
     * <p>그래서 단계를 옮기라는 지시까지 함께 넣는다. 프롬프트에 같은 규칙이 있지만 경량
     * 모델은 그것을 판매 요청에 연결하지 못했다.
     */
    @Transactional(readOnly = true)
    protected List<String> productLines(Long userId) {
        List<String> lines = new ArrayList<>();
        for (ProductModelResponse model : productQuery.findMyProductModels(userId)) {
            if (model.status() != ProductModelStatus.DRAFT || model.stockQuantity() <= 0) {
                continue;
            }
            lines.add(("- 제품 %d(%s, %,d원): 재고 %d개가 있는데 아직 판매 전이다. "
                    + "담당자가 판매를 말하면 set_phase 로 PRODUCT 로 옮긴 뒤 "
                    + "set_product_on_sale 을 부른다 — 할 수 없는 일이라고 답하지 않는다")
                    .formatted(model.productModelId(), model.name(), model.price(),
                            model.stockQuantity()));
        }
        return lines;
    }
}
