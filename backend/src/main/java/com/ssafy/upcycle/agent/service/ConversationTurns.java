package com.ssafy.upcycle.agent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 한 대화에서 모델 턴은 <b>한 번에 하나만</b> 돈다.
 *
 * <h2>왜 필요한가 — 두 턴이 겹쳐 같은 말을 두 번, 서로 다른 말을 했다</h2>
 *
 * <p>소재 두 가지를 함께 가공하면 카드가 두 장씩 뜨고, 담당자는 1초 간격으로 두 장을 누른다. 카드마다
 * 요청이 따로 와서 모델 턴도 두 개가 동시에 돌았다. 각 턴은 시작할 때의 상태를 보고 말하므로 먼저 시작한
 * 턴은 다른 카드가 아직 안 끝난 것으로 알았다 — 한쪽은 "울 원단은 아직 결제가 필요합니다", 다른 쪽은 "두
 * 가공비 모두 결제됐습니다" 를 연달아 띄웠고, 인계 서명 뒤에는 "소재가 업체로 발송됐습니다" 와 "두 소재
 * 모두 업체로 발송되었습니다" 가 나란히 떴다.
 *
 * <h2>어떻게 하는가</h2>
 *
 * <ul>
 *   <li><b>이어 말하기</b>(카드 처리·결제 복귀 뒤)는 도는 턴이 있으면 기다리지 않는다. 무슨 일이 있었는지만
 *       맡기고 곧바로 돌아온다. 도는 턴이 끝나면 맡겨 둔 일을 <b>모아 한 턴</b>으로 이어 말한다 — 그 턴은
 *       앞 턴이 끝난 뒤의 상태를 보므로 서로 어긋난 말이 나오지 않는다.</li>
 *   <li><b>담당자의 말</b>은 도는 턴이 끝날 때까지 기다렸다가 돈다. 묻지 않은 말에 답하는 턴과 섞이면 한
 *       턴이 담당자의 말까지 읽고 답한 뒤 담당자의 턴이 같은 답을 한 번 더 한다.</li>
 * </ul>
 *
 * <p>서버 한 대를 전제로 한 메모리 잠금이다. 대화마다 작은 항목 하나가 남는데 지우지 않는다 — 지우는 순간
 * 다른 요청이 옛 항목을 잡고 있으면 두 턴이 다시 겹칠 수 있다.
 */
@Component
public class ConversationTurns {

    private final ConcurrentHashMap<Long, Slot> slots = new ConcurrentHashMap<>();

    private static final class Slot {
        private boolean running;
        private final List<String> waiting = new ArrayList<>();
    }

    /**
     * 이어 말하기. 도는 턴이 없으면 지금 돌고, 있으면 {@code outcome} 을 맡기고 곧바로 돌아온다.
     *
     * <p>이 호출이 턴을 돌렸다면 그 턴이 끝난 뒤 맡겨진 일도 이어서 돈다({@code followUp}). 이 호출의 턴이
     * 실패해도 맡겨진 일은 돌고, 실패는 그 뒤에 호출한 쪽으로 올라간다.
     *
     * @param turn 이번 일로 한 턴을 돈다. 실패하면 예외를 올린다 — 호출한 쪽이 대신 안내한다
     * @param followUp 맡겨진 일들을 모아 한 턴을 돈다. <b>예외를 올리지 않아야 한다</b> — 맡긴 쪽은 이미
     *     돌아가 대신 안내할 수 없다
     */
    public void resume(Long conversationId, String outcome, Consumer<String> turn, Consumer<String> followUp) {
        Slot slot = slots.computeIfAbsent(conversationId, id -> new Slot());
        synchronized (slot) {
            if (slot.running) {
                slot.waiting.add(outcome == null ? "" : outcome);
                return;
            }
            slot.running = true;
        }
        try {
            turn.accept(outcome);
        } finally {
            drain(slot, followUp);
        }
    }

    /**
     * 담당자의 말. 도는 턴이 있으면 끝날 때까지 기다렸다가 돈다. 그동안 맡겨진 일은 이 턴 뒤에 돈다.
     */
    public void exclusive(Long conversationId, Runnable turn, Consumer<String> followUp) {
        Slot slot = slots.computeIfAbsent(conversationId, id -> new Slot());
        synchronized (slot) {
            while (slot.running) {
                try {
                    slot.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for the running agent turn");
                }
            }
            slot.running = true;
        }
        try {
            turn.run();
        } finally {
            drain(slot, followUp);
        }
    }

    /**
     * 맡겨진 일이 없어질 때까지 모아서 돈 뒤 자리를 비운다.
     *
     * <p><b>비었는지 보는 것과 자리를 비우는 것은 한 잠금 안에서 한다.</b> 둘 사이가 벌어지면 그 틈에 맡겨진
     * 일은 아무도 돌지 않는다 — 맡긴 쪽은 도는 턴이 있다고 보고 돌아갔고, 도는 쪽은 이미 비었다고 봤다.
     */
    private static void drain(Slot slot, Consumer<String> followUp) {
        while (true) {
            List<String> outcomes;
            synchronized (slot) {
                if (slot.waiting.isEmpty()) {
                    slot.running = false;
                    slot.notifyAll();
                    return;
                }
                outcomes = List.copyOf(slot.waiting);
                slot.waiting.clear();
            }
            try {
                followUp.accept(joined(outcomes));
            } catch (RuntimeException unexpected) {
                // 올리지 않기로 한 예외다. 그래도 올라오면 여기서 멈추지 않는다 — 자리를 비우지 못하면 이
                // 대화의 턴이 영원히 막힌다.
            }
        }
    }

    /** 맡겨진 일들을 한 덩어리로. 빈 것은 뺀다 — 결과 문장 없이 이어 말하라는 요청도 있다. */
    private static String joined(List<String> outcomes) {
        List<String> facts = outcomes.stream().filter(StringUtils::hasText).toList();
        return String.join("\n", facts);
    }
}
