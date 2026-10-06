package com.ssafy.upcycle.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.entity.BlockchainOperation;
import com.ssafy.upcycle.blockchain.repository.BlockchainOperationRepository;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;

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
 *
 * <h2>해시를 체인에서 확인한 뒤에만 적는다</h2>
 *
 * <p>여기까지는 "주소와 서명자를 서버가 정한다" 였고, <b>해시 자체는 확인하지 않았다.</b>
 * 그래서 인증된 소유자라면 누구나 존재하지 않는 해시를 보낼 수 있었다 — 2026-09-21 점검에서
 * 실제로 체인에 없는 해시 3건을 넣었고({@code eth_getTransactionReceipt} → null),
 * 그 행들이 공개 QR 여권의 "블록체인 증명" 으로 그대로 렌더링됐다.
 * {@code blockchainVerified} 가 이 행들로 판정되므로 <b>여권의 핵심 주장이 위조 가능</b>했다.
 *
 * <p>이제 저장 전에 영수증을 가져와 네 가지를 확인한다 — 존재·성공({@code status=0x1})·
 * 목적지 컨트랙트·서명자. 넷 중 하나라도 어긋나면 기록하지 않는다. 검증 자체는
 * {@link ProcessingReceiptVerifier} 가 처리 도메인에서 이미 하던 것과 같은 방식이고,
 * 이 클래스를 거치는 모든 도메인(소재 발행·출처검증·거래·생산)에 같은 기준을 준다.
 *
 * <p><b>블록번호도 영수증에서 가져온다.</b> 예전에는 요청 바디의 값을 그대로 적었는데,
 * 해시를 확인하면서 블록번호만 클라이언트를 믿을 이유가 없다.
 */
@Service
@RequiredArgsConstructor
public class ChainTransactionRecorder {

    /** 어느 컨트랙트를 불렀는가. 주소는 설정에서 꺼내므로 호출부는 이름만 안다. */
    public enum Contract {
        MATERIAL_TOKEN,
        MARKETPLACE,
        PROCESSING_REGISTRY,
        PRODUCTION_REGISTRY
    }

    private final BlockchainTransactionRepository transactionRepository;
    private final BlockchainOperationRepository operationRepository;
    private final UserWalletRepository walletRepository;
    private final BlockchainProperties properties;
    private final BlockchainJsonRpcClient rpc;
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
     * 한 건을 기록한다. <b>영수증을 확인한 뒤에만 적는다.</b>
     *
     * @param signerId 서명한 사용자. 활성 지갑 주소를 여기서 찾고, 영수증의 {@code from} 과
     *     같아야 한다
     * @param functionName 컨트랙트 함수 이름. 검증 판정이 이 값을 보므로 상수로만 넘긴다
     * @throws BusinessException 해시가 체인에 없거나, 실패한 트랜잭션이거나, 다른 컨트랙트·
     *     다른 서명자의 것이면 {@link BlockchainErrorCode#BLOCKCHAIN_LINK_INVALID}
     */
    @Transactional
    public void record(
            Long signerId,
            Contract contract,
            String functionName,
            BlockchainReferenceType referenceType,
            Long referenceId,
            String txHash) {
        requireConfigured();
        String contractAddress = addressOf(contract);
        if (contractAddress == null || contractAddress.length() != 42 || !contractAddress.startsWith("0x")) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }

        String signerWallet = walletRepository
                .findByUserIdAndActiveTrue(signerId)
                .map(UserWallet::getWalletAddress)
                .orElseThrow(() -> new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID));

        BlockchainOperation operation = operationRepository.findByTxHash(txHash.toLowerCase()).orElse(null);
        VerifiedReceipt verified = verifiedReceipt(txHash, contractAddress, signerWallet, operation);

