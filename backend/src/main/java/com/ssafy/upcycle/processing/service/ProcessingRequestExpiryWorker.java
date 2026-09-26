package com.ssafy.upcycle.processing.service;

import com.ssafy.upcycle.processing.entity.ProcessingRequestStatus;
import com.ssafy.upcycle.processing.repository.ProcessingRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.processing.expiry.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ProcessingRequestExpiryWorker {
    private final ProcessingRequestRepository requests;
    private final ProcessingRequestService service;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.processing.expiry.interval-ms:60000}")
    public void expireDueRequests() {
        for (var request : requests.findTop100ByStatusAndExpiresAtLessThanEqualOrderById(
                ProcessingRequestStatus.REQUESTED, LocalDateTime.now(clock))) {
            try {
                // A separate transaction per request; one failure does not roll back the batch.
                service.expire(request.getId());
            } catch (RuntimeException exception) {
                log.error("Failed to expire processing request {}", request.getId(), exception);
            }
        }
    }
}
