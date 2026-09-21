package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 확정된 생산을 블록체인에 기록한다. <b>이력 사슬의 마지막 칸이다.</b>
 *
 * <p>소재 구매와 가공은 각각 서명으로 체인에 남는데 생산만 빠져 있었다. 그러면 소비자가
 * QR 을 찍었을 때 이력이 <b>제품 직전에서 끊긴다</b> — 어떤 소재가 이 제품이 됐는지가
 * 체인에 없기 때문이다. 업사이클 이력이 이 플랫폼의 값인데 마지막 연결이 비어 있었다.
 *
 * <p>확정만으로는 부족한 또 하나의 이유가 있다. 확정은 소재를 <b>DB 에서만</b> 줄인다.
 * 온체인 토큰은 그대로 남아 있어서, 체인만 보면 브랜드가 여전히 그 소재를 갖고 있는 것처럼
 * 보이고 <b>같은 소재로 두 번 업사이클했다고 주장할 수 있다.</b> 3단계(소각)가 그 구멍을
 * 막는다.
 *
 * <h2>서명이 네 번이다</h2>
 *
 * <pre>
 *   1) createBatch        생산 배치를 체인에 만든다
 *   2) submitPlanAndLock  투입을 잠그고 계획 해시를 올린다
 *   3) confirmProduction  소재를 소각하고 자투리를 발행한다
 *   4) linkProductItems   제품 개체를 배치에 연결한다
 * </pre>
 *
 * <p><b>순서는 컨트랙트가 강제한다.</b> 그래서 화면이 단계를 스스로 정하지 않고 체인에
 * 물어본다 — 서명은 됐는데 서버 보고가 실패하면 서버 기준으로는 영원히 "아직 안 함" 이고,
 * 그 상태에서 다시 서명하면 컨트랙트가 revert 해 막다른 길이 된다. 그 판정과 네 번의
 * 서명은 이미 생산 화면이 하고 있고({@code ProductionChainPanel}), 카드는 그것을 그대로
 * 띄운다. 에이전트용 서명 경로를 따로 만들면 두 벌이 되고, 어긋나는 순간 어느 쪽이 정본인지
 * 알 수 없게 된다.
 *
 * <p>그래서 이 tool 의 {@code signatureSteps} 는 1 이다. 네 단계의 진행은 카드 안의 패널이
 * 직접 보여 주고, 카드는 전부 끝났을 때 한 번 닫힌다.
 */
@Component
public class RecordProductionChainTool extends AbstractAgentTool {

    private final ProductionBatchService batchService;

    public RecordProductionChainTool(
            ObjectMapper objectMapper, ProductionBatchService batchService) {
        super(objectMapper);
        this.batchService = batchService;
    }

    @Override
    public String name() {
        return "record_production_chain";
    }

    @Override
    public String description() {
        return "확정된 생산을 블록체인에 기록하도록 담당자에게 서명 카드를 띄운다. "
                + "네 번의 서명이 필요하며 카드가 순서대로 안내한다. "
                + "이것을 해야 제품 QR 에서 소재까지 이력이 이어지고, 쓴 소재가 체인에서도 줄어든다. "
                + "생산 확정(confirm_production) 뒤에만 부를 수 있다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "batchId": {"type": "integer"},
                    "summary": {"type": "string", "description": "확인 카드에 보일 한 줄 설명"}
                  },
                  "required": ["batchId", "summary"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.PRODUCTION, JourneyPhase.PRODUCT);
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
     * 확정되지 않은 배치는 <b>카드를 띄우기 전에</b> 돌려세운다.
     *
     * <p>체인 기록은 확정된 배치만 받는다. 카드를 띄워 놓고 서명 창에서 막히면 담당자는
     * 지갑까지 열고 나서야 안 된다는 것을 알게 된다.
     */
    @Override
    public Optional<AgentToolResult> refuseBeforeProposal(
            AgentToolContext context, JsonNode arguments) {
        long batchId = requiredPositiveLong(arguments, "batchId");
        ProductionBatchResponse batch = findBatch(context, batchId);
        if (batch == null || batch.status() == ProductionBatchStatus.CONFIRMED) {
            // 못 찾은 경우는 판단할 근거가 없을 뿐이다. 소유 검사는 도메인이 한다.
            return Optional.empty();
        }
        return Optional.of(AgentToolResult.of(json(Map.of(
                "error", "PRODUCTION_NOT_CONFIRMED",
                "batchId", batchId,
                "status", batch.status(),
                "hint", "확정된 배치만 체인에 기록할 수 있습니다. confirm_production 을 먼저 "
                        + "진행하세요 — 확정은 되돌릴 수 없으므로 담당자에게 무엇이 소비되는지 "
                        + "먼저 설명해야 합니다."))));
    }

    /**
     * 이미 끝난 일인가.
     *
     * <p>네 단계가 모두 끝났는지는 체인이 안다. 서버가 가진 것은 1단계의 산물인
     * {@code chainBatchId} 뿐이라 여기서는 "시작조차 안 했는가" 만 볼 수 있다. 그래서
     * 이 판정은 하지 않는다 — 카드 안의 패널이 체인을 읽어 남은 단계만 열어 준다.
     */
    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long batchId = requiredPositiveLong(arguments, "batchId");
        String summary = requiredText(arguments, "summary");

        ProductionBatchResponse batch = findBatch(context, batchId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchId", batchId);
        payload.put("chainBatchId", batch == null ? null : batch.chainBatchId());
        payload.put("summary", summary);

        String message = batch != null && batch.chainBatchId() != null
                ? "생산이 블록체인에 기록됐습니다. 이제 제품 QR 에서 소재까지 이력이 이어집니다."
                : "블록체인 기록이 끝나지 않았습니다. 카드에서 남은 서명을 마쳐 주세요.";
        payload.put("message", message);

        return AgentToolResult.of(json(payload), message);
    }

    private ProductionBatchResponse findBatch(AgentToolContext context, long batchId) {
        return batchService.findMyBatches(context.userId()).stream()
                .filter(candidate -> candidate.productionBatchId() == batchId)
                .findFirst()
                .orElse(null);
    }
}
