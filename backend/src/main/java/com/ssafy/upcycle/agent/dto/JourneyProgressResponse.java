package com.ssafy.upcycle.agent.dto;

import com.ssafy.upcycle.agent.entity.JourneyPhase;
import java.util.List;

/**
 * 대화 위에 뜨는 경과 표시.
 *
 * <p><b>판정을 서버가 한다.</b> 화면이 계산하면 모델이 말하는 것과 막대가 보여주는 것이
 * 어긋난다 — "아직 선별이 남았습니다" 라고 말하는데 막대는 다 찬 식이다. 같은 근거로
 * 계산해야 담당자가 둘을 같은 것으로 읽을 수 있다. 가공 계획({@code requirementJson})은
 * 애초에 화면이 읽을 수도 없다.
 *
 * <h2>남은 일은 한 번만 판정한다</h2>
 *
 * <p>{@code nextTodo} 와 {@code tasks} 는 모델에게 가는 목록과 <b>같은 판정</b>
 * ({@code BrandStepResolver})에서 나온다. 예전에는 막대·오른쪽 패널·모델이 각자 판정해,
 * 체인에 수령 서명이 빠진 거래를 두고 막대는 "수령이 끝났습니다" 를, 대화는 "서명이
 * 남았습니다" 를 말했다. 오른쪽 패널은 거래만 볼 줄 알아 가공·생산의 할 일은 아예 없었다.
 *
 * <h2>두 층이다</h2>
 *
 * <p>{@code journey} 는 여정 네 칸이다. 대화가 길어져도 전체에서 어디쯤인지 잃지 않게
 * 항상 보인다. {@code steps} 는 <b>지금 단계의 세부</b>이고 단계가 바뀌면 내용이 통째로
 * 바뀐다 — 네 단계의 세부를 모두 펼치면 스무 줄이 되어 아무도 읽지 않는다.
 */
public record JourneyProgressResponse(
        JourneyPhase phase,
        List<Mark> journey,
        /** 지금 따라가는 대상. 없으면 null — 막대를 그리지 않는다. */
        String subject,
        /** 대상의 부연. 수량·금액처럼 한 줄로 붙는 것. */
        String subjectDetail,
        List<Mark> steps,
        /** 담당자가 지금 할 일. 기다리는 중이면 그렇다고 적는다. */
        String nextTodo,
        /** 이 대화의 프로젝트에서 남은 일 전부. 오른쪽 패널이 그린다. */
        List<Task> tasks,
        /**
         * 이번 조회에서 <b>새 카드가 떴는가.</b>
         *
         * <p>화면은 대화를 주기 조회하지 않는다 — 담당자가 말을 걸 때만 바뀐다고 보았기
         * 때문이다. 그런데 상대(공급업체·가공업체)가 움직이면 담당자 차례가 되고, 그때
         * 서버가 카드를 대신 띄운다({@code BrandStepAutoProposer}).
         *
         * <p>이 값이 참이면 화면이 대화를 다시 읽어야 새 카드가 보인다. 매번 다시 읽게 하면
         * 20초마다 대화 전체를 받아 오게 되므로, 바뀐 때만 알린다.
         */
        boolean conversationChanged,
        /**
         * 이 대화의 프로젝트에서 <b>결제한 금액</b> — 소재 구매와 가공비. 오른쪽 패널이 그린다.
         *
         * <p>예전에는 화면이 거래 목록만 더해 가공비가 빠졌다. 가공은 업체 한 곳에 공정을 여럿 맡기면 한 건이고,
         * 업체가 다르면 따로다. 그래서 가공 작업마다 한 줄에 공정과 업체를 함께 적는다.
         */
        List<Spend> spending) {

    /**
     * 카드 알림 없이 만드는 경우.
     *
     * <p>막대를 계산하는 쪽({@code AgentJourneyProgress})은 카드가 떴는지 모른다 — 그건
     * 조회를 엮는 쪽이 붙인다. 기본값을 두어 계산부가 이 필드를 알 필요가 없게 한다.
     */
    public JourneyProgressResponse(
            JourneyPhase phase,
            List<Mark> journey,
            String subject,
            String subjectDetail,
            List<Mark> steps,
            String nextTodo) {
        this(phase, journey, subject, subjectDetail, steps, nextTodo, List.of(), false, List.of());
    }

    /** 이번 조회에서 새 카드가 떴음을 표시한 사본. */
    public JourneyProgressResponse withConversationChanged(boolean changed) {
        return new JourneyProgressResponse(
                phase, journey, subject, subjectDetail, steps, nextTodo, tasks, changed, spending);
    }

    /** 남은 일 목록을 붙인 사본. 막대의 대상이 없어도 패널에는 할 일이 보여야 한다. */
    public JourneyProgressResponse withTasks(List<Task> tasks) {
        return new JourneyProgressResponse(
                phase, journey, subject, subjectDetail, steps, nextTodo, List.copyOf(tasks),
                conversationChanged, spending);
    }

    /** 결제한 금액을 붙인 사본. */
    public JourneyProgressResponse withSpending(List<Spend> spending) {
        return new JourneyProgressResponse(
                phase, journey, subject, subjectDetail, steps, nextTodo, tasks, conversationChanged,
                List.copyOf(spending));
    }

    /**
     * 결제 한 건.
     *
     * @param kind MATERIAL(소재 구매) · PROCESSING(가공비)
     * @param label 무엇에 — "폐데님 원단 10kg", "폐데님 원단 10kg 가공"
     * @param detail 가공이면 공정과 업체 — "세척·해체·선별 · pro". 소재면 null
     * @param amount 실제로 결제한 금액(원)
     */
    public record Spend(String kind, String label, String detail, long amount) {}

    /**
     * 남은 일 한 줄.
     *
     * @param domain 무엇의 번호인지 — TRADE · PROCESSING · PRODUCTION · PRODUCT
     * @param actionable 담당자가 할 일이면 참, 상대를 기다리는 중이면 거짓
     */
    public record Task(String domain, Long referenceId, String subject, String label, boolean actionable) {}

    /** 여정 한 칸, 또는 세부 한 단계. */
    public record Mark(String label, boolean done, boolean current) {

        public static Mark done(String label) {
            return new Mark(label, true, false);
        }

        public static Mark todo(String label) {
            return new Mark(label, false, false);
        }
    }

    /** 대상이 없을 때. 소재를 아직 고르지 않았거나 여정이 끝난 대화가 여기 해당한다. */
    public static JourneyProgressResponse empty(JourneyPhase phase, List<Mark> journey) {
        return new JourneyProgressResponse(phase, journey, null, null, List.of(), null);
    }
}
