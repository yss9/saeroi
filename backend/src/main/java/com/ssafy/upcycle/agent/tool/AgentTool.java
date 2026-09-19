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
