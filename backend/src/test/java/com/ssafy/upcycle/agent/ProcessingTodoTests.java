package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.agent.service.ProcessingTodo;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 가공 작업의 상태가 담당자에게 무엇을 뜻하는지 — 그 판정 한 곳.
 *
 * <p>같은 물음에 답하는 자리가 셋이라({@code get_my_processing}, 경과 막대, 사건 보고)
 * 여기가 흔들리면 모델이 말하는 것과 화면이 어긋난다.
 */
class ProcessingTodoTests {

    @ParameterizedTest
    @EnumSource(ProcessingJobStatus.class)
    void 모든_상태에_뜻이_있다(ProcessingJobStatus status) {
        ProcessingTodo todo = ProcessingTodo.of(status);

        // 상태를 새로 추가하고 판정을 빠뜨리면 여기서 걸린다.
        assertThat(todo).isNotNull();
        assertThat(todo.label()).isNotBlank();
        assertThat(todo.instruction()).isNotBlank();
    }

    /**
     * 결제만으로는 소재를 보낼 수 없다.
     *
     * <p>{@code ProcessingJob.ship} 이 {@code ACCEPTED} 를 요구한다 — "결제만으로는 보내지
     * 않는다. 가공사가 받겠다고 답한 뒤여야 한다". 예전에는 결제 콜백이 "다음은 소재를
     * 업체로 보내는 단계" 라고 대화에 알려, 담당자가 시킨 대로 눌렀다가 거절당했다.
     */
    @Test
    void 결제_직후에는_담당자가_할_일이_없다() {
        ProcessingTodo paid = ProcessingTodo.of(ProcessingJobStatus.PAID);

        assertThat(paid.isActionable())
                .as("결제 직후는 업체 수락을 기다리는 구간이다")
                .isFalse();
        assertThat(paid.instruction()).doesNotContain("request_custody_transfer");
    }

    @Test
    void 수락된_뒤에야_인계를_안내한다() {
        ProcessingTodo accepted = ProcessingTodo.of(ProcessingJobStatus.ACCEPTED);

        assertThat(accepted.isActionable()).isTrue();
        assertThat(accepted.instruction()).contains("request_custody_transfer");
    }

    @Test
    void 기다리는_구간은_할_일로_세지_않는다() {
        // 여기 적히면 모델이 무언가 하려 든다 — 재촉할 tool 을 찾거나 지어낸다.
        assertThat(ProcessingTodo.of(ProcessingJobStatus.SHIPPED).isActionable()).isFalse();
        assertThat(ProcessingTodo.of(ProcessingJobStatus.PROCESSING).isActionable()).isFalse();
    }

    @Test
    void 끝난_작업은_진행_중으로_보지_않는다() {
        assertThat(ProcessingTodo.of(ProcessingJobStatus.CONFIRMED).isClosed()).isTrue();
        assertThat(ProcessingTodo.of(ProcessingJobStatus.CANCELED).isClosed()).isTrue();
        assertThat(ProcessingTodo.of(ProcessingJobStatus.RECEIVED).isClosed()).isFalse();
    }
}
