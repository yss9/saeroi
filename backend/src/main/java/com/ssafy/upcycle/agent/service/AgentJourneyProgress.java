package com.ssafy.upcycle.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.dto.JourneyProgressResponse;
import com.ssafy.upcycle.agent.dto.JourneyProgressResponse.Mark;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.RequiredProcessing;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화 위 경과 표시를 계산한다.
 *
 * <p>{@link AgentPendingWork} 와 같은 자리에 있다 — 대화를 걸지 않고 도메인만 읽으므로
 * 크레딧이 들지 않는다. 다른 점은 이쪽이 <b>화면에 그릴 모양</b>을 만든다는 것이다.
 *
 * <h2>단계마다 기준이 다르다</h2>
 *
 * <pre>
 *   소재   거래 하나        주문 · 결제 · 온체인 · 발송 · 수령
 *   가공   공정 목록        담당자가 하기로 한 공정 + 남은 필수 공정
 *   생산   배치 하나        열기 · 투입 · 확정 · 체인
 *   판매   제품 하나        등록 · 판매 시작 · 주문
 * </pre>
 *
 * <p><b>가공만 기준이 다른 것이 핵심이다.</b> 나머지는 따라갈 대상이 하나지만, 가공은 업체
 * 한 곳이 모든 공정을 하지는 않아 <b>작업이 여러 번</b> 돌고 그때마다 소재가 새로 생긴다.
 * 작업을 기준으로 그리면 세 번 가공할 때 막대가 세 번 처음으로 돌아간다. 담당자가 알고 싶은
 * 것은 "작업 #12 가 어디까지 왔나" 가 아니라 "이 소재가 제품이 될 준비가 됐나" 다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentJourneyProgress {

    private static final List<JourneyPhase> JOURNEY = List.of(
            JourneyPhase.MATERIAL_SOURCING,
            JourneyPhase.PROCESSING,
            JourneyPhase.PRODUCTION,
            JourneyPhase.PRODUCT);

    private static final List<String> JOURNEY_LABELS = List.of("소재", "가공", "생산", "판매");

    private final TradeQueryService tradeQuery;
    private final ProcessingJobWorkflowService jobWorkflow;
    private final MaterialQueryService materialQuery;
    private final ProductionBatchService batchService;
    private final ProductModelQueryService productQuery;
    private final ObjectMapper objectMapper;

    /**
     * 이 대화의 경과.
     *
     * <p>어느 조회가 흔들려도 <b>빈 막대를 돌려준다.</b> 경과 표시가 안 보이는 것은 불편이지만
     * 대화창이 통째로 열리지 않는 것은 장애다.
     */
    @Transactional(readOnly = true)
    public JourneyProgressResponse of(Long userId, AgentConversation conversation) {
        JourneyPhase phase = conversation.getPhase();
        List<Mark> journey = journeyOf(phase);
        try {
            return switch (phase) {
                case MATERIAL_SOURCING -> sourcing(userId, journey, phase);
                case PROCESSING -> processing(userId, conversation, journey, phase);
                case PRODUCTION -> production(userId, journey, phase);
                case PRODUCT -> product(userId, journey, phase);
                case INTAKE, DONE -> JourneyProgressResponse.empty(phase, journey);
            };
        } catch (RuntimeException failure) {
            log.warn("Journey progress failed: phase={} reason={}", phase, failure.toString());
            return JourneyProgressResponse.empty(phase, journey);
        }
    }

    /** 여정 네 칸. 지난 단계는 완료, 지금 단계는 current. */
    private List<Mark> journeyOf(JourneyPhase phase) {
        int index = JOURNEY.indexOf(phase);
        // INTAKE 는 아직 여정에 들어오지 않았고, DONE 은 전부 지난 것이다.
        int position = phase == JourneyPhase.DONE ? JOURNEY.size() : index;

        List<Mark> marks = new ArrayList<>();
        for (int i = 0; i < JOURNEY.size(); i++) {
            marks.add(new Mark(JOURNEY_LABELS.get(i), position > i, position == i));
        }
        return marks;
    }

    // ── 소재 ────────────────────────────────────────────────────────────

    private JourneyProgressResponse sourcing(Long userId, List<Mark> journey, JourneyPhase phase) {
        TradeResponse trade = currentTrade(userId);
        if (trade == null) {
            return JourneyProgressResponse.empty(phase, journey);
        }

        List<Mark> steps = new ArrayList<>();
        steps.add(Mark.done("주문"));
        steps.add(mark("결제", trade.paidAt() != null));
        // 온체인 기록은 선택이다 — 판매 글이 체인에 없으면 이 칸은 영영 채워지지 않는다.
        steps.add(mark("온체인", trade.chainTradeId() != null));
        steps.add(mark("발송", trade.shippedAt() != null));
        steps.add(mark("수령", trade.receivedAt() != null));
        markCurrent(steps);

        String todo;
        if (trade.status() == TradeStatus.PAID && trade.chainTradeId() == null) {
            todo = "결제가 끝났습니다. 온체인 기록에 서명할 차례입니다.";
        } else if (trade.receivedAt() != null) {
            todo = "수령이 끝났습니다. 가공이나 생산으로 넘어갈 수 있습니다.";
        } else if (trade.shippedAt() != null) {
            todo = "물건이 오는 중입니다. 받으면 수령을 확정해 주세요.";
        } else if (trade.paidAt() != null) {
            todo = "판매자가 발송하기를 기다리는 중입니다. 지금 하실 일은 없습니다.";
        } else {
            todo = "결제할 차례입니다.";
        }

        return new JourneyProgressResponse(
                phase,
                journey,
                trade.materialName(),
                "%,dkg · %,d원".formatted(trade.quantity() / 1000, trade.totalPrice()),
                steps,
                todo);
    }

    private TradeResponse currentTrade(Long userId) {
        var page = tradeQuery.findMyTrades(
                userId, new TradeSearchCondition(TradeSearchCondition.TradeSide.BUY, null, 0, 10));
        return page.content().stream()
                .filter(trade -> trade.status() != TradeStatus.CANCELED)
                .findFirst()
                .orElse(null);
    }

    // ── 가공 ────────────────────────────────────────────────────────────

    /**
     * 공정을 기준으로 그린다.
     *
     * <p>칸은 <b>담당자가 하기로 한 공정과 아직 남은 필수 공정의 합집합</b>이다. 계획에만
     * 맞추면 막대가 다 찼는데 생산 확정에서 막히고, 필수만 보면 담당자가 추가로 맡긴
     * 재단·염색이 보이지 않아 "다 끝났다" 로 읽힌다.
     */
    private JourneyProgressResponse processing(
            Long userId, AgentConversation conversation, List<Mark> journey, JourneyPhase phase) {

        List<ProcessingJobResponse> jobs = jobWorkflow.list(userId);
        ProcessingJobResponse running = jobs.stream()
                .filter(job -> !ProcessingTodo.of(job.status()).isClosed())
                .findFirst()
                .orElse(null);

        MyMaterialResponse material = subjectMaterial(userId, running);
        if (material == null) {
            return JourneyProgressResponse.empty(phase, journey);
        }

        List<ProcessType> completed = material.completedProcessTypes() == null
                ? List.of()
                : material.completedProcessTypes();

        Set<ProcessType> planned = new LinkedHashSet<>(plannedProcessTypes(conversation));
        planned.addAll(completed);
        planned.addAll(RequiredProcessing.missing(material.rootOrigin(), completed));

        List<Mark> steps = new ArrayList<>();
        for (ProcessType type : planned) {
            steps.add(mark(label(type), completed.contains(type)));
        }
        markCurrent(steps);

        List<ProcessType> missingRequired =
                RequiredProcessing.missing(material.rootOrigin(), completed);
        String todo;
        if (running != null) {
            todo = "작업 %d: %s"
                    .formatted(running.jobId(), ProcessingTodo.of(running.status()).label());
        } else if (!missingRequired.isEmpty()) {
            todo = "필수 공정 %s 이(가) 남았습니다. 업체를 찾아 맡겨야 합니다."
                    .formatted(missingRequired);
        } else {
            todo = "필수 공정이 끝났습니다. 생산으로 넘어갈 수 있습니다.";
        }

        return new JourneyProgressResponse(
                phase,
                journey,
                material.name(),
                "소재 %d · %,dkg".formatted(material.materialId(), material.available() / 1000),
                steps,
                todo);
    }

    /**
     * 지금 따라갈 소재.
     *
     * <p>진행 중인 작업이 있으면 그 투입 소재다. 없으면 <b>필수 공정이 남은 보유 소재</b> 중
     * 최신 — 가공을 마치면 소재가 새로 생기므로, 방금 승인해 받은 결과물이 여기 잡힌다.
     */
    private MyMaterialResponse subjectMaterial(Long userId, ProcessingJobResponse running) {
        List<MyMaterialResponse> mine = materialQuery.findMyMaterials(userId);
        if (running != null) {
            var byJob = mine.stream()
                    .filter(m -> m.materialId() == running.inputMaterialId())
                    .findFirst();
            if (byJob.isPresent()) {
                return byJob.get();
            }
        }
        return mine.stream()
                .filter(m -> !RequiredProcessing.satisfied(
                        m.rootOrigin(),
                        m.completedProcessTypes() == null ? List.of() : m.completedProcessTypes()))
                .findFirst()
                .orElse(mine.isEmpty() ? null : mine.getFirst());
    }

    /** 담당자가 하기로 한 공정. 요구사항에 없으면 빈 목록이다. */
    private List<ProcessType> plannedProcessTypes(AgentConversation conversation) {
        String requirement = conversation.getRequirementJson();
        if (requirement == null || requirement.isBlank()) {
            return List.of();
        }
        try {
            JsonNode types = objectMapper.readTree(requirement).path("processTypes");
            if (!types.isArray()) {
                return List.of();
            }
            List<ProcessType> planned = new ArrayList<>();
            for (JsonNode node : types) {
                try {
                    planned.add(ProcessType.valueOf(node.asText()));
                } catch (IllegalArgumentException unknown) {
                    // 모델이 만든 값이라 우리 enum 에 없을 수 있다. 막대 하나 빠지는 것으로 끝낸다.
                }
            }
            return planned;
        } catch (Exception malformed) {
            return List.of();
        }
    }

    private String label(ProcessType type) {
        return switch (type) {
            case CLEANING -> "세척";
            case DISASSEMBLY -> "해체";
            case SORTING -> "선별";
            case CUTTING -> "재단";
            case SHREDDING -> "파쇄";
            case COLOR_PROCESSING -> "염색";
            case SURFACE_FINISHING -> "표면처리";
            case REMANUFACTURING -> "재제조";
        };
    }

    // ── 생산 ────────────────────────────────────────────────────────────

    private JourneyProgressResponse production(Long userId, List<Mark> journey, JourneyPhase phase) {
        List<ProductionBatchResponse> batches = batchService.findMyBatches(userId);
        if (batches.isEmpty()) {
            return JourneyProgressResponse.empty(phase, journey);
        }
        // **가장 최근에 만든 배치를 본다.** 예전에는 getFirst() 였는데 그것은 가장 오래된
        // 배치다. 지난 테스트에서 버려 둔 빈 배치가 목록 앞에 남아 있으면, 방금 확정하고
        // 체인 기록까지 끝낸 배치가 아니라 그 빈 배치를 따라간다 — 다 끝냈는데 막대는
        // "투입할 소재를 정할 차례입니다" 라고 말했다.
        //
        // 진행 중인 것을 고르는 규칙("확정 안 된 것 우선")도 같은 이유로 틀린다. 버려진
        // 배치가 영원히 미확정이라 늘 그쪽이 뽑힌다. 대화가 다루는 배치를 서버가 알 방법이
        // 없으므로, 가장 최근에 만든 것을 그 대리값으로 쓴다.
        ProductionBatchResponse batch = batches.stream()
                .max(java.util.Comparator.comparing(ProductionBatchResponse::productionBatchId))
                .orElseThrow();

        boolean confirmed = batch.status() == ProductionBatchStatus.CONFIRMED;
        // **투입 여부를 batch.totalUsedQuantity() 로 보지 않는다.** 그 값은 확정 시점에 제품에
        // 실제로 쓰인 양으로 채워지고 그 전까지 언제나 0 이다 (ProductionBatch.confirm).
        // 그대로 쓰면 소재를 넣어 둔 배치에도 "투입할 소재를 정할 차례입니다" 가 떠서, 담당자는
        // 방금 넣은 것이 사라진 줄 안다. 화면 쪽은 같은 함정을 이미 피하고 있다
        // (ProductionBatchDetail.tsx 주석).
        long inputTotal = batch.inputs() == null
                ? 0L
                : batch.inputs().stream().mapToLong(ProductionInputResponse::inputQuantity).sum();

        List<Mark> steps = new ArrayList<>();
        steps.add(Mark.done("배치"));
        steps.add(mark("소재 투입", inputTotal > 0));
        steps.add(mark("확정", confirmed));
        steps.add(mark("제품 연결", batch.productModelId() != null));
        // 체인은 네 번 서명하지만 막대에서는 한 칸이다. 서버가 아는 것은 1단계의 산물인
        // chainBatchId 뿐이라 여기서 네 칸을 그리면 2~4단계를 늘 미완으로 보여주게 된다.
        steps.add(mark("블록체인 기록", batch.chainBatchId() != null));
        markCurrent(steps);

        String todo;
        if (!confirmed && inputTotal == 0) {
            todo = "투입할 소재를 정할 차례입니다.";
        } else if (!confirmed) {
            todo = "생산을 확정할 차례입니다. 되돌릴 수 없습니다.";
        } else if (batch.chainBatchId() == null) {
            todo = "블록체인 기록이 남았습니다. 서명이 네 번 필요합니다.";
        } else if (batch.productModelId() == null) {
            todo = "만든 제품을 등록해 이 생산에 연결할 차례입니다.";
        } else {
            todo = "생산이 끝났습니다. 판매를 시작할 차례입니다.";
        }

        return new JourneyProgressResponse(
                phase,
                journey,
                batch.batchName(),
                "%d개 · 업사이클 %s%%".formatted(batch.producedQuantity(), batch.upcycleRatio()),
                steps,
                todo);
    }

    // ── 판매 ────────────────────────────────────────────────────────────

    private JourneyProgressResponse product(Long userId, List<Mark> journey, JourneyPhase phase) {
        List<ProductModelResponse> models = productQuery.findMyProductModels(userId);
        if (models.isEmpty()) {
            return JourneyProgressResponse.empty(phase, journey);
        }
        ProductModelResponse model = models.getFirst();

        boolean onSale = "ON_SALE".equals(String.valueOf(model.status()));
        List<Mark> steps = new ArrayList<>();
        steps.add(Mark.done("등록"));
        steps.add(mark("판매 시작", onSale));
        markCurrent(steps);

        return new JourneyProgressResponse(
                phase,
                journey,
                model.name(),
                "%,d원".formatted(model.price()),
                steps,
                onSale
                        ? "판매 중입니다. 들어온 주문은 대화에서 확인할 수 있습니다."
                        : "판매를 시작할 차례입니다. 재고가 있어야 합니다.");
    }

    // ── 공통 ────────────────────────────────────────────────────────────

    private Mark mark(String label, boolean done) {
        return new Mark(label, done, false);
    }

    /** 끝나지 않은 첫 칸이 지금 칸이다. 전부 끝났으면 현재 칸은 없다. */
    private void markCurrent(List<Mark> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (!steps.get(i).done()) {
                steps.set(i, new Mark(steps.get(i).label(), false, true));
                return;
            }
        }
    }
}
