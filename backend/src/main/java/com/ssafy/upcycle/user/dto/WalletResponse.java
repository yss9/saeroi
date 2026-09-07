package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.user.entity.UserWallet;
import java.time.LocalDateTime;

/**
 * 지갑 한 건. 활성·비활성 모두 같은 모양으로 내려간다 — 화면이 이력을 보여줘야 하기 때문이다.
 *
 * <p>{@code walletAddress} 는 EIP-55 체크섬 형태로 내려준다. 저장은 소문자지만
 * 사용자가 MetaMask 에서 보는 형태와 같아야 대조할 수 있다.
 */
public record WalletResponse(
        Long id,
        String walletAddress,
        long chainId,
        boolean active,
        boolean verified,
        LocalDateTime verifiedAt,
        LocalDateTime createdAt,
        LocalDateTime deactivatedAt) {

    public static WalletResponse from(UserWallet wallet, String checksummedAddress) {
        return new WalletResponse(
                wallet.getId(),
                checksummedAddress,
                wallet.getChainId(),
                wallet.isActive(),
                wallet.isVerified(),
                wallet.getVerifiedAt(),
                wallet.getCreatedAt(),
                wallet.getDeactivatedAt());
    }
}
