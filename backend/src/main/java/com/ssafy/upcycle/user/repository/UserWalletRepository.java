package com.ssafy.upcycle.user.repository;

import com.ssafy.upcycle.user.entity.UserWallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {

    /** 인자는 반드시 정규화된(소문자) 주소여야 한다. {@code WalletAddresses.normalize} 참고. */
    Optional<UserWallet> findByWalletAddress(String walletAddress);
}
