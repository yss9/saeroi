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
        String nextTodo) {

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
