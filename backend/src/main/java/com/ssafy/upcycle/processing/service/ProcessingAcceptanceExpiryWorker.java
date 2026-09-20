package com.ssafy.upcycle.processing.service;

import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 가공사가 답하지 않은 채 수락 기한이 지난 작업을 정리한다.
 *
 * <p>요청 시 결제로 정했기 때문에 반드시 있어야 하는 배치다. 브랜드는 이미 돈을 냈고 소재는
 * 잠겨 있는데, 가공사가 아무것도 누르지 않으면 <b>아무 일도 일어나지 않는 채로 영원히</b>
 * 남는다. 그 상태에서 빠져나갈 문을 브랜드 쪽에 만들어 줄 수도 있지만, 그건 브랜드가
 * 자기 소재가 묶여 있다는 것을 알아차려야 한다는 뜻이다.
 *
 * <p>요청 만료 배치({@code ProcessingRequestExpiryWorker})와 겹쳐 보이지만 보는 곳이 다르다 —
 * 그쪽은 요청의 기한을, 이쪽은 작업의 수락 기한을 본다. 둘 다 같은 종착지로 가고 서로
 * 멱등하므로, 어느 쪽이 먼저 돌아도 결과가 같다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.processing.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ProcessingAcceptanceExpiryWorker {

    private final ProcessingJobRepository jobs;
    private final ProcessingJobService service;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.processing.expiry.interval-ms:60000}")
    public void expireUnansweredJobs() {
        for (var job : jobs.findTop100ByStatusAndAcceptExpiresAtLessThanEqualOrderById(
                ProcessingJobStatus.PAID, LocalDateTime.now(clock))) {
            try {
                // 작업 하나마다 별도 트랜잭션이다. 환불 하나가 실패해도 나머지는 정리된다.
                service.expireAcceptance(job.getId());
            } catch (RuntimeException exception) {
                log.error("Failed to expire acceptance for processing job {}", job.getId(), exception);
            }
        }
    }
}
