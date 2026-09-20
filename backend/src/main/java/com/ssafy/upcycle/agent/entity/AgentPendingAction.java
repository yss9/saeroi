package com.ssafy.upcycle.agent.entity;

import com.ssafy.upcycle.common.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 에이전트가 사용자에게 넘긴 일. <b>이 설계의 안전 경계다.</b>
 *
 * <p>모델은 소재를 사거나 견적을 확정하는 일을 직접 실행하지 않는다. 대신 이 행을 만들고,
 * 프론트가 대화 말풍선 안에 카드로 그리고, 사용자가 누른 뒤에야 실제 도메인 서비스가 돈다.
 * 이유는 두 가지다.
 *
 * <ul>
 *   <li>지갑 서명과 결제는 <b>원리적으로</b> 서버가 대신할 수 없다. 개인키는 브라우저에만
 *       있고 PG 승인은 사용자가 해야 한다.
 *   <li>서명이 필요 없는 쓰기라도 모델의 오해 한 번으로 돈이 나가면 안 된다. 그래서
 *       {@link PendingActionType#CONFIRM} 을 따로 두었다.
 * </ul>
 *
 * <p>{@code expiresAt} 은 오래 방치된 제안이 나중에 되살아나는 것을 막는다. 사용자가
 * 어제 열어둔 카드를 오늘 눌렀을 때 그 사이 재고나 가격이 바뀌었을 수 있다.
 */
@Getter
@Entity
@Table(
        name = "agent_pending_action",
        indexes =
                @Index(
                        name = "idx_agent_pending_action_conversation",
                        columnList = "conversation_id,status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentPendingAction extends BaseEntity {

    private static final int SUMMARY_MAX_LENGTH = 500;
    private static final int TOOL_NAME_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action_type", nullable = false, length = 40)
    private PendingActionType actionType;

    /** 사용자가 승인했을 때 실제로 실행할 tool. */
    @Column(name = "tool_name", nullable = false, length = TOOL_NAME_MAX_LENGTH)
    private String toolName;

    /** 카드에 그대로 보여줄 한 줄 설명. 사용자가 무엇에 동의하는지 알 수 있어야 한다. */
    @Column(nullable = false, length = SUMMARY_MAX_LENGTH)
    private String summary;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PendingActionStatus status;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    /**
     * 이 카드가 요구하는 서명 횟수. 단발은 1.
     *
     * <p>생산 확정처럼 컨트랙트가 순서를 강제하는 연속 서명을 하나의 카드로 묶기 위한 값이다
     * (`createBatch → submitPlanAndLock → confirmProduction → linkProductItems`).
     */
    @Column(name = "step_total", nullable = false)
    private int stepTotal;

    /**
     * 지금까지 보고된 횟수. <b>정본이 아니라 표시용이다.</b>
     *
     * <p>진행 상태의 정본은 체인이다 — 트랜잭션은 성공했는데 서버 보고가 실패하면 이 값이
     * 체인보다 뒤처진다. 그 상태에서 이 값을 믿고 다음 단계를 정하면 이미 끝난 단계를 다시
     * 서명해 revert 한다. 프론트가 카드를 그릴 때마다 온체인 상태를 읽어 다음 단계를 정하고,
     * 이 값은 대화 이력과 화면 표시에만 쓴다.
     */
    @Column(name = "step_done", nullable = false)
    private int stepDone;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static AgentPendingAction propose(
            Long conversationId,
            Long messageId,
            PendingActionType actionType,
            String toolName,
            String summary,
            String payloadJson,
            int stepTotal,
            LocalDateTime expiresAt) {
        AgentPendingAction action = new AgentPendingAction();
        action.conversationId = requirePositive(conversationId, "conversation id");
        action.messageId = requirePositive(messageId, "message id");
        action.actionType = requireNonNull(actionType, "action type");
        action.toolName = requireText(toolName, TOOL_NAME_MAX_LENGTH, "tool name");
        action.summary = requireText(summary, SUMMARY_MAX_LENGTH, "summary");
        action.payloadJson = requireText(payloadJson, Integer.MAX_VALUE, "payload");
        action.status = PendingActionStatus.PENDING;
        action.stepTotal = stepTotal <= 0 ? 1 : stepTotal;
        action.stepDone = 0;
        action.expiresAt = requireNonNull(expiresAt, "expires at");
        return action;
    }

    /**
     * 중간 단계 하나가 끝났다. 카드는 {@code PENDING} 으로 남는다.
     *
     * <p>마지막 단계면 {@link #complete} 를 대신 부른다 — 여기서 자동으로 완료시키지 않는
     * 이유는, 완료 처리에 도메인 서비스 호출이 함께 일어나야 하기 때문이다.
     */
    public void advanceStep(LocalDateTime now) {
        requirePending(now);
        if (stepDone + 1 >= stepTotal) {
            throw new IllegalStateException("Last step must go through complete()");
        }
        this.stepDone++;
    }

    /** 진행 기록을 갈아끼운다. 카드 상태는 건드리지 않는다. */
    public void replaceResult(String resultJson) {
        this.resultJson = resultJson;
    }

    public boolean isLastStep() {
        return stepDone + 1 >= stepTotal;
    }

    public boolean isMultiStep() {
        return stepTotal > 1;
    }

    /**
     * 사용자가 수행한 결과를 받는다.
     *
     * <p>{@code now} 를 받아 만료를 여기서 판정하는 이유는, 호출자가 잊고 지나가면 만료된
     * 제안이 그대로 실행되기 때문이다. 판정을 엔티티 안에 두면 빠뜨릴 수 없다.
     */
    public void complete(String resultJson, LocalDateTime now) {
        requirePending(now);
        this.resultJson = resultJson;
        this.stepDone = stepTotal;
        this.status = PendingActionStatus.COMPLETED;
    }

    public void reject(LocalDateTime now) {
        requirePending(now);
        this.status = PendingActionStatus.REJECTED;
    }

    /** 다른 경로로 이미 처리됐다. 대기 중인 카드만 닫는다 — 끝난 카드를 다시 건드리지 않는다. */
    public void markStale() {
        if (status == PendingActionStatus.PENDING) {
            this.status = PendingActionStatus.STALE;
        }
    }

    public void expire() {
        if (status == PendingActionStatus.PENDING) {
            this.status = PendingActionStatus.EXPIRED;
        }
    }

    public boolean isPending() {
        return status == PendingActionStatus.PENDING;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    private void requirePending(LocalDateTime now) {
        if (status != PendingActionStatus.PENDING) {
            throw new IllegalStateException("Pending action is already " + status);
        }
        if (isExpiredAt(requireNonNull(now, "now"))) {
            this.status = PendingActionStatus.EXPIRED;
            throw new IllegalStateException("Pending action has expired");
        }
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Pending action " + field + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Pending action " + field + " is invalid");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Pending action " + field + " must not be null");
        }
        return value;
    }
}
