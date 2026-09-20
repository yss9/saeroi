package com.ssafy.upcycle.processing.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브랜드가 특정 가공사에게 보낸 가공 요청 한 건.
 *
 * <h2>{@code expiresAt} 하나가 두 기한을 맡는다</h2>
 *
 * <p>요청이 살아 있는 동안 기다리는 것이 차례로 둘이다 — 먼저 <b>브랜드의 결제</b>, 그다음
 * <b>가공사의 수락</b>이다. 컬럼을 둘로 나눌 수도 있지만, 어느 시점에도 기다리는 것은
 * 하나뿐이라 "지금 무엇을 기다리는가" 는 상태가 말해 주고 "언제까지인가" 는 이 값 하나면
 * 된다. 만료 배치도 컬럼 하나만 본다.
 *
 * <p>결제가 승인되면 {@link #awaitAcceptance}가 이 값을 수락 기한으로 밀어 준다. 그 전까지는
 * 결제 기한이다.
 */
@Getter
@Entity
@Table(name = "processing_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "request_code", nullable = false, length = 30, unique = true)
    private String requestCode;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "processor_id", nullable = false)
    private Long processorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProcessingRequestStatus status;

    /** 지금 기다리는 것의 마감 시각. 결제 전에는 결제 기한, 결제 후에는 수락 기한이다. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 요청을 만든다.
     *
     * <p><b>만료 시각을 호출부가 준다.</b> 예전에는 여기서 7일을 더했지만, 지금은 요청과
     * 동시에 작업이 만들어지고 그 작업의 결제 기한이 곧 이 요청의 기한이다 — 두 곳에서 각자
     * 기한을 만들면 결제창이 살아 있는데 요청이 먼저 죽거나 그 반대가 된다.
     */
    public static ProcessingRequest create(
            Long brandId, Long processorId, LocalDateTime now, LocalDateTime expiresAt) {
        if (brandId == null || processorId == null || now == null || expiresAt == null
                || brandId.equals(processorId) || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException(
                    "Distinct brand and processor, creation time and a future deadline are required");
        }
        ProcessingRequest request = new ProcessingRequest();
        request.brandId = brandId;
        request.processorId = processorId;
        request.requestCode = "PR" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
        request.status = ProcessingRequestStatus.REQUESTED;
        request.createdAt = now;
        request.expiresAt = expiresAt;
        return request;
    }

    public boolean isDue(LocalDateTime now) {
        return status == ProcessingRequestStatus.REQUESTED && !expiresAt.isAfter(now);
    }

    /**
     * 결제가 끝났다. 이제부터는 가공사의 답을 기다린다.
     *
     * <p>상태는 그대로 {@code REQUESTED} 다 — 결제는 브랜드 쪽 사정이고, 요청이 받아들여졌는지는
     * 아직 아무도 답하지 않았다. 바뀌는 것은 <b>무엇을 기다리는가</b>뿐이라 기한만 민다.
     */
    public void awaitAcceptance(LocalDateTime acceptDeadline) {
        if (status != ProcessingRequestStatus.REQUESTED) {
            throw new IllegalStateException("Only a pending request may wait for acceptance");
        }
        if (acceptDeadline == null) {
            throw new IllegalArgumentException("Acceptance deadline is required");
        }
        this.expiresAt = acceptDeadline;
    }

    /** 가공사가 받았다. */
    public void accept() {
        if (status != ProcessingRequestStatus.REQUESTED) {
            throw new IllegalStateException("Only a pending request may be accepted");
        }
        status = ProcessingRequestStatus.ACCEPTED;
    }

    public void terminate(ProcessingRequestStatus target) {
        if (target != ProcessingRequestStatus.CANCELED && target != ProcessingRequestStatus.REJECTED
                && target != ProcessingRequestStatus.EXPIRED) {
            throw new IllegalArgumentException("Not a terminal request status");
        }
        if (status != ProcessingRequestStatus.REQUESTED) {
            throw new IllegalStateException("Only a pending request may terminate");
        }
        status = target;
    }
}
