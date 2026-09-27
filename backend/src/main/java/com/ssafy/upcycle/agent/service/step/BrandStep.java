package com.ssafy.upcycle.agent.service.step;

/**
 * 여정의 한 지점에서 <b>담당자에게 남은 일 한 가지.</b>
 *
 * <h2>왜 이 타입이 생겼나 — 같은 질문을 네 곳이 각자 답하고 있었다</h2>
 *
 * <p>"담당자가 다음에 무엇을 해야 하나" 를 네 군데가 따로 계산했다. 대화 프롬프트에 넣는
 * 목록({@code AgentPendingWork}), 진행 막대({@code AgentJourneyProgress}), 가공 상태 판정
 * ({@code ProcessingTodo}), 그리고 화면의 거래 목록({@code pendingStepOf}).
 *
 * <p>덮는 범위가 서로 달랐다. 거래만 보는 곳, 가공만 보는 곳, 초안 배치만 보는 곳이 있었고
 * <b>어느 곳도 전부를 보지 않았다.</b> 그래서 한 곳을 고쳐도 다른 경로로 빠져나갔다.
 *
 * <p>실제로 그렇게 멈췄다. 생산을 확정한 뒤 남는 두 가지 — 블록체인 기록과 제품 연결 — 이
 * 어느 목록에도 없었다. 모델이 그 단계를 꺼내지 못했고, 담당자는 그런 단계가 있는 줄도 몰라
 * 물어볼 수도 없었다. 만들어진 개체 10개가 <b>어느 제품의 재고도 아닌 채</b> 남았다.
 *
 * <p>그래서 판정을 한 곳({@link BrandStepResolver})에 모으고, 대화·막대·패널이 같은 값을
 * 읽는다. 단계를 하나 추가할 때 고칠 자리가 하나다.
 *
 * @param domain 어느 영역의 일인가
 * @param referenceId 거래·작업·배치·제품 번호. <b>담당자에게 보여줄 값이 아니다</b> — 모델이
 *                    도구 인자로 쓰는 값이다
 * @param subject 담당자가 알아볼 이름. "실크 원단 1kg", "2026 실크 열쇠고리 1차"
 * @param action 무엇을 하는 일인가. {@link StepAction#WAIT} 이면 상대 차례다
 * @param label 담당자가 읽을 한 줄. 화면과 대화가 그대로 쓴다
 * @param instruction 모델용 지시. 부를 도구와 주의할 점을 담는다
 * @param toolName 모델이 부를 도구. {@code WAIT} 이면 null
 */
public record BrandStep(
        StepDomain domain,
        long referenceId,
        String subject,
        StepAction action,
        String label,
        String instruction,
        String toolName) {

    /** 담당자가 지금 할 수 있는 일인가. 아니면 상대를 기다리는 중이다. */
    public boolean isActionable() {
        return action != StepAction.WAIT;
    }

    public static BrandStep waiting(
            StepDomain domain, long referenceId, String subject, String label, String instruction) {
        return new BrandStep(domain, referenceId, subject, StepAction.WAIT, label, instruction, null);
    }

    public static BrandStep of(
            StepDomain domain,
            long referenceId,
            String subject,
            StepAction action,
            String label,
            String instruction,
            String toolName) {
        return new BrandStep(domain, referenceId, subject, action, label, instruction, toolName);
    }
}
