package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;

/**
 * 가공 작업의 상태가 <b>담당자에게 무엇을 뜻하는지</b>. 이 판정은 여기 한 곳에만 둔다.
 *
 * <p>같은 물음("지금 내가 뭘 해야 하나")에 답하는 자리가 셋이다 — 모델이 부르는 조회
 * ({@code get_my_processing}), 대화 위 경과 막대({@link AgentJourneyProgress}), 사건 보고에
 * 붙는 할 일 목록({@link AgentPendingWork}). 셋이 각자 판정하면 상태 하나를 추가할 때 세
 * 군데를 고쳐야 하고, 그중 하나를 빠뜨리면 <b>모델이 말하는 것과 화면이 보여주는 것이
 * 어긋난다.</b> 담당자는 어느 쪽이 맞는지 알 수 없다.
 *
 * <p>대신 <b>표현은 둘로 나눈다.</b> 모델에게는 다음에 부를 tool 까지 말해 줘야 하고, 화면에는
 * 짧은 문구가 맞다. 무엇을 뜻하는지는 하나이고 어떻게 말하는지가 둘이다.
 */
public enum ProcessingTodo {

    PAY(
            true,
            "가공비 결제 차례입니다",
            "가공비가 정해졌고 아직 결제 전이다. 담당자에게 금액을 보여 주고 "
                    + "pay_processing_fee 로 결제 카드를 띄운다"),

    /**
     * 결제만으로는 소재를 보낼 수 없다.
     *
     * <p>엔티티가 {@code ProcessingJobStatus.ACCEPTED} 를 요구한다 — "결제만으로는 보내지
     * 않는다. 가공사가 받겠다고 답한 뒤여야 한다". 결제 직후에 인계를 안내하면 담당자는
     * 시킨 대로 눌렀는데 도메인이 거절하는 것을 보게 된다.
     */
    WAIT_ACCEPT(false, "업체 수락을 기다리는 중입니다", "결제가 끝났다. 업체가 수락하기를 기다리는 중이다"),

    TRANSFER(
            true,
            "소재 보내기 서명(두 번째 서명)을 할 차례입니다",
            "업체가 작업을 확인했다. request_custody_transfer 로 소재 보내기 서명 카드를 띄운다 — 이것이 소재를 "
                    + "보내는 두 번째 서명이고, 서명하면 소재가 업체로 넘어간다고 먼저 말한다"),

    WAIT_RECEIVE(false, "업체가 받기를 기다리는 중입니다", "소재를 보냈다. 업체가 받기를 기다리는 중이다"),

    WAIT_WORK(false, "업체가 작업 중입니다", "업체가 작업 중이다. 담당자가 지금 할 일은 없다"),

    /**
     * 업체가 결과를 <b>올렸고 아직 돌려보내지 않았다.</b>
     *
     * <p>예전에는 여기서 승인 서명을 받으라고 했다. 그런데 승인({@code receiveReturn})은
     * 결과물이 반환 발송된 뒤에만 받으므로, 이 단계에서 뜬 승인 카드는 절대 성공할 수 없었다.
     * 지금 담당자가 할 일은 결과를 확인하는 것이고, 반려는 가공 화면에서 한다.
     */
    REVIEW(
            true,
            "업체가 결과를 올렸습니다. 확인해 주세요",
            "업체가 결과를 제출했고 아직 결과물을 돌려보내지 않았다. 결과(산출량·손실·등급)를 "
                    + "담당자에게 보여 준다. 고칠 게 있으면 가공 화면에서 반려할 수 있다고 알린다. "
                    + "승인 서명은 업체가 반환 발송한 뒤에 한다 — 지금 approve_processing_result 를 "
                    + "부르지 않는다"),

    APPROVE_ON_ARRIVAL(
            true,
            "소재가 오는 중입니다. 받으면 승인해 주세요",
            "업체가 소재를 돌려보냈다. 받은 뒤 approve_processing_result 로 승인한다"),

    DONE(false, "끝난 작업입니다", "끝난 작업이다. 이 소재는 생산에 쓸 수 있다"),

    REJECTED(false, "반려한 작업입니다", "담당자가 결과를 반려한 작업이다"),

    DECLINED(false, "업체가 거절했습니다", "업체가 거절했다. 다른 업체를 찾아야 한다"),

    CLOSED(false, "닫힌 작업입니다", "기한이 지났거나 취소된 작업이다");

    private final boolean actionable;
    private final String label;
    private final String instruction;

    ProcessingTodo(boolean actionable, String label, String instruction) {
        this.actionable = actionable;
        this.label = label;
        this.instruction = instruction;
    }

    /** 담당자가 <b>지금</b> 할 수 있는 일이 있는가. 없으면 상대를 기다리는 중이다. */
    public boolean isActionable() {
        return actionable;
    }

    /** 화면에 붙는 짧은 문구. */
    public String label() {
        return label;
    }

    /** 모델에게 주는 문장. 다음에 부를 tool 까지 말해 준다. */
    public String instruction() {
        return instruction;
    }

    /** 이 작업이 이미 끝났거나 닫혔는가. 진행 중인 것만 고를 때 쓴다. */
    public boolean isClosed() {
        return this == DONE || this == REJECTED || this == DECLINED || this == CLOSED;
    }

    public static ProcessingTodo of(ProcessingJobStatus status) {
        return switch (status) {
            case CREATED -> PAY;
            case PAID -> WAIT_ACCEPT;
            case ACCEPTED -> TRANSFER;
            case SHIPPED -> WAIT_RECEIVE;
            case RECEIVED, PROCESSING -> WAIT_WORK;
            case RESULT_SUBMITTED -> REVIEW;
            case RETURN_SHIPPED -> APPROVE_ON_ARRIVAL;
            case CONFIRMED -> DONE;
            case REJECTED -> ProcessingTodo.REJECTED;
            case DECLINED -> ProcessingTodo.DECLINED;
            case EXPIRED, CANCELED -> CLOSED;
        };
    }
}
