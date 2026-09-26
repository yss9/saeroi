package com.ssafy.upcycle.user.repository;

import com.ssafy.upcycle.user.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 같은 토큰으로 동시에 재발급해도 단 하나만 성공하게 하는 조건부 폐기다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
               set token.revokedAt = :now
             where token.id = :id
               and token.revokedAt is null
               and token.expiresAt > :now
            """)
    int revokeIfActive(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 한 사용자의 살아 있는 Refresh Token 을 한 번에 폐기한다. 비밀번호를 바꾼 뒤 부른다.
     *
     * <p><b>모든 기기가 대상이다.</b> 비밀번호를 바꾸는 이유 중 하나가 계정 탈취 의심인데,
     * 바꾼 기기의 세션만 정리하면 침입자의 세션은 그대로 살아 있다.
     *
     * <p>이미 폐기됐거나 만료된 행은 건드리지 않는다 — {@code revoked_at} 을 덮어쓰면 처음
     * 폐기된 시각을 잃는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken token
               set token.revokedAt = :now
             where token.userId = :userId
               and token.revokedAt is null
               and token.expiresAt > :now
            """)
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
