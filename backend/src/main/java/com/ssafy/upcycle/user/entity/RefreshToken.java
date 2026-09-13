package com.ssafy.upcycle.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 액세스 토큰 재발급에 사용하는 장기 토큰.
 *
 * <p>원문은 로그인 응답으로 한 번만 전달하고 DB에는 SHA-256 해시만 저장한다. 탈취된 DB만으로
 * 세션을 재현하지 못하게 하기 위한 경계다. 재발급할 때는 기존 행을 폐기하고 새 행을 발급하는
 * rotation 방식을 사용한다.
 *
 * <p>{@code refresh_token}에는 {@code updated_at}이 없으므로 {@code BaseEntity}를 상속하지 않는다.
 */
@Getter
@Entity
@Table(name = "refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {

    public static final int HASH_LENGTH = 64;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 255, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private RefreshToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
        if (userId == null) {
            throw new IllegalArgumentException("Refresh token user id must not be null");
        }
        if (tokenHash == null || tokenHash.length() != HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "Refresh token hash must be exactly " + HASH_LENGTH + " characters");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Refresh token expiry must not be null");
        }
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(Long userId, String tokenHash, LocalDateTime expiresAt) {
        return new RefreshToken(userId, tokenHash, expiresAt);
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
