package com.ssafy.upcycle.agent.service.step;

import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.service.ProcessingTodo;
import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
import com.ssafy.upcycle.blockchain.service.ChainProcessingStatusReader;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputResponse;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.dto.TradeSearchCondition;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 담당자에게 남은 일을 <b>한 곳에서</b> 판정한다.
 *
 * <p>대화 프롬프트·진행 막대·할 일 패널이 모두 이 결과를 읽는다. 예전에는 넷이 각자
 * 계산했고 덮는 범위가 달라, 한 곳을 고쳐도 다른 경로로 빠져나갔다({@link BrandStep} 참고).
 *
 * <h2>여정 전체를 덮는다</h2>
 *
 * <p>담당자가 <b>모르는 단계일수록 서버가 꺼내야 한다.</b> 온체인 작업 등록이나 서명 4번은
 * 구현 세부이지 담당자가 아는 절차가 아니다. 목록에서 빠지면 그 단계는 존재하지 않는 것과
 * 같다 — 물어볼 수도 없기 때문이다.
 *
 * <pre>
 *   거래   결제 → 온체인 주문 서명 → (판매자 발송) → 수령 서명
 *   가공   요청 → 결제 → (업체 수락) → 온체인 등록 → (업체 체인 수락) → 인계 서명
 *          → (업체 작업) → 결과 승인 서명
 *   생산   배치 → 소재 투입 → 확정 → 온체인 기록(서명 4번) → 제품 연결
 *   제품   등록 → 판매 시작
 * </pre>
 *
 * <h2>이 대화가 시작한 일만 본다</h2>
 *
 * <p>대화 하나는 제품 프로젝트 하나다({@link AgentProject}). 이 목록은 대화가 <b>먼저 꺼내는</b>
 * 것이라 프로젝트의 일만 담는다. 예전에는 브랜드의 모든 거래를 봤고, 에코백을 만드는 대화에
 * 홈페이지에서 산 다른 소재의 "수령 확인" 카드가 떴다. 홈페이지에서 시작한 일도 담당자가
 * 물으면 처리한다 — 그건 도구의 몫이고, 이 목록이 막지 않는다.
 *
 * <h2>기다리는 구간도 담는다</h2>
 *
 * <p>{@link StepAction#WAIT} 을 빼면 모델이 "할 일이 없다" 로 읽고 재촉할 도구를 찾는다.
 * "상대 차례다" 라고 적어 두어야 그대로 알린다.
 *
 * <h2>한 갈래가 흔들려도 나머지는 살린다</h2>
 *
 * <p>네 영역을 각각 감싼다. 조회 하나가 실패했다고 목록 전체를 잃으면, 이 목록이 막으려던
 * 문제(모델이 낡은 값으로 지어내는 것)가 그대로 돌아온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrandStepResolver {

    /**
     * 프로젝트 거래를 찾으려고 읽을 구매 거래 수. 예전에는 최근 10건만 봤는데, 프로젝트 거래를
     * 번호로 고르게 되면서 그 사이 홈페이지에서 산 거래가 많으면 밀려날 수 있어 넓혔다.
     */
    private static final int TRADE_WINDOW = 100;
    /** 컨트랙트의 작업 상태 REQUESTED — 등록은 됐고 업체가 아직 수락하지 않았다({@code ChainProcessingStatusReader}). */
    private static final int CHAIN_REQUESTED = 1;

    private final TradeQueryService tradeQuery;
    private final ProcessingJobWorkflowService jobWorkflow;
    private final ProductionBatchService batchService;
    private final ProductModelQueryService productQuery;
    private final ChainOrderStatusReader chainOrders;
    private final ChainProcessingStatusReader chainJobs;
    private final MaterialRepository materials;
    private final ProductionChainProgress chainRecord;

    /** @param project 이 대화가 시작한 일. 비어 있으면 남은 일도 없다 */
    @Transactional(readOnly = true)
    public List<BrandStep> resolve(Long userId, AgentProject project) {
        List<BrandStep> steps = new ArrayList<>();
        if (project.isEmpty()) {
            return steps;
        }
        collect(steps, () -> tradeSteps(userId, project), "trade");
        collect(steps, () -> processingSteps(userId, project), "processing");
        collect(steps, () -> productionSteps(userId, project), "production");
        collect(steps, () -> productSteps(userId, project), "product");
        return steps;
    }

    private void collect(List<BrandStep> into, java.util.function.Supplier<List<BrandStep>> source, String area) {
        try {
            into.addAll(source.get());
        } catch (RuntimeException failure) {
            log.warn(
                    "Brand step resolve failed for {}: reason={} message={}",
                    area,
                    failure.getClass().getSimpleName(),
                    failure.getMessage());
        }
    }

    // ── 거래 ────────────────────────────────────────────────────────────

    private List<BrandStep> tradeSteps(Long userId, AgentProject project) {
        List<BrandStep> steps = new ArrayList<>();
        if (project.tradeIds().isEmpty()) {
            return steps;
        }
        var page = tradeQuery.findMyTrades(
                userId,
                new TradeSearchCondition(TradeSearchCondition.TradeSide.BUY, null, 0, TRADE_WINDOW));

        for (TradeResponse trade : page.content()) {
            if (!project.ownsTrade(trade.tradeId())) {
                continue;
            }
            BrandStep step = tradeStep(trade);
            if (step != null) {
                steps.add(step);
            }
        }
        return steps;
    }

    private BrandStep tradeStep(TradeResponse trade) {
        long id = trade.tradeId();
        // **양을 함께 적는다.** 같은 소재를 두 번 사면 이름만으로는 어느 거래인지 알 수 없다 — 실제로
        // "폴리에스터 원단 — 수령을 확정할 차례입니다" 가 두 줄 떠서 담당자는 같은 알림이 되풀이된 줄 알았다.
        String subject = "%s %s".formatted(trade.materialName(), Quantities.text(trade.quantity()));

        if (trade.status() == TradeStatus.CREATED) {
            return BrandStep.of(StepDomain.TRADE, id, subject, StepAction.PAY,
                    "결제할 차례입니다",
                    "결제가 아직이다. 담당자가 결제창을 닫았다면 cancel_trade 로 정리한다",
                    "cancel_trade");
        }
        if (trade.status() == TradeStatus.PAID && trade.chainTradeId() == null) {
            return BrandStep.of(StepDomain.TRADE, id, subject, StepAction.SIGN,
                    "온체인 기록 서명이 남았습니다",
                    "결제는 끝났고 온체인 기록이 아직이다. request_chain_order 로 서명 카드를 띄운다",
                    "request_chain_order");
        }
        if (trade.status() == TradeStatus.SHIPPED) {
            return BrandStep.of(StepDomain.TRADE, id, subject, StepAction.SIGN,
                    "수령을 확정할 차례입니다",
                    "판매자가 발송했다. 물건을 받았는지 확인하고 confirm_receipt 를 부른다",
                    "confirm_receipt");
        }
        // 수령은 끝났는데 체인에 안 남은 경우. DB 로는 알 수 없어 체인에 물어본다 —
        // buyer_signed_at 이 서명 없이 채워지던 시절의 데이터가 남아 있다.
        if (trade.chainTradeId() != null
                && trade.receivedAt() != null
                && chainOrders.needsReceiptSignature(trade.chainTradeId()).orElse(false)) {
            return BrandStep.of(StepDomain.TRADE, id, subject, StepAction.SIGN,
                    "수령이 체인에 남지 않았습니다",
                    "수령은 확정됐지만 체인에 남지 않았다. 체인에서는 소재가 아직 판매자 잠금에 "
                            + "있다. confirm_receipt 로 서명 카드를 띄운다 — 서명하면 수령까지 확정된다",
                    "confirm_receipt");
        }
        if (trade.status() == TradeStatus.PAID) {
            return BrandStep.waiting(StepDomain.TRADE, id, subject,
                    "판매자 발송을 기다리는 중입니다",
                    "결제와 온체인 기록이 끝났다. 판매자가 발송할 차례다 — 담당자가 할 일이 없다");
        }
        return null;
    }

    // ── 가공 ────────────────────────────────────────────────────────────

    private List<BrandStep> processingSteps(Long userId, AgentProject project) {
        List<BrandStep> steps = new ArrayList<>();
        if (project.jobIds().isEmpty()) {
            return steps;
        }
        for (ProcessingJobResponse job : jobWorkflow.list(userId)) {
            if (!project.ownsJob(job.jobId())) {
                continue;
            }
            ProcessingTodo todo = ProcessingTodo.of(job.status());
            if (todo.isClosed()) {
                continue;
            }

            // **온체인 등록은 DB 상태로 드러나지 않는다.** 업체가 결제를 수락하면 DB 는
            // ACCEPTED 가 되지만, 체인에 작업이 없으면 인계 서명을 할 수 없다. 이 단계가
            // 목록에 없어 담당자가 인계를 시도했다가 컨트랙트가 거절한 일이 있었다.
            if (job.status() == ProcessingJobStatus.ACCEPTED && job.chainJobId() == null) {
                steps.add(BrandStep.of(StepDomain.PROCESSING, job.jobId(), subjectOf(job),
                        StepAction.SIGN,
                        "소재를 보내기 위한 첫 번째 서명(작업 등록)이 필요합니다",
                        "업체가 수락했지만 작업이 아직 체인에 없다. register_processing_chain_job 으로 "
                                + "등록 서명을 받는다 — 그 뒤에 업체가 체인에서 한 번 더 수락해야 "
                                + "인계할 수 있다. 카드를 띄우거나 알릴 때 먼저 설명한다: 소재를 보내려면 "
                                + "서명이 두 번이다. 첫 번째(작업 등록)는 보낼 소재를 이 작업에 묶어 둘 뿐 소재가 "
                                + "나가지 않는다. 업체가 작업을 확인하면 두 번째(소재 보내기)로 소재가 업체로 넘어간다",
                        "register_processing_chain_job"));
                continue;
            }

            // **등록 뒤 업체가 체인에서 수락하기 전은 담당자 차례가 아니다.** DB 는 계속 ACCEPTED 라
            // 예전에는 이 구간도 "소재를 인계할 차례" 로 적었다. 인계 카드는 도구가 막아 뜨지 않았고, 모델은
            // 도구의 거절 문구를 그대로 옮겨 "업체가 온체인에서 수락하기를 기다리는 중" 이라고 말했다.
            if (job.status() == ProcessingJobStatus.ACCEPTED && awaitingProcessorOnChain(job.chainJobId())) {
                steps.add(BrandStep.waiting(StepDomain.PROCESSING, job.jobId(), subjectOf(job),
                        "업체가 작업 내용을 확인하는 중입니다",
                        "등록 서명은 끝났다. 업체가 작업 조건을 확인하고 수락하면 소재를 보낼 수 있다 — 담당자가 "
                                + "할 일은 없다. 담당자에게는 \"업체가 작업을 확인하는 중\" 이라고만 알린다"));
                continue;
            }

            steps.add(todo.isActionable()
                    ? BrandStep.of(StepDomain.PROCESSING, job.jobId(), subjectOf(job),
                            actionOf(todo), todo.label(), todo.instruction(), toolOf(todo))
                    : BrandStep.waiting(StepDomain.PROCESSING, job.jobId(), subjectOf(job),
                            todo.label(), todo.instruction()));
        }
        return steps;
    }

    /** 체인에 등록된 작업을 업체가 아직 수락하지 않았는가. 체인을 읽을 수 없으면 판정하지 않는다. */
    private boolean awaitingProcessorOnChain(Long chainJobId) {
        if (chainJobId == null || !chainJobs.canRead()) {
            return false;
        }
        return chainJobs.status(chainJobId).orElse(-1) == CHAIN_REQUESTED;
    }

    /**
     * **번호 대신 무엇을 맡겼는지 적는다.** 이 이름은 오른쪽 할 일 목록과 서버가 띄우는 카드 안내에 그대로
     * 나간다. "가공 작업 9 — 온체인 작업 등록이 필요합니다" 를 받은 담당자는 9번이 울인지 데님인지 알 수
     * 없었다. 번호는 모델이 도구에 넘길 수 있게 상태 블록에 따로 적힌다.
     */
    private String subjectOf(ProcessingJobResponse job) {
        String name = job.inputMaterialId() == null
                ? null
                : materials.findById(job.inputMaterialId()).map(Material::getName).orElse(null);
        if (name == null || name.isBlank()) {
            return "가공 작업 %d".formatted(job.jobId());
        }
        return "%s %s 가공".formatted(name, Quantities.text(job.inputQuantity()));
    }

    private StepAction actionOf(ProcessingTodo todo) {
        return switch (todo) {
            case PAY -> StepAction.PAY;
            case TRANSFER, APPROVE_ON_ARRIVAL -> StepAction.SIGN;
            default -> StepAction.CONFIRM;
        };
    }

    private String toolOf(ProcessingTodo todo) {
        return switch (todo) {
            case PAY -> "pay_processing_fee";
            case TRANSFER -> "request_custody_transfer";
            // 결과만 올라온 단계(REVIEW)는 도구가 없다. 승인은 반환 발송 뒤에만 된다 — 여기에
            // 도구를 붙이면 자동 카드가 절대 성공할 수 없는 서명을 띄운다.
            case APPROVE_ON_ARRIVAL -> "approve_processing_result";
            default -> null;
        };
    }

    // ── 생산 ────────────────────────────────────────────────────────────

    /**
     * 프로젝트 배치만 본다. 예전에는 "대화 시작 뒤에 열린 빈 배치" 와 "소재가 투입된 배치" 를
     * 시각으로 갈랐다 — 서버가 어느 배치가 이 대화의 것인지 몰랐기 때문이다. 이제는 안다.
     */
    private List<BrandStep> productionSteps(Long userId, AgentProject project) {
        List<BrandStep> steps = new ArrayList<>();
        if (project.batchIds().isEmpty()) {
            return steps;
        }
        for (ProductionBatchResponse batch : batchService.findMyBatches(userId)) {
            if (!project.ownsBatch(batch.productionBatchId())) {
                continue;
            }
            if (batch.status() == ProductionBatchStatus.CONFIRMED) {
                steps.addAll(confirmedSteps(batch));
                continue;
            }
            if (batch.status() != ProductionBatchStatus.DRAFT) {
                continue;
            }
            long locked = lockedInput(batch);
            steps.add(locked == 0
                    ? BrandStep.of(StepDomain.PRODUCTION, batch.productionBatchId(), batch.batchName(),
                            StepAction.CONFIRM,
                            "투입할 소재를 정할 차례입니다",
                            "아직 소재를 투입하지 않았다. add_production_inputs 에 이 배치 번호를 넘긴다",
                            "add_production_inputs")
                    : BrandStep.of(StepDomain.PRODUCTION, batch.productionBatchId(), batch.batchName(),
                            StepAction.CONFIRM,
                            "생산을 확정할 차례입니다. 되돌릴 수 없습니다.",
                            "소재 %,dg 이 투입돼 잠겨 있다. confirm_production 으로 확정하거나, 담당자가 "
                                    .formatted(locked)
                                    + "그만두겠다고 하면 cancel_production_batch 로 취소해 소재를 풀어 준다",
                            "confirm_production"));
        }
        return steps;
    }

    /**
     * 확정된 배치의 남은 일. <b>블록체인 기록 네 번을 먼저, 제품 정보는 그다음이다.</b>
     *
     * <p>예전에는 둘을 한꺼번에 담당자 차례로 적었다. 모델은 제품 등록을 먼저 골랐고, 판매가를 묻지 않아 0원으로
     * 등록했다. 서버는 그 제품의 판매 시작 카드를 띄웠고, 담당자가 취소해도 다시 띄워 결국 0원에 판매가 시작됐다.
     * 체인 기록이 남은 동안에는 그것 하나만 적는다.
     */
    private List<BrandStep> confirmedSteps(ProductionBatchResponse batch) {
        List<BrandStep> steps = new ArrayList<>();
        if (needsChainRecord(batch)) {
            steps.add(BrandStep.of(StepDomain.PRODUCTION, batch.productionBatchId(), batch.batchName(),
                    StepAction.SIGN,
                    "블록체인 기록이 남았습니다. 서명이 네 번 필요합니다.",
                    "확정은 끝났고 온체인 기록이 아직이다. record_production_chain 에 이 배치 번호를 "
                            + "넘긴다 — 서명 4번이 필요하고, 이것을 해야 제품 QR 에서 소재까지 이력이 이어진다. "
                            + "제품 등록은 네 번을 다 마친 뒤에 한다",
                    "record_production_chain"));
            return steps;
        }
        if (batch.productModelId() == null) {
            steps.add(BrandStep.of(StepDomain.PRODUCTION, batch.productionBatchId(), batch.batchName(),
                    StepAction.CONFIRM,
                    "판매할 제품 정보를 정할 차례입니다",
                    "블록체인 기록까지 끝났고 개체 %d개가 아직 어느 제품의 재고도 아니다 — 팔 수 없다. "
                            .formatted(batch.producedQuantity())
                            + "담당자에게 판매가를 묻고, 제품 이름과 설명은 이 대화의 사실로 지어 제안해 동의를 받은 뒤 "
                            + "register_product_model 에 batchId 로 이 번호를 넘긴다. 판매가를 듣기 전에는 등록하지 않는다",
                    "register_product_model"));
        }
        return steps;
    }

    /**
     * 온체인 기록이 <b>아직 남았는가.</b>
     *
     * <p><b>{@code chainBatchId} 가 채워진 것으로 끝났다고 보면 안 된다.</b> 그 값은 네 번의
     * 서명 중 첫 번째({@code createBatch})의 산물이다. 담당자가 1단계만 서명하고 지갑을
     * 닫으면 번호는 남고 기록은 없는데, 목록은 그 배치에 대해 아무 말도 하지 않는다 —
     * 배치 6이 그렇게 확정된 채로 멈춰 있었다. 확정은 소재를 DB 에서만 줄이므로, 그 상태로
     * 두면 체인에서는 같은 소재를 브랜드가 여전히 갖고 있다.
     *
     * <p>그래서 체인에 직접 묻는다. 판정 자체는 {@link ProductionChainProgress} 에 있다 —
     * 진행 막대도 같은 것을 읽어야 한다. 각자 판정하던 동안 막대는 "블록체인 기록 완료" 로,
     * 대화는 "서명이 남았습니다" 로 <b>한 화면에서 서로 다른 말을</b> 했다.
     */
    private boolean needsChainRecord(ProductionBatchResponse batch) {
        return chainRecord.isPending(batch.chainBatchId());
    }

    /**
     * 이 배치에 잠긴 투입량.
     *
     * <p><b>{@code totalUsedQuantity} 를 쓰면 안 된다.</b> 그 값은 확정 시점에 제품에 실제로
     * 쓰인 양으로 채워지고 그 전까지 언제나 0 이다. 그것으로 판정했다가 소재를 넣어 둔 배치를
     * "빈 배치" 로 분류해, 묶인 소재를 드러내려던 목록이 바로 그것을 감췄다.
     */
    private long lockedInput(ProductionBatchResponse batch) {
        if (batch.inputs() == null) {
            return 0L;
        }
        return batch.inputs().stream().mapToLong(ProductionInputResponse::inputQuantity).sum();
    }

    // ── 제품 ────────────────────────────────────────────────────────────

    private List<BrandStep> productSteps(Long userId, AgentProject project) {
        List<BrandStep> steps = new ArrayList<>();
        if (project.productModelIds().isEmpty()) {
            return steps;
        }
        for (ProductModelResponse model : productQuery.findMyProductModels(userId)) {
            if (!project.ownsProductModel(model.productModelId())) {
                continue;
            }
            // **0원이면 판매가부터.** 판매가를 묻지 않고 등록한 제품이 0원에 판매됐다. 판매 중이어도
            // 알린다 — 누구나 0원에 살 수 있는 상태다.
            if (model.price() <= 0
                    && (model.status() == ProductModelStatus.DRAFT || model.status() == ProductModelStatus.ON_SALE)) {
                steps.add(BrandStep.of(StepDomain.PRODUCT, model.productModelId(), model.name(),
                        StepAction.CONFIRM,
                        model.status() == ProductModelStatus.ON_SALE
                                ? "판매가가 0원인 채로 판매 중입니다"
                                : "판매가를 정할 차례입니다",
                        "판매가가 0원이다. 담당자에게 판매가를 묻고 update_product_model 로 고친다 — "
                                + "제품을 새로 등록하지 않는다. 판매 시작은 판매가를 고친 뒤에 한다",
                        "update_product_model"));
                continue;
            }
            if (model.status() != ProductModelStatus.DRAFT || model.stockQuantity() <= 0) {
                continue;
            }
            steps.add(BrandStep.of(StepDomain.PRODUCT, model.productModelId(), model.name(),
                    StepAction.CONFIRM,
                    "판매를 시작할 차례입니다",
                    "재고 %d개가 있는데 아직 판매 전이다. 담당자가 판매를 말하면 "
                            .formatted(model.stockQuantity())
                            + "set_product_on_sale 을 부른다 — 할 수 없는 일이라고 답하지 않는다",
                    "set_product_on_sale"));
        }
        return steps;
    }
}
