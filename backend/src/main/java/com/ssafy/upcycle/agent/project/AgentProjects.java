package com.ssafy.upcycle.agent.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentToolRegistry;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.dto.ProcessingOutputResponse;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputResponse;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.dto.TradeSearchCondition;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 대화의 프로젝트({@link AgentProject})를 만든다.
 *
 * <h2>따로 기록하지 않고 승인된 카드에서 읽는다</h2>
 *
 * <p>대화에서 생긴 일은 전부 승인된 카드를 거친다. 카드에는 인자와 실행 결과가 함께 남으므로
 * "이 대화가 무엇을 만들었나" 는 이미 저장돼 있다. 별도 표를 두면 카드와 표 두 곳에 쓰게 되고,
 * 한쪽이 빠지는 순간 둘이 어긋난다. 기능을 넣기 전에 시작한 대화도 그대로 읽힌다.
 *
 * <h2>소재는 따라간다</h2>
 *
 * <p>카드가 직접 말해 주는 것은 거래·가공·배치·제품 번호다. 소재는 거기서 따라 나온다 —
 * 산 거래의 소재, 맡긴 가공의 투입 소재와 결과물, 배치에 넣은 소재. 가공 결과물은 승인한 뒤에
 * 새 번호로 생기므로 카드만 봐서는 알 수 없고, 작업을 읽어야 한다.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> 20초마다 도는 자동 카드 경로({@code BrandStepAutoProposer})
 * 에서도 불리는데, 거기에는 바깥 트랜잭션이 없다. 조회마다 각 서비스의 짧은 트랜잭션을 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentProjects {

    /**
     * 소재를 따라가려고 읽을 구매 거래 수. 프로젝트 거래가 이보다 오래되면 그 소재는 빠진다 —
     * 대화 하나가 제품 하나라 이 폭을 넘을 일은 없다고 보고, 목록 조회 한 번으로 끝낸다.
     */
    private static final int TRADE_WINDOW = 100;

    private final AgentPendingActionRepository pendingActions;
    private final AgentToolRegistry registry;
    private final ObjectMapper objectMapper;
    private final TradeQueryService tradeQuery;
    private final ProcessingJobWorkflowService jobWorkflow;
    private final ProductionBatchService batchService;

    /**
     * 카드가 직접 말해 주는 것만. 도메인을 읽지 않으므로 가볍다 — 화면에 거래 번호만 넘길 때
     * 쓴다.
     */
    public AgentProject claimed(Long conversationId) {
        return claims(conversationId).build();
    }

    /** 소재·제품까지 따라간 프로젝트. 대화가 먼저 꺼낼 것을 고를 때 쓴다. */
    public AgentProject of(Long userId, Long conversationId) {
        AgentProject.Builder project = claims(conversationId);
        AgentProject claimed = project.build();
        if (claimed.isEmpty()) {
            return claimed;
        }
        followTrades(userId, claimed, project);
        followJobs(userId, claimed, project);
        followBatches(userId, claimed, project);
        return project.build();
    }

    /**
     * 카드 한 장이 만든 것. 승인 직후, 이 카드가 프로젝트를 다음 단계로 들였는지 볼 때 쓴다
     * ({@code AgentActionStore.apply}).
     */
    public AgentProject claimedBy(String toolName, JsonNode arguments, String resultJson) {
        AgentProject.Builder project = new AgentProject.Builder();
        try {
            registry.find(toolName).ifPresent(tool -> tool.claim(arguments, read(resultJson), project));
        } catch (RuntimeException unreadable) {
            // 승인 트랜잭션 안에서 불린다. 여기서 터지면 이미 끝난 승인까지 되돌아간다 —
            // 단계를 못 옮기는 편이 훨씬 낫다.
            log.warn("Agent project claim unreadable on approval: tool={} reason={}",
                    toolName, unreadable.getClass().getSimpleName());
        }
        return project.build();
    }

    /** 이미 읽어 둔 카드에서 고른다. 대화 화면을 만들 때 카드를 한 번 더 읽지 않으려는 것이다. */
    public AgentProject claimedFrom(List<AgentPendingAction> actions) {
        return claims(actions.stream()
                        .filter(action -> action.getStatus() == PendingActionStatus.COMPLETED)
                        .toList())
                .build();
    }

    private AgentProject.Builder claims(Long conversationId) {
        return claims(pendingActions.findByConversationIdAndStatus(
                conversationId, PendingActionStatus.COMPLETED));
    }

    private AgentProject.Builder claims(List<AgentPendingAction> completed) {
        AgentProject.Builder project = new AgentProject.Builder();
        for (AgentPendingAction action : completed) {
            registry.find(action.getToolName()).ifPresent(tool -> {
                try {
                    tool.claim(read(action.getPayloadJson()), read(action.getResultJson()), project);
                } catch (RuntimeException unreadable) {
                    // 카드 하나를 못 읽었다고 프로젝트 전체를 잃지 않는다.
                    log.warn(
                            "Agent project claim skipped: actionId={} tool={} reason={}",
                            action.getId(),
                            action.getToolName(),
                            unreadable.getClass().getSimpleName());
                }
            });
        }
        return project;
    }

    /** 산 거래의 소재. 수령하면 같은 소재 번호의 구매자 몫이 늘어난다({@code TradeReceiptService}). */
    private void followTrades(Long userId, AgentProject claimed, AgentProject.Builder project) {
        if (claimed.tradeIds().isEmpty()) {
            return;
        }
        guarded("trades", () -> {
            var page = tradeQuery.findMyTrades(
                    userId,
                    new TradeSearchCondition(TradeSearchCondition.TradeSide.BUY, null, 0, TRADE_WINDOW));
            for (TradeResponse trade : page.content()) {
                if (claimed.ownsTrade(trade.tradeId())) {
                    project.material(trade.materialId());
                }
            }
        });
    }

    /** 맡긴 가공의 투입 소재와 결과물. 결과물은 승인 뒤 새 번호로 생긴다. */
    private void followJobs(Long userId, AgentProject claimed, AgentProject.Builder project) {
        if (claimed.jobIds().isEmpty()) {
            return;
        }
        guarded("jobs", () -> {
            for (ProcessingJobResponse job : jobWorkflow.list(userId)) {
                if (!claimed.ownsJob(job.jobId())) {
                    continue;
                }
                project.material(job.inputMaterialId());
                if (job.outputs() != null) {
                    for (ProcessingOutputResponse output : job.outputs()) {
                        project.material(output.materialId());
                    }
                }
            }
        });
    }

    /**
     * 배치에 넣은 소재와 배치에 연결된 제품. 홈페이지에서 제품을 연결했더라도 이 배치로 만든
     * 제품이면 판매까지 이 프로젝트가 이끈다.
     */
    private void followBatches(Long userId, AgentProject claimed, AgentProject.Builder project) {
        if (claimed.batchIds().isEmpty()) {
            return;
        }
        guarded("batches", () -> {
            for (ProductionBatchResponse batch : batchService.findMyBatches(userId)) {
                if (!claimed.ownsBatch(batch.productionBatchId())) {
                    continue;
                }
                project.productModel(batch.productModelId());
                if (batch.inputs() != null) {
                    for (ProductionInputResponse input : batch.inputs()) {
                        project.material(input.materialId());
                    }
                }
            }
        });
    }

    private void guarded(String area, Runnable follow) {
        try {
            follow.run();
        } catch (RuntimeException failure) {
            // 한 갈래를 못 따라가도 카드가 말해 준 번호는 남는다.
            log.warn(
                    "Agent project follow failed for {}: reason={} message={}",
                    area,
                    failure.getClass().getSimpleName(),
                    failure.getMessage());
        }
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception malformed) {
            return objectMapper.createObjectNode();
        }
    }
}
