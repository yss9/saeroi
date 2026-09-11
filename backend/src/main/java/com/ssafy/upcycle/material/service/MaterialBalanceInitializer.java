package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 등록자에게 최초 보유 행을 만든다.
 *
 * <p><b>두 발행 경로가 같은 규칙을 쓰게 하려고 뽑았다.</b> 오프체인 발행은 등록 트랜잭션
 * 안에서, 온체인 발행은 mint 가 확정된 뒤에 이 행을 만든다 — 시점은 다르지만 "등록자의 활성
 * 지갑으로 전량을 보유한다" 는 규칙은 같아야 한다. 복사해 두면 한쪽만 고쳐지고, 그러면 같은
 * 소재가 발행 방식에 따라 다른 주소로 기록된다.
 */
@Component
@RequiredArgsConstructor
public class MaterialBalanceInitializer {

    private final MaterialBalanceRepository balanceRepository;
    private final UserWalletRepository walletRepository;

    /**
     * 보유 행을 만든다. <b>이미 있으면 아무것도 하지 않는다.</b>
     *
     * <p>두 번 부르면 같은 소재에 보유 행이 둘 생기고, 잔량 조회가 그 둘을 각각 세어
     * 실제보다 많은 수량을 답한다.
     *
     * <p><b>지갑이 없으면 발행하지 않는다.</b> {@code material_balance.wallet_address} 가
     * {@code NOT NULL} 이라 기술적으로도 값이 필요하지만, 그보다 <b>소재를 소유하려면 지갑이
     * 있어야 한다</b>는 규칙이 이미 서 있다 — 거래 생성도 양쪽 활성 지갑을 요구한다.
     */
    public void open(Long materialId, Long ownerId, long quantity) {
        if (balanceRepository.findByMaterialIdAndOwnerId(materialId, ownerId).isPresent()) {
            return;
        }
        String walletAddress = walletRepository
                .findByUserIdAndActiveTrue(ownerId)
                .map(UserWallet::getWalletAddress)
                .orElseThrow(() -> new BusinessException(MaterialErrorCode.MATERIAL_WALLET_NOT_LINKED));

        balanceRepository.save(MaterialBalance.of(materialId, ownerId, walletAddress, quantity));
    }
}
