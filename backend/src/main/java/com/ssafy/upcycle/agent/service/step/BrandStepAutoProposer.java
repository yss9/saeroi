package com.ssafy.upcycle.agent.service.step;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.service.AgentToolExecutor;
import com.ssafy.upcycle.agent.service.AgentToolRegistry;
import com.ssafy.upcycle.agent.service.OpenCards;
import com.ssafy.upcycle.agent.tool.AgentTool;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 담당자 차례가 되면 <b>말을 걸지 않아도 카드를 띄운다.</b>
 *
 * <h2>왜 필요한가 — 상대가 움직여도 대화창은 조용했다</h2>
 *
 * <p>공급업체가 발송하고, 가공업체가 수락하고, 결과를 돌려보내도 대화는 아무 말이 없었다.
 * 진행 막대와 오른쪽 목록만 바뀌었고, 담당자가 그 영역을 보고 있지 않으면 놓쳤다.
 *
 * <p>담당자가 먼저 물어보면 되지 않느냐 — <b>물어볼 수가 없다.</b> 온체인 작업 등록이나
 * 인계 서명 같은 단계는 구현 세부이지 담당자가 아는 절차가 아니다. 무엇이 남았는지 모르는
 * 사람에게 "무엇을 하시겠어요?" 라고 물으면 대화가 그 자리에서 멈춘다. 실제로 가공업체가
 * 수락한 뒤 담당자가 "소재 발송했어" 라고 먼저 꺼내야 진행됐다.
 *
 * <h2>모델을 부르지 않는다</h2>
 *
 * <p>여기서 띄우는 카드는 <b>무엇을 할지 이미 정해진</b> 것들뿐이다
 * ({@link AutoProposableStep}). 어느 거래·작업·배치인지, 어떤 도구인지, 무엇에 서명하는지가
 * 전부 상태에서 나온다. 모델이 고를 것이 없으니 부를 이유도 없고, 크레딧도 들지 않는다.
 *
 * <p>담당자에게 물어야 채워지는 인자가 있는 단계(소재 구매, 배치 열기, 소재 투입, 제품 등록)는
 * 띄우지 않는다. 그건 대화로 정한 뒤 모델이 띄우는 것이 맞다.
 *
 * <h2>모델이 띄울 때와 같은 검사를 지난다</h2>
 *
 * <p>{@link AgentToolExecutor#mayPropose} 를 거친다. 카드를 만드는 문이 둘인데 한쪽만
 * 검사하면, 도구가 막아 둔 것이 이 문으로 그대로 새어 나간다. 남은 일 목록은 DB 만 보고
 * 판정하는데 어떤 단계는 <b>체인을 읽어야 알 수 있다</b> — 소재 인계가 그렇다. DB 가
 * ACCEPTED 여도 업체가 체인에서 수락하기 전이면 서명할 수 없고, 모르고 서명하면 컨트랙트가
 * 되돌린다.
 *
 * <h2>이 대화가 시작한 일에만 띄운다</h2>
 *
 * <p>담당자가 부르지 않았는데 뜨는 카드라 범위가 가장 좁아야 한다. 홈페이지에서 산 소재가
 * 발송됐다고 에코백을 만드는 대화에 수령 카드가 뜨면, 담당자는 이것이 이번 제품 얘기인지 알 수
 * 없다({@link AgentProject}).
 *
 * <h2>두 번 띄우지 않는다</h2>
 *
 * <p>폴링마다 불리므로 같은 카드가 쌓이면 안 된다. 같은 도구·같은 대상에 대해 이미 떠 있는
 * 카드가 있으면 건너뛴다. 이미 처리한 건은 다음 폴링에서 상태가 바뀌어 목록에서 빠진다.
 *
 * <p><b>담당자가 취소한 건은 목록에서 빠지지 않는다</b> — 판매 시작을 취소해도 제품은 여전히
 * 판매 전이다. 그래서 이 대화에서 취소된 같은 일은 따로 보고 건너뛴다. 다시 하려면 담당자가
 * 말하고, 그때는 모델이 띄운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandStepAutoProposer {

    private final AgentStepCardStore cards;
    private final AgentToolExecutor executor;
    private final AgentToolRegistry registry;
    private final OpenCards openCards;
    private final ObjectMapper objectMapper;

    /**
     * 떠 있는 카드를 지금 남은 일과 맞춘다. 기한이 지난 것은 닫고, 빠진 것은 띄운다.
     *
     * <p><b>여기에는 트랜잭션이 없다.</b> 아래에서 도구의 사전 검사를 부르는데, 그 검사가
     * {@code @Transactional} 서비스를 지나면서 예외를 던지면 — 여기서 잡더라도 — 바깥
     * 트랜잭션이 rollback-only 로 표시된다. 그러면 같은 트랜잭션에서 띄운 카드까지 통째로
     * 사라지고 커밋이 {@code UnexpectedRollbackException} 으로 끝난다. 이 경로는 20초마다
     * 도는 폴링이라 그런 일이 나면 계속 깨진다. 쓰기는 {@link AgentStepCardStore} 의 짧은
     * 트랜잭션들이 맡는다.
     *
     * <p><b>남은 일은 받아서 쓴다.</b> 판정은 부르는 쪽이 한 번만 한다
     * ({@code AgentConversationService.progress}) — 진행 막대와 오른쪽 패널도 같은 목록을 읽는다.
     * 각자 판정하던 동안 한 화면에서 서로 다른 말을 했고, 체인도 요청마다 두 번 읽었다.
     *
     * @param steps 이 대화의 프로젝트에서 남은 일({@link BrandStepResolver})
     * @return 화면에서 달라진 카드 수. 0 이면 대화를 다시 읽을 필요가 없다
     */
    public int reconcile(AgentConversation conversation, List<BrandStep> steps) {
        if (!conversation.isActive()) {
            return 0;
        }
        int changed = cards.expireTimedOut(conversation.getId());
        return changed + propose(conversation, steps, openCards.of(conversation.getId()));
    }

    private int propose(AgentConversation conversation, List<BrandStep> steps, List<AgentPendingAction> open) {
        List<AgentPendingAction> rejected = null;
        int created = 0;
        for (BrandStep step : steps) {
            if (!step.isActionable() || step.toolName() == null) {
                continue;
            }
            Optional<AutoProposableStep> auto = AutoProposableStep.of(step.toolName());
            if (auto.isEmpty()) {
                continue;
            }
            // **한 단계가 흔들려도 나머지는 살린다.** 이 응답에는 진행 막대도 함께 실려
            // 있어서, 여기서 예외가 그대로 올라가면 카드 하나 때문에 화면 위쪽이 통째로
            // 비어 버린다. 게다가 20초마다 도는 경로라 한 번이 아니라 계속 그렇다.
            try {
                if (rejected == null) {
                    rejected = openCards.rejected(conversation.getId());
                }
                if (propose(conversation, step, auto.get(), open, rejected)) {
                    created++;
                }
            } catch (RuntimeException failure) {
                log.warn(
                        "Auto propose failed for one step: tool={} reference={} reason={}",
                        step.toolName(),
                        step.referenceId(),
                        failure.getClass().getSimpleName());
            }
        }
        return created;
    }

    /** @return 카드를 실제로 띄웠는가. 도구가 아직 이르다고 하면 조용히 넘어간다 */
    private boolean propose(
            AgentConversation conversation,
            BrandStep step,
            AutoProposableStep auto,
            List<AgentPendingAction> open,
            List<AgentPendingAction> rejected) {
        AgentTool tool = registry.find(auto.toolName()).orElse(null);
        if (tool == null) {
            // 목록이 가리키는 도구가 없다. 목록 쪽 오타이지 담당자가 할 수 있는 일이 아니다.
            log.warn("Auto proposable tool missing from registry: name={}", auto.toolName());
            return false;
        }

        Map<String, Object> arguments = auto.argumentsFor(step.referenceId(), auto.summaryFor(step.subject()));
        JsonNode argumentsNode = objectMapper.valueToTree(arguments);
        // 도구가 제목을 쓰면 그것을 쓴다 — 모델이 띄운 카드와 같은 정보가 담긴다(가공 결과의 손실 같은 것).
        String summary = executor
                .serverSummaryFor(conversation, conversation.getBrandUserId(), auto.toolName(), argumentsNode)
                .orElse(auto.summaryFor(step.subject()));
        arguments = auto.argumentsFor(step.referenceId(), summary);

        // 같은 일에 카드가 이미 떠 있으면 건너뛴다. 판정은 모델 경로와 같은 것을 쓴다 —
        // 문이 둘인데 판정이 둘이면 그 사이로 중복이 들어온다.
        if (openCards.sameWork(open, auto.toolName(), argumentsNode).isPresent()) {
            return false;
        }
        // **담당자가 취소한 일은 다시 띄우지 않는다.** 취소해도 남은 일 목록에는 그대로 남아,
        // 폴링마다 같은 카드가 새로 떴다 — 0원 판매 시작 카드를 세 번 취소했는데도 계속 떠서
        // 결국 승인됐다. 다시 하려면 담당자가 말하고, 그때는 모델이 띄운다.
        if (openCards.sameWork(rejected, auto.toolName(), argumentsNode).isPresent()) {
            return false;
        }

        // **도구가 스스로 볼 기회를 준다.** 남은 일 목록은 DB 만 보지만 어떤 단계는 체인을
        // 읽어야 알 수 있다. 여기서 막히면 아무 말도 하지 않는다 — 폴링마다 "아직
        // 안 됩니다" 를 남기면 대화가 그것으로 덮인다.
        if (!executor.mayPropose(conversation, conversation.getBrandUserId(), auto.toolName(), argumentsNode)) {
            log.debug(
                    "Auto propose held back by tool: tool={} reference={}",
                    auto.toolName(),
                    step.referenceId());
            return false;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(arguments);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Auto step payload must serialize", impossible);
        }

        // 카드의 종류와 서명 횟수는 도구에서 가져온다. 여기에 따로 적어 두면 도구가 바뀔 때
        // 한쪽만 고쳐져, 같은 작업이 어느 문으로 들어왔는지에 따라 다른 카드로 뜬다.
        cards.propose(
                conversation,
                noticeFor(step, auto),
                tool.pendingKind().actionType(),
                auto.toolName(),
                summary,
                payload,
                tool.signatureSteps());
        return true;
    }

    /**
     * 담당자가 읽을 안내.
     *
     * <p>무엇에 대한 일인지를 먼저 적는다. 거래·작업·배치가 여럿일 때 "수령을 확정할
     * 차례입니다" 만으로는 어느 것인지 알 수 없다.
     */
    private String noticeFor(BrandStep step, AutoProposableStep auto) {
        String notice = "%s — %s.".formatted(step.subject(), step.label());
        // 담당자가 모르는 절차면 왜 필요한지 함께 적는다(AutoProposableStep#why).
        return auto.why().map(why -> notice + " " + why).orElse(notice) + " 아래 카드에서 이어서 진행해 주세요.";
    }
}
