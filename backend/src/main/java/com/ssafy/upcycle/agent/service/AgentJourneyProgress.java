package com.ssafy.upcycle.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.agent.dto.JourneyProgressResponse;
import com.ssafy.upcycle.agent.service.step.ProductionChainProgress;
import com.ssafy.upcycle.agent.dto.JourneyProgressResponse.Mark;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.project.AgentProjects;
import com.ssafy.upcycle.agent.service.step.BrandStep;
import com.ssafy.upcycle.agent.service.step.BrandStepResolver;
import com.ssafy.upcycle.agent.service.step.StepDomain;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.agent.tool.processing.ProcessLabels;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * <h2>이 대화의 프로젝트만 그린다</h2>
 *
 * <p>칸마다 따라갈 대상은 이 대화가 시작한 일에서 고른다({@link AgentProject}). 예전에는 서버가
 * 어느 거래·배치가 이 대화의 것인지 몰라 대리값을 썼다 — "대화 시작 이후에 만든 거래", "가장
 * 최근 배치", "첫 번째 제품". 그래서 대화를 켜 둔 채 홈페이지에서 산 소재가 막대를 차지하거나,
 * 지난 테스트의 배치를 따라가는 일이 있었다.
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
    private final ProductionChainProgress chainRecord;
    private final ObjectMapper objectMapper;
    private final AgentProjects projects;
    private final BrandStepResolver resolver;
    private final AgentSpending spending;

    /**
     * 이 대화의 경과.
     *
     * <p>어느 조회가 흔들려도 <b>빈 막대를 돌려준다.</b> 경과 표시가 안 보이는 것은 불편이지만
     * 대화창이 통째로 열리지 않는 것은 장애다.
     */
    @Transactional(readOnly = true)
    public JourneyProgressResponse of(Long userId, AgentConversation conversation) {
        AgentProject project = projects.of(userId, conversation.getId());
        return of(userId, conversation, project, resolver.resolve(userId, project));
    }

    /**
     * 이미 판정한 남은 일로 그린다. 요청 경로는 이것을 쓴다 — 자동 카드와 같은 판정을 한 번만
     * 하려는 것이다({@code AgentConversationService.progress}).
     *
     * @param steps 이 프로젝트에서 남은 일. 막대의 할 일 문장과 오른쪽 패널이 여기서 나온다
     */
    @Transactional(readOnly = true)
    public JourneyProgressResponse of(
            Long userId, AgentConversation conversation, AgentProject project, List<BrandStep> steps) {
        JourneyPhase phase = conversation.getPhase();
        List<Mark> journey = journeyOf(phase);
        JourneyProgressResponse bar;
        try {
            bar = switch (phase) {
                case MATERIAL_SOURCING -> sourcing(userId, project, steps, journey, phase);
                case PROCESSING -> processing(userId, conversation, project, steps, journey, phase);
                case PRODUCTION -> production(userId, project, steps, journey, phase);
                case PRODUCT -> product(userId, project, steps, journey, phase);
                case INTAKE, DONE -> JourneyProgressResponse.empty(phase, journey);
            };
        } catch (RuntimeException failure) {
            log.warn("Journey progress failed: phase={} reason={}", phase, failure.toString());
            bar = JourneyProgressResponse.empty(phase, journey);
        }
        return bar.withTasks(steps.stream()
                        .map(step -> new JourneyProgressResponse.Task(
                                step.domain().name(),
                                step.referenceId(),
                                step.subject(),
                                step.label(),
                                step.isActionable()))
                        .toList())
                .withSpending(spendingOf(userId, project));
    }

    /** 결제한 금액. 읽다 실패해도 막대는 그린다 — 금액 칸이 빌 뿐이다. */
    private List<JourneyProgressResponse.Spend> spendingOf(Long userId, AgentProject project) {
        try {
            return spending.of(userId, project);
        } catch (RuntimeException failure) {
            log.warn("Journey spending failed: reason={}", failure.toString());
            return List.of();
        }
    }

    /**
     * 이 대상에 남은 일의 문장. <b>모델이 읽는 목록과 같은 판정</b>에서 가져온다 — 막대가 따로
     * 판정하던 동안, 체인에 수령 서명이 빠진 거래를 두고 막대는 "수령이 끝났습니다" 를,
     * 대화는 "서명이 남았습니다" 를 말했다.
     */
    private Optional<String> todoFor(List<BrandStep> steps, StepDomain domain, Long referenceId) {
        if (referenceId == null) {
            return Optional.empty();
        }
        return steps.stream()
                .filter(step -> step.domain() == domain && step.referenceId() == referenceId)
                .map(BrandStep::label)
                .findFirst();
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

    private JourneyProgressResponse sourcing(
            Long userId,
            AgentProject project,
            List<BrandStep> pending,
            List<Mark> journey,
            JourneyPhase phase) {
        TradeResponse trade = currentTrade(userId, project);
        if (trade == null) {
            // 빈 막대만 두면 담당자는 지금 무엇을 해야 하는지 알 수 없다. 이 단계에서 아직
            // 아무 거래도 없다는 것은 "소재를 고를 차례" 라는 뜻이다.
            return new JourneyProgressResponse(
                    phase,
                    journey,
                    null,
                    null,
                    List.of(),
                    "어떤 소재가 얼마나 필요한지 말씀해 주시면 판매 글을 찾아 드리겠습니다.");
        }

        List<Mark> steps = new ArrayList<>();
        steps.add(Mark.done("주문"));
        steps.add(mark("결제", trade.paidAt() != null));
        // 온체인 기록은 선택이다 — 판매 글이 체인에 없으면 이 칸은 영영 채워지지 않는다.
        steps.add(mark("온체인", trade.chainTradeId() != null));
        steps.add(mark("발송", trade.shippedAt() != null));
        steps.add(mark("수령", trade.receivedAt() != null));
        markCurrent(steps);

        // 남은 일이 있으면 그 문장을, 없으면 이 거래는 끝난 것이다.
        String todo = todoFor(pending, StepDomain.TRADE, trade.tradeId())
                .orElse(trade.receivedAt() != null
                        ? "수령이 끝났습니다. 가공이나 생산으로 넘어갈 수 있습니다."
                        : null);

        return new JourneyProgressResponse(
                phase,
                journey,
                trade.materialName(),
                "%s · %,d원".formatted(Quantities.text(trade.quantity()), trade.totalPrice()),
                steps,
                todo);
    }

    /**
     * 막대에 보일 거래 — 이 프로젝트의 거래 중 <b>아직 수령하지 않은 것</b>, 없으면 가장 최근 것.
     *
     * <p>예전에는 "이 대화가 시작된 뒤에 만든 거래" 를 이 대화의 거래로 추정했고, 없으면 끝나지
     * 않은 지난 거래를 보였다. 그래서 대화를 켜 둔 채 홈페이지에서 산 소재가 막대를 차지했고,
     * 새 대화를 열면 지난 대화의 거래가 떴다. 이제는 이 대화에서 산 거래만 본다.
     *
     * <p>소재를 여러 건 사면 남은 일이 있는 쪽을 보인다. 끝난 거래를 앞에 두면 담당자는
     * 아직 오는 중인 소재를 놓친다.
     */
    private TradeResponse currentTrade(Long userId, AgentProject project) {
        if (project.tradeIds().isEmpty()) {
            return null;
        }
        var page = tradeQuery.findMyTrades(
                userId, new TradeSearchCondition(TradeSearchCondition.TradeSide.BUY, null, 0, 100));
        List<TradeResponse> mine = page.content().stream()
                .filter(trade -> project.ownsTrade(trade.tradeId()))
                .filter(trade -> trade.status() != TradeStatus.CANCELED)
                .sorted(Comparator.comparing(TradeResponse::tradeId).reversed())
                .toList();

        return mine.stream()
                .filter(trade -> trade.receivedAt() == null)
                .findFirst()
                .or(() -> mine.stream().findFirst())
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
            Long userId,
            AgentConversation conversation,
            AgentProject project,
            List<BrandStep> pending,
            List<Mark> journey,
            JourneyPhase phase) {

        ProcessingJobResponse running = project.jobIds().isEmpty()
                ? null
                : jobWorkflow.list(userId).stream()
                        .filter(job -> project.ownsJob(job.jobId()))
                        .filter(job -> !ProcessingTodo.of(job.status()).isClosed())
                        .max(Comparator.comparing(ProcessingJobResponse::jobId))
                        .orElse(null);

        MyMaterialResponse material = subjectMaterial(userId, project, running);
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
            todo = "작업 %d: %s".formatted(
                    running.jobId(),
                    todoFor(pending, StepDomain.PROCESSING, running.jobId())
                            .orElse(ProcessingTodo.of(running.status()).label()));
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
                "소재 %d · %s".formatted(material.materialId(), Quantities.text(material.available())),
                steps,
                todo);
    }

    /**
     * 지금 따라갈 소재 — 이 프로젝트의 소재에서 고른다.
     *
     * <p>진행 중인 작업이 있으면 그 투입 소재다. 없으면 <b>필수 공정이 남은 프로젝트 소재</b> 중
     * 최신 — 가공을 마치면 소재가 새로 생기므로, 방금 승인해 받은 결과물이 여기 잡힌다.
     */
    private MyMaterialResponse subjectMaterial(
            Long userId, AgentProject project, ProcessingJobResponse running) {
        if (project.materialIds().isEmpty()) {
            return null;
        }
        List<MyMaterialResponse> mine = materialQuery.findMyMaterials(userId).stream()
                .filter(material -> project.ownsMaterial(material.materialId()))
                .sorted(Comparator.comparing(MyMaterialResponse::materialId).reversed())
                .toList();
        return pickSubject(mine, running == null ? null : running.inputMaterialId());
    }

    /**
     * 따라갈 소재를 고른다. 진행 중인 작업이 있으면 그 투입 소재, 없으면 필수 공정이 남은 소재, 그것도 없으면 가장
     * 최근 소재다.
     *
     * <p><b>다 쓴 소재는 따라가지 않는다.</b> 가공을 마치면 투입했던 원래 소재가 0kg 으로 목록에 남는다. 예전에는
     * 그것이 "필수 공정이 남은 소재" 로 잡혀, 결과물(세척·해체·선별 완료)이 있는데도 막대의 체크가 끝까지 켜지지
     * 않았다. 진행 중인 작업의 투입 소재는 잠겨 있어 사용 가능량이 0 일 수 있으므로 이 규칙에서 뺀다.
     *
     * @param newestFirst 이 프로젝트의 소재, 최신순
     * @param runningInputId 진행 중인 가공 작업의 투입 소재. 없으면 null
     */
    public static MyMaterialResponse pickSubject(List<MyMaterialResponse> newestFirst, Long runningInputId) {
        if (runningInputId != null) {
            var byJob = newestFirst.stream()
                    // 둘 다 Long 이다. == 는 값이 아니라 객체를 비교해 id 가 127 을 넘으면 어긋난다.
                    .filter(m -> Objects.equals(m.materialId(), runningInputId))
                    .findFirst();
            if (byJob.isPresent()) {
                return byJob.get();
            }
        }
        List<MyMaterialResponse> held = newestFirst.stream().filter(m -> m.available() > 0).toList();
        return held.stream()
                .filter(m -> !RequiredProcessing.satisfied(
                        m.rootOrigin(),
                        m.completedProcessTypes() == null ? List.of() : m.completedProcessTypes()))
                .findFirst()
                .or(() -> held.stream().findFirst())
                .orElse(newestFirst.isEmpty() ? null : newestFirst.getFirst());
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
        return ProcessLabels.text(type);
    }

    // ── 생산 ────────────────────────────────────────────────────────────

    private JourneyProgressResponse production(
            Long userId,
            AgentProject project,
            List<BrandStep> pending,
            List<Mark> journey,
            JourneyPhase phase) {
        List<ProductionBatchResponse> batches = project.batchIds().isEmpty()
                ? List.of()
                : batchService.findMyBatches(userId).stream()
                        .filter(batch -> project.ownsBatch(batch.productionBatchId()))
                        .filter(batch -> batch.status() != ProductionBatchStatus.CANCELED)
                        .toList();
        if (batches.isEmpty()) {
            return JourneyProgressResponse.empty(phase, journey);
        }
        // **이 프로젝트의 배치 중 가장 최근 것을 본다.** 예전에는 계정 전체에서 골라, 지난
        // 테스트에서 버려 둔 빈 배치를 따라가며 다 끝냈는데도 "투입할 소재를 정할 차례입니다"
        // 라고 말했다. 이제 후보가 이 대화의 배치뿐이라 그런 대리값이 필요 없다.
        ProductionBatchResponse batch = batches.stream()
                .max(Comparator.comparing(ProductionBatchResponse::productionBatchId))
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
        // 체인은 네 번 서명하지만 막대에서는 한 칸이다. 네 칸을 그리면 담당자가 세어야 할
        // 것만 늘고, 어차피 진행은 카드 안의 패널이 보여 준다.
        //
        // **chainBatchId 로 칠하지 않는다.** 그 값은 1단계의 산물이라, 첫 서명만 하고 멈춘
        // 배치를 완료로 칠한다. 실제로 배치 6에서 막대는 "기록 완료" 를, 같은 화면의 대화는
        // "서명이 남았습니다" 를 동시에 보여줬다 — 담당자는 어느 쪽이 맞는지 알 수 없다.
        steps.add(mark(
                "블록체인 기록",
                chainRecord.isRecorded(batch.chainBatchId())));
        markCurrent(steps);

        // 배치 하나에 남은 일이 둘일 수 있다(체인 기록, 제품 연결). 판정 목록의 순서대로 앞의
        // 것을 보인다 — 체인 기록을 먼저 해야 제품 QR 에서 소재까지 이력이 이어진다.
        String todo = todoFor(pending, StepDomain.PRODUCTION, batch.productionBatchId())
                .orElse(confirmed ? "생산이 끝났습니다. 판매를 시작할 차례입니다." : null);

        return new JourneyProgressResponse(
                phase,
                journey,
                batch.batchName(),
                "%d개 · 업사이클 %s%%".formatted(batch.producedQuantity(), batch.upcycleRatio()),
                steps,
                todo);
    }

    // ── 판매 ────────────────────────────────────────────────────────────

    private JourneyProgressResponse product(
            Long userId,
            AgentProject project,
            List<BrandStep> pending,
            List<Mark> journey,
            JourneyPhase phase) {
        ProductModelResponse model = project.productModelIds().isEmpty()
                ? null
                : productQuery.findMyProductModels(userId).stream()
                        .filter(candidate -> project.ownsProductModel(candidate.productModelId()))
                        .max(Comparator.comparing(ProductModelResponse::productModelId))
                        .orElse(null);
        if (model == null) {
            return JourneyProgressResponse.empty(phase, journey);
        }

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
                todoFor(pending, StepDomain.PRODUCT, model.productModelId())
                        .orElse(onSale
                                ? "판매 중입니다. 들어온 주문은 대화에서 확인할 수 있습니다."
                                : "재고가 없어 아직 판매를 시작할 수 없습니다. 만든 제품을 이 제품에 연결하면 "
                                        + "재고가 생깁니다."));
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
