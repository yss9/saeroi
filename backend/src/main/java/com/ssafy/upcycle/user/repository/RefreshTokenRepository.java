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
}
