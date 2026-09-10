package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 확정된 온체인 트랜잭션을 {@code blockchain_transaction} 에 남긴다.
 *
 * <p>소재 발행·판매 등록·주문 생성이 모두 같은 일을 하므로 여기 모았다 — 각 도메인이
 * 따로 기록하면 {@code function_name} 이나 주소를 다르게 넣기 시작하고, 그러면
 * 검증 판정({@code MaterialVerificationStateResolver})이 조용히 어긋난다.
 *
 * <p><b>어느 컨트랙트인지는 서버가 정한다.</b> 호출부가 {@link Contract} 로 알려 주고
 * 실제 주소는 설정에서 온다. 화면이 보낸 주소를 쓰면 자기가 배포한 컨트랙트를 가리키는
 * 기록을 만들 수 있고, 체인에서 다시 확인하지 않는 한 구분되지 않는다.
 *
 * <p><b>서명자 주소도 서버가 찾는다.</b> 요청 바디의 지갑을 믿으면 남의 주소로 서명한
 * 것처럼 기록할 수 있다. 거래·판매글이 지갑 주소를 스냅샷으로 들고 있는 것과 같은 이유다.
 */
@Service
@RequiredArgsConstructor
public class ChainTransactionRecorder {

    /** 어느 컨트랙트를 불렀는가. 주소는 설정에서 꺼내므로 호출부는 이름만 안다. */
    public enum Contract {
        MATERIAL_TOKEN,
        MARKETPLACE,
        PRODUCTION_REGISTRY
    }

    private final BlockchainTransactionRepository transactionRepository;
    private final UserWalletRepository walletRepository;
    private final BlockchainProperties properties;
    private final Clock clock;

    /**
     * 온체인 연동이 설정되어 있는지. 설정 전에는 어떤 기록도 받지 않는다.
     *
     * <p>주소가 비어 있는데 기록을 받으면 {@code contract_address} 가 빈 문자열인 행이
     * 쌓이고, 나중에 그것이 어느 배포분이었는지 알 수 없다.
     */
    public void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }
    }

    /**
     * 한 건을 기록한다.
     *
     * @param signerId 서명한 사용자. 활성 지갑 주소를 여기서 찾는다
     * @param functionName 컨트랙트 함수 이름. 검증 판정이 이 값을 보므로 상수로만 넘긴다
     */
    public void record(
            Long signerId,
            Contract contract,
            String functionName,
            BlockchainReferenceType referenceType,
            Long referenceId,
            String txHash,
            Long blockNumber) {
        requireConfigured();

        String signerWallet = walletRepository
                .findByUserIdAndActiveTrue(signerId)
                .map(UserWallet::getWalletAddress)
                .orElseThrow(() -> new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID));

        try {
            transactionRepository.save(BlockchainTransaction.confirmed(
                    properties.chainId(),
                    addressOf(contract),
                    txHash,
                    referenceType,
                    referenceId,
                    functionName,
                    signerWallet,
                    blockNumber,
                    LocalDateTime.now(clock)));
        } catch (IllegalArgumentException invalid) {
            // 엔티티의 형식 검증(해시 길이, 주소 형식)을 도메인 오류로 바꾼다.
            // 그대로 흘리면 사용자에게 설명할 수 없는 500 이 된다.
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
        }
    }

    private String addressOf(Contract contract) {
        return switch (contract) {
            case MATERIAL_TOKEN -> properties.materialTokenAddress();
            case MARKETPLACE -> properties.marketplaceAddress();
            case PRODUCTION_REGISTRY -> properties.productionRegistryAddress();
        };
    }

    public Long chainId() {
        return properties.chainId();
    }

    public String materialTokenAddress() {
        return properties.materialTokenAddress();
    }
}
