package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.dto.LiveBlockEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 새 블록을 여러 관리자 브라우저에 fan-out하고, 재접속용 최근 이벤트를 보관한다. */
@Component
public class BlockchainLiveEventHub {

    private static final int REPLAY_LIMIT = 50;
    private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();
    private final Deque<LiveBlockEvent> replay = new ArrayDeque<>();

    public SseEmitter subscribe(Long lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(ignored -> clients.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(new ConnectedEvent(Instant.now(), lastEventId)));
            for (LiveBlockEvent event : replayAfter(lastEventId)) {
                sendBlock(emitter, event);
            }
        } catch (IOException | IllegalStateException exception) {
            clients.remove(emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publish(LiveBlockEvent event) {
        synchronized (replay) {
            replay.addLast(event);
            while (replay.size() > REPLAY_LIMIT) replay.removeFirst();
        }
        for (SseEmitter emitter : clients) {
            try {
                sendBlock(emitter, event);
            } catch (IOException | IllegalStateException exception) {
                clients.remove(emitter);
                emitter.complete();
            }
        }
    }

    public void heartbeat(long latestBlock) {
        HeartbeatEvent heartbeat = new HeartbeatEvent(latestBlock, Instant.now());
        for (SseEmitter emitter : clients) {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data(heartbeat));
            } catch (IOException | IllegalStateException exception) {
                clients.remove(emitter);
                emitter.complete();
            }
        }
    }

    private List<LiveBlockEvent> replayAfter(Long lastEventId) {
        synchronized (replay) {
            if (lastEventId == null) return List.of();
            return replay.stream().filter(event -> event.number() > lastEventId).toList();
        }
    }

    private static void sendBlock(SseEmitter emitter, LiveBlockEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(event.number()))
                .name("block")
                .data(event));
    }

    record ConnectedEvent(Instant connectedAt, Long replayAfterBlock) {}
    record HeartbeatEvent(long latestBlock, Instant checkedAt) {}
}
