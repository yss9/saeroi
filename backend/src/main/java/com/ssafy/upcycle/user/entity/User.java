package com.ssafy.upcycle.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 플랫폼 계정.
 *
 * <p><b>{@code BaseEntity} 를 상속하지 않는다.</b> {@code users} 는 {@code created_at} 과
 * {@code updated_at} 을 모두 갖고 있어 상속해도 컬럼은 맞지만, {@code Material} 이 세운 선례대로
 * 감사 필드를 엔티티에 직접 선언한다. {@code UserSchemaMappingTests} 가 선언된 필드의
 * {@code @Column} 만 훑어 schema.sql 과 대조하므로, 상속으로 감추면 그 대조가 무력해진다.
 *
 * <p><b>{@code password} 는 매핑하되 절대 밖으로 내보내지 않는다.</b> 스키마 대조 테스트가
 * 컬럼 일치를 요구하므로 필드는 있어야 하지만, 어떤 DTO 에도 담지 않고 로그에도 남기지 않는다.
 * 그래서 이 클래스에는 {@code @ToString} 이 없다.
 *
 * <p>지갑 주소는 여기 두지 않는다. ERD 상 {@code user_wallet} 이 1:1 로 분리되어 있고,
 * 지갑이 없는 CONSUMER 계정이 존재하기 때문이다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    private static final int EMAIL_MAX_LENGTH = 255;
    private static final int PASSWORD_MAX_LENGTH = 255;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int PHONE_MAX_LENGTH = 20;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = EMAIL_MAX_LENGTH, unique = true)
    private String email;

    /** BCrypt 해시. 노출 금지 — 클래스 주석 참고. */
    @Column(name = "password", nullable = false, length = PASSWORD_MAX_LENGTH)
    private String password;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "phone", nullable = false, length = PHONE_MAX_LENGTH)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private User(String email, String encodedPassword, String name, String phone, UserRole role) {
        this.email = requireText(email, "email", EMAIL_MAX_LENGTH);
        this.password = requireText(encodedPassword, "password", PASSWORD_MAX_LENGTH);
        this.name = requireText(name, "name", NAME_MAX_LENGTH);
        this.phone = requireText(phone, "phone", PHONE_MAX_LENGTH);
        this.role = requireNonNull(role, "role");
        this.status = UserStatus.ACTIVE;
    }

    /**
     * 계정 생성.
     *
     * <p><b>지갑 로그인 흐름은 이 메서드를 부르지 않는다.</b> 미등록 지갑은 자동 가입시키지 않고
     * {@code WALLET_NOT_LINKED} 로 거부하는 것이 확정된 정책이다. 지금 이 팩토리를 쓰는 곳은
     * 테스트와 수동 시드뿐이며, 회원가입 API 가 생기면 그쪽이 호출부가 된다.
     *
     * @param encodedPassword 이미 인코딩된 비밀번호. 평문을 넘기지 말 것.
     */
    public static User register(
            String email, String encodedPassword, String name, String phone, UserRole role) {
        return new User(email, encodedPassword, name, phone, role);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * 계정을 정지시킨다. 정지된 계정은 지갑 서명 로그인도 거부된다.
     *
     * <p>아직 이걸 부르는 운영 경로(관리자 API)는 없다. 그래도 두는 이유는
     * {@code users.status} 가 스키마에 존재하고 로그인이 그 값을 실제로 검사하기 때문이다 —
     * 검사만 있고 상태를 만들 방법이 없으면 그 분기를 검증할 수 없다.
     */
    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("User " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("User " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "User " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
