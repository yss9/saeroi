package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.agent.service.ConversationTurns;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 한 대화에서 모델 턴은 한 번에 하나만 돈다.
 *
 * <p>소재 두 가지를 함께 가공하며 카드 두 장을 1초 간격으로 누르자 턴 두 개가 겹쳐 돌았다. 먼저 시작한
 * 턴은 다른 카드가 아직 안 끝난 상태를 보고 말해, "울 원단은 아직 결제가 필요합니다" 와 "두 가공비 모두
 * 결제됐습니다" 가 연달아 떴다.
 */
class ConversationTurnsTests {

    private static final long CONVERSATION = 20L;

    private final ConversationTurns turns = new ConversationTurns();
    private final List<String> ran = new CopyOnWriteArrayList<>();

    @Test
    void 도는_턴이_있으면_이어_말하기는_맡기고_곧바로_돌아온다() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        Thread first = new Thread(() -> turns.resume(CONVERSATION, "울 원단 인계 서명", fact -> {
            ran.add("turn:" + fact);
            firstStarted.countDown();
            await(releaseFirst);
        }, facts -> ran.add("followUp:" + facts)));
        first.start();
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // 두 번째 카드. 앞 턴이 끝나기를 기다리지 않는다 — 그러면 화면이 그동안 멈춘다.
        turns.resume(CONVERSATION, "폐데님 원단 인계 서명", fact -> ran.add("turn:" + fact), facts -> ran.add("x"));
        assertThat(ran).containsExactly("turn:울 원단 인계 서명");

        releaseFirst.countDown();
        first.join(5_000);
        // 앞 턴이 끝난 뒤, 그 상태를 보고 이어 말한다.
        assertThat(ran).containsExactly("turn:울 원단 인계 서명", "followUp:폐데님 원단 인계 서명");
    }

    @Test
    void 그동안_끝난_일은_모아서_한_번에_이어_말한다() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        Thread first = new Thread(() -> turns.resume(CONVERSATION, "가공비 결제 1", fact -> {
            firstStarted.countDown();
            await(releaseFirst);
        }, facts -> ran.add(facts)));
        first.start();
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        turns.resume(CONVERSATION, "가공비 결제 2", fact -> ran.add("x"), facts -> ran.add("x"));
        turns.resume(CONVERSATION, "소재 인계 서명", fact -> ran.add("x"), facts -> ran.add("x"));
        releaseFirst.countDown();
        first.join(5_000);

        assertThat(ran).containsExactly("가공비 결제 2\n소재 인계 서명");
    }

    @Test
    void 담당자의_말은_도는_턴이_끝날_때까지_기다린다() throws Exception {
        CountDownLatch resumeStarted = new CountDownLatch(1);
        CountDownLatch releaseResume = new CountDownLatch(1);
        Thread resume = new Thread(() -> turns.resume(CONVERSATION, "결제 완료", fact -> {
            ran.add("resume");
            resumeStarted.countDown();
            await(releaseResume);
            ran.add("resume done");
        }, facts -> ran.add("followUp")));
        resume.start();
        assertThat(resumeStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Thread user = new Thread(() -> turns.exclusive(CONVERSATION, () -> ran.add("user"), facts -> ran.add("x")));
        user.start();
        Thread.sleep(200);
        assertThat(ran).containsExactly("resume");

        releaseResume.countDown();
        resume.join(5_000);
        user.join(5_000);
        assertThat(ran).containsExactly("resume", "resume done", "user");
    }

    @Test
    void 이번_턴이_실패해도_맡겨진_일은_돌고_실패는_호출한_쪽으로_간다() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        Thread first = new Thread(() -> {
            try {
                turns.resume(CONVERSATION, "결제 완료", fact -> {
                    firstStarted.countDown();
                    await(releaseFirst);
                    throw new IllegalStateException("LLM unavailable");
                }, facts -> ran.add("followUp:" + facts));
            } catch (IllegalStateException failure) {
                failures.add(failure);
            }
        });
        first.start();
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
        turns.resume(CONVERSATION, "서명 완료", fact -> ran.add("x"), facts -> ran.add("x"));

        releaseFirst.countDown();
        first.join(5_000);

        assertThat(ran).containsExactly("followUp:서명 완료");
        assertThat(failures).hasSize(1);
        // 자리가 비었다 — 다음 턴이 막히지 않는다.
        turns.resume(CONVERSATION, "다음", fact -> ran.add("turn:" + fact), facts -> ran.add("x"));
        assertThat(ran).endsWith("turn:다음");
    }

    @Test
    void 다른_대화의_턴은_서로_기다리지_않는다() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        Thread first = new Thread(() -> turns.resume(CONVERSATION, "a", fact -> {
            firstStarted.countDown();
            await(releaseFirst);
        }, facts -> ran.add("x")));
        first.start();
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        turns.resume(CONVERSATION + 1, "b", fact -> ran.add("other:" + fact), facts -> ran.add("x"));
        assertThat(ran).containsExactly("other:b");

        releaseFirst.countDown();
        first.join(5_000);
    }

    @Test
    void 도는_턴이_없으면_실패는_그대로_올라간다() {
        assertThatThrownBy(() -> turns.resume(CONVERSATION, "결제 완료", fact -> {
            throw new IllegalStateException("LLM unavailable");
        }, facts -> ran.add("x"))).isInstanceOf(IllegalStateException.class);
        assertThat(ran).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test latch timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
