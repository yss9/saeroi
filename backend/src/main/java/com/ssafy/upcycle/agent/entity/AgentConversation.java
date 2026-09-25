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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * BRAND 사용자의 여정 하나. 사용자가 끝낼 때까지 살아 있다.
 *
 * <p>ERD 관례대로 {@code @ManyToOne} 을 쓰지 않고 FK id 를 그대로 들고 있다.
 *
 * <p>{@code requirementJson} 은 대화에서 모아낸 요구사항 스냅샷이다. 이력을 다시 읽어
 * 추론하지 않고 여기에 요약해 두는 이유는, 대화가 길어지면 컨텍스트 윈도우를 넘겨 앞부분을
 * 잘라내야 하는데 그때 "무엇을 만들기로 했는지"까지 같이 사라지면 안 되기 때문이다.
 */
@Getter
@Entity
@Table(
        name = "agent_conversation",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_agent_conversation_active",
                        columnNames = "active_brand_user_id"),
        indexes =
                @Index(
                        name = "idx_agent_conversation_brand",
                        columnList = "brand_user_id,updated_at,id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentConversation extends BaseEntity {

    private static final int TITLE_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brand_user_id", nullable = false)
    private Long brandUserId;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private JourneyPhase phase;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ConversationStatus status;

    @Column(name = "requirement_json", columnDefinition = "TEXT")
    private String requirementJson;

    /**
     * 담당자당 활성 대화가 하나임을 DB 가 보장하게 하는 생성 컬럼.
     *
     * <p>MySQL 8 에는 부분 유니크 인덱스가 없어, {@code user_wallet.active_user_id} 와 같은
     * 방법을 쓴다 — 상태가 ACTIVE 일 때만 값이 채워지고 거기에 UNIQUE 를 건다. 서비스에서
     * "있으면 재사용" 으로 거르지만, 동시 요청 두 개가 같이 통과하는 경합을 막는 것은 이쪽이다.
     *
     * <p>DB 가 계산하므로 우리는 쓰지 않는다.
     */
    @Column(name = "active_brand_user_id", insertable = false, updatable = false)
    private Long activeBrandUserId;

    public static AgentConversation start(Long brandUserId, String title) {
        AgentConversation conversation = new AgentConversation();
        conversation.brandUserId = requirePositive(brandUserId, "brand user id");
        conversation.title = requireText(title, "title");
        conversation.phase = JourneyPhase.INTAKE;
        conversation.status = ConversationStatus.ACTIVE;
        return conversation;
    }

    /**
     * 단계를 옮긴다.
     *
     * <p>뒤로 가는 것을 막지 않는다. 사용자가 "소재부터 다시 고를래" 라고 말하는 것은 정상
     * 흐름이고, 그때 단계를 되돌리지 못하면 필요한 tool 이 노출되지 않아 대화가 막힌다.
     */
    public void moveTo(JourneyPhase next) {
        requireActive();
        this.phase = requireNonNull(next, "phase");
    }

    /**
     * 앞으로만 옮긴다. 이미 그 단계이거나 더 나아갔으면 그대로 둔다.
     *
     * <p>승인한 카드가 프로젝트를 다음 단계로 들였을 때 쓴다(소재를 사면 소재 단계, 가공을
     * 맡기면 가공 단계 …). 도구가 단계로 가려지지 않게 된 뒤로 모델은 {@code set_phase} 를
     * 부를 이유가 줄었고, 그대로 두면 진행 막대가 실제 진행보다 뒤처진다. 뒤로 돌리지 않는
     * 이유는 생산 중에 소재를 더 사는 일이 흔하기 때문이다 — 그때 막대가 소재로 돌아가면
     * 담당자는 생산이 사라진 줄 안다.
     */
    public void advanceTo(JourneyPhase reached) {
        requireActive();
        requireNonNull(reached, "phase");
        if (phase != JourneyPhase.DONE && reached.ordinal() > phase.ordinal()) {
            this.phase = reached;
        }
    }

    public void updateRequirement(String requirementJson) {
        requireActive();
        this.requirementJson = requirementJson;
    }

    public void rename(String title) {
        this.title = requireText(title, "title");
    }

    public void complete() {
        requireActive();
        this.status = ConversationStatus.COMPLETED;
        this.phase = JourneyPhase.DONE;
    }

    public void abandon() {
        requireActive();
        this.status = ConversationStatus.ABANDONED;
    }

    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    public boolean isOwnedBy(Long userId) {
        return brandUserId.equals(userId);
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("Conversation is no longer active");
        }
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Conversation " + field + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Conversation " + field + " must be 1.." + TITLE_MAX_LENGTH + " characters");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Conversation " + field + " must not be null");
        }
        return value;
    }
}
