package com.ssafy.upcycle.processing.service;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="app.processing.expiry.enabled", havingValue="true", matchIfMissing=true)
public class ProcessingJobExpiryWorker {
    private final ProcessingJobRepository jobs; private final ProcessingJobService service; private final Clock clock;
    @Scheduled(fixedDelayString="${app.processing.expiry.interval-ms:60000}")
    public void expireDueJobs() {
        for (var job:jobs.findTop100ByStatusAndPaymentExpiresAtLessThanEqualOrderById(
                ProcessingJobStatus.CREATED, LocalDateTime.now(clock))) {
            try { service.expire(job.getId()); } catch(RuntimeException e) { log.error("Failed to expire processing Job {}",job.getId(),e); }
        }
    }
}