        try {
            transactionRepository.save(BlockchainTransaction.confirmed(
                    properties.chainId(),
                    contractAddress,
                    txHash,
                    referenceType,
                    referenceId,
                    functionName,
                    signerWallet,
                    verified.blockNumber(),
                    verified.logIndex(),
                    LocalDateTime.now(clock)));
            if (operation != null) {
                LocalDateTime now = LocalDateTime.now(clock);
                operation.confirmed(verified.blockNumber(), verified.logIndex(), now);
                operation.projected(now);
            }
        } catch (IllegalArgumentException invalid) {
            // 엔티티의 형식 검증(해시 길이, 주소 형식)을 도메인 오류로 바꾼다.
            // 그대로 흘리면 사용자에게 설명할 수 없는 500 이 된다.
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
        }
    }

    /**
     * 영수증을 가져와 이 기록이 주장하는 것과 맞는지 확인하고, 확정된 블록번호를 돌려준다.
     *
     * <p>영수증이 아직 안 보이는 경우는 {@link BlockchainJsonRpcClient} 가 재시도로 흡수한다 —
     * 화면이 영수증을 받고 곧바로 알려 오므로 보통 한 번에 잡히지만, 노드가 조금 늦을 수 있다.
     * 끝내 안 보이면 거기서 {@code BLOCKCHAIN_LINK_INVALID} 가 난다.
     */
    private VerifiedReceipt verifiedReceipt(String txHash, String contractAddress, String signerWallet,
            BlockchainOperation operation) {
        JsonNode receipt = rpc.transactionReceipt(txHash);
        if (!"0x1".equals(receipt.path("status").asText())) {
            // 리버트된 트랜잭션도 해시는 남는다. 증명으로 적으면 "실패한 발행" 이 성공한
            // 것처럼 보인다.
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
        }
        requireSameAddress(contractAddress, receipt.path("to").asText());
        requireSameAddress(signerWallet, receipt.path("from").asText());
        int logIndex = 0;
        if (operation != null) {
            if (!operation.getContractAddress().equalsIgnoreCase(contractAddress)
                    || !operation.getActorWallet().equalsIgnoreCase(signerWallet)) {
                throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
            }
            JsonNode matched = null;
            String topic = Hash.sha3String("OperationRecorded(bytes32,bytes4,address)");
            for (JsonNode log : receipt.path("logs")) {
                JsonNode topics = log.path("topics");
                if (contractAddress.equalsIgnoreCase(log.path("address").asText())
                        && topics.isArray() && topics.size() >= 2
                        && topic.equalsIgnoreCase(topics.get(0).asText())
                        && operation.getOperationId().equalsIgnoreCase(topics.get(1).asText())
                        && topics.size() >= 3
                        && topics.get(2).asText().length() >= 10
                        && operation.getExpectedSelector().equalsIgnoreCase(topics.get(2).asText().substring(0, 10))) {
                    matched = log; break;
                }
            }
            if (matched == null) throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
            logIndex = Math.toIntExact(hexToLong(matched.path("logIndex").asText()));
        }
        return new VerifiedReceipt(hexToLong(receipt.path("blockNumber").asText()), logIndex);
    }

    private void requireSameAddress(String expected, String actual) {
        if (expected == null || actual == null || !expected.equalsIgnoreCase(actual)) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
        }
    }

    private long hexToLong(String value) {
        try {
            String raw = value.startsWith("0x") ? value.substring(2) : value;
            return new java.math.BigInteger(raw, 16).longValueExact();
        } catch (RuntimeException malformed) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_LINK_INVALID);
        }
    }

    private String addressOf(Contract contract) {
        return switch (contract) {
            case MATERIAL_TOKEN -> properties.materialTokenAddress();
            case MARKETPLACE -> properties.marketplaceAddress();
            case PROCESSING_REGISTRY -> properties.processingRegistryAddress();
            case PRODUCTION_REGISTRY -> properties.productionRegistryAddress();
        };
    }

    public Long chainId() {
        return properties.chainId();
    }

    public String materialTokenAddress() {
        return properties.materialTokenAddress();
    }

    private record VerifiedReceipt(long blockNumber, int logIndex) {}
}
