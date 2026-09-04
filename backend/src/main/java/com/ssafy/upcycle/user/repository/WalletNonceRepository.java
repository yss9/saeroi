package com.ssafy.upcycle.user.repository;

import com.ssafy.upcycle.user.entity.WalletNonce;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletNonceRepository extends JpaRepository<WalletNonce, Long> {

    /**
     * 주소와 nonce를 함께 조건으로 건다. {@code findByNonce} 가 아니다 —
     * 지갑 A 에게 발급한 nonce 를 지갑 B 로 제출하는 경로를 서명 검증 이전에 끊는다.
     */
    Optional<WalletNonce> findByWalletAddressAndNonce(String walletAddress, String nonce);

    /** 새 nonce 를 낼 때 이전에 발급했던 미사용분을 지운다. 지갑당 살아 있는 nonce 는 하나. */
    long deleteByWalletAddressAndUsedAtIsNull(String walletAddress);

    /**
     * 등록 경로용. 아직 계정에 붙지 않은 주소라 주소 기준으로 정리할 수 없어 사용자 기준으로 지운다.
     * 사용자당 살아 있는 등록 nonce 도 하나로 유지된다.
     */
    long deleteByUserIdAndUsedAtIsNull(Long userId);

    /**
     * nonce 를 소모한다. <b>1회용 보장의 근거가 이 한 문장이다.</b>
     *
     * <p>{@code used_at is null} 조건이 붙은 UPDATE 라 InnoDB 가 행 잠금을 잡고,
     * 영향 행 수가 곧 "내가 소모에 성공했는가" 다. 애플리케이션 락도 {@code SELECT ... FOR UPDATE}
     * 도 없이 다중 인스턴스에서 성립한다. 반환값이 1 이 아니면 다른 요청이 먼저 가져간 것이다.
     *
     * <p>⚠️ {@code clearAutomatically = true} 라 호출 직후 영속성 컨텍스트가 비워진다.
     * 이 호출 <b>이후에</b> 로드한 엔티티만 dirty checking 이 동작한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WalletNonce n set n.usedAt = :now where n.id = :id and n.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now);
}
