package com.ssafy.upcycle.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import java.util.Set;

/**
 * 모델이 부를 수 있는 기능 하나.
 *
 * <p>구현체는 <b>로직을 갖지 않는다.</b> 기존 도메인 서비스({@code ProcessorSearchService},
 * {@code TradeCommandService} …)를 호출해 결과를 모델이 읽을 형태로 옮기기만 한다. 규칙을
 * 여기에 복제하면 화면으로 하는 것과 대화로 하는 것의 동작이 갈라진다.
 *
 * <h2>읽기와 쓰기의 경계</h2>
 *
 * <p>{@link #isWrite()} 가 {@code true} 인 tool 은 <b>실행되지 않는다.</b>
 * {@code AgentToolExecutor} 가 대신 {@code AgentPendingAction} 을 만들고, 사용자가 화면에서
 * 승인한 뒤에야 {@link #execute} 가 불린다. 모델의 오해 한 번으로 결제나 서명이 나가는 일을
 * 구조적으로 막기 위한 것이라, 개별 tool 이 이 결정을 우회할 수 없게 되어 있다.
 */
public interface AgentTool {

    /** 모델이 부를 이름. snake_case 로 통일한다. */
    String name();

    /** 언제 이 tool 을 쓰는지. 모델이 고르는 유일한 근거이므로 구체적으로 쓴다. */
    String description();

    /** 인자의 JSON Schema. 인자가 없으면 {@code {"type":"object","properties":{}}}. */
    String parametersJsonSchema();

    /** 이 tool 이 노출되는 단계. 비어 있으면 모든 단계에서 보인다. */
    Set<JourneyPhase> phases();

    /**
     * 상태를 바꾸는가.
     *
     * <p>{@code true} 면 사용자 승인 없이는 실행되지 않는다.
     */
    boolean isWrite();

    /**
     * 카드를 띄우기 <b>전에</b> 거절할 이유가 있으면 그 결과를 돌려준다.
     *
     * <p>쓰기 tool 은 승인 전까지 실행되지 않으므로, 실행 안에서 검사하면 담당자가 카드를
     * 누른 뒤에야 거절된다. 담당자 입장에서는 승인했는데 오류가 나는 것이고, 무엇이 잘못됐는지
     * 설명하는 것은 모델이 아니라 오류 화면이 된다.
     *
     * <p>모델이 고쳐서 다시 부를 수 있는 종류의 문제에 쓴다 — 빠진 필수 공정처럼. 권한이나
     * 재고 같은 도메인 규칙은 여기서 미리 보지 않는다. 그건 승인 시점의 상태로 판단해야 하고,
     * 두 곳에서 검사하면 규칙이 갈라진다.
     *
     * <p>여기서 무엇을 하든 <b>상태를 바꾸면 안 된다.</b> 이 메서드는 사용자 승인 없이 불린다.
     */
    default java.util.Optional<AgentToolResult> refuseBeforeProposal(
            AgentToolContext context, com.fasterxml.jackson.databind.JsonNode arguments) {
        return java.util.Optional.empty();
    }

    /**
     * 이 카드가 <b>이미 지난 일</b>인지. 그렇다면 그 이유를 담당자에게 할 말로 돌려준다.
     *
     * <p>대화창은 도메인의 유일한 입구가 아니다. 담당자는 같은 거래를 거래 상세 화면에서
     * 수령 확정하거나 취소할 수 있고, 그러면 대화는 그 사실을 모른 채 "수령 확정하시겠어요?"
     * 카드를 띄운다. 눌러도 도메인이 막아 데이터가 깨지지는 않지만, 담당자는 승인했는데
     * 오류가 나는 것을 보게 된다 — 무엇이 잘못됐는지 설명하는 것이 모델이 아니라 오류
     * 화면이 되어 버린다.
     *
     * <p>그래서 두 시점에 같은 검사를 쓴다. 카드를 <b>만들 때</b>(이미 끝난 일이면 애초에
     * 띄우지 않는다) 그리고 <b>누를 때</b>(만든 뒤에 상태가 바뀌었을 수 있다).
     *
     * <p><b>도메인 규칙을 다시 구현하지 않는다.</b> 여기서 보는 것은 "이 카드가 가리키는 일이
     * 이미 일어났는가" 뿐이고, 권한·재고·상태 기계는 승인 시점에 도메인이 판단한다. 두
     * 곳에서 같은 규칙을 검사하면 한쪽만 바뀔 때 구멍이 난다.
     *
     * <p>상태를 바꾸면 안 된다 — 이 메서드는 사용자 승인 없이 불린다.
     */
    default java.util.Optional<String> stalenessReason(
            AgentToolContext context, com.fasterxml.jackson.databind.JsonNode arguments) {
        return java.util.Optional.empty();
    }

    /**
     * 쓰기 tool 이 사용자에게 어떤 종류의 확인을 요구하는지.
     *
     * <p>읽기 tool 에서는 쓰이지 않는다.
     */
    default PendingActionKind pendingKind() {
        return PendingActionKind.CONFIRM;
    }

    /**
     * 이 tool 이 요구하는 서명 횟수. 기본은 1회(단발)다.
     *
     * <p>생산 확정처럼 컨트랙트가 순서를 강제하는 연속 서명은 이 값을 4로 두어 카드 하나로
     * 묶는다. 담당자가 카드 네 개를 따로 누르는 것보다, 진행 상태가 보이는 하나를 누르는
     * 편이 낫다 — 팝업 횟수는 같지만 하나의 작업으로 인식된다.
     *
     * <p>단발도 {@code stepTotal=1} 인 묶음으로 다뤄 처리 경로를 하나로 유지한다.
     */
    default int signatureSteps() {
        return 1;
    }

    /**
     * 실행한다.
     *
     * @param context 호출한 사용자와 대화. 인가는 여기 담긴 사용자로 기존 서비스가 판단한다
     * @param arguments 모델이 만든 인자. 신뢰할 수 없는 입력으로 다룬다
     * @return 모델에게 되돌려줄 결과
     */
    AgentToolResult execute(AgentToolContext context, JsonNode arguments);
}
