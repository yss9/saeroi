package com.ssafy.upcycle.user.repository;

import com.ssafy.upcycle.user.entity.UserWallet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {

    /**
     * 주소로 <b>활성</b> 지갑을 찾는다. 로그인·검증 경로는 반드시 이걸 쓴다 —
     * 비활성 지갑은 없는 것과 같게 취급해야 한다.
     *
     * <p>인자는 정규화된(소문자) 주소여야 한다. {@code WalletAddresses.normalize} 참고.
     */
    Optional<UserWallet> findByWalletAddressAndActiveTrue(String walletAddress);

    /**
     * 활성 여부와 무관하게 주소로 찾는다. 남의 지갑 주소를 등록하려는 시도를 막을 때만 쓴다
     * — {@code wallet_address} 는 전역 UNIQUE 라 비활성 행도 자리를 차지한다.
     */
    Optional<UserWallet> findByWalletAddress(String walletAddress);

    /** 계정의 현재 활성 지갑. 교체 시 먼저 비활성화하려고 찾는다. */
    Optional<UserWallet> findByUserIdAndActiveTrue(Long userId);

    /** 계정의 지갑 이력 전체 (활성·비활성). 최신순. */
    List<UserWallet> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** 소유자 확인까지 겸하는 단건 조회. 남의 지갑 id 를 넣으면 빈 값이 온다. */
    Optional<UserWallet> findByIdAndUserId(Long id, Long userId);
}
