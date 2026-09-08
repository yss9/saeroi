package com.ssafy.upcycle.blockchain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 온체인 트랜잭션 한 건. 테이블은 {@code schema.sql} 에 이미 있었고 매핑만 없었다.
 *
 * <h2>쓰기 주체가 하나 생겼다</h2>
 *
 * <p>원래 두 경로를 예정했다.
 *
 * <ol>
 *   <li><b>Tx 를 보내는 경로</b> — 지금 {@link #confirmed} 가 이것이다. 다만 서버가
 *       보내지 않는다. <b>서명은 사용자 지갑이 하므로 서버에는 개인키가 없고</b>, 화면이
 *       영수증을 받은 뒤 결과만 알려 준다. 그래서 {@code PENDING} 을 거치지 않는다 —
 *       서버가 아는 시점에는 이미 확정되어 있다.</li>
 *   <li>이벤트 리스너 — 우리 서버를 거치지 않은 직접 호출을 줍는다 (S15P21E104-205).
 *       아직 없다. 그것이 생기면 {@code PENDING} 과 실패 기록이 함께 필요해진다.</li>
 * </ol>
 *
 * <p>그래서 팩토리를 <b>확정된 트랜잭션 하나만</b> 만들 수 있게 좁혀 두었다. 상태 전이
 * 메서드를 두지 않은 것도 같은 이유다 — 확정된 사실은 바뀌지 않는다.
 *
 * <h2>UNIQUE {@code (chain_id, tx_hash, log_index)} 가 이 테이블의 핵심</h2>
 *
 * <p>같은 이벤트를 두 번 적재하는 것을 DB 가 막는다. 리스너는 재시작하면 이미 처리한
 * 블록을 다시 읽을 수 있으므로 애플리케이션 판정만으로는 부족하다.
 *
 * @see com.ssafy.upcycle.blockchain.service.BlockchainVerificationService
 */
@Getter
@Entity
@Table(name = "blockchain_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockchainTransaction {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chain_id", nullable = false)
    private Long chainId;

    @Column(name = "contract_address", nullable = false, length = 42)
    private String contractAddress;

    /**
     * 트랜잭션 해시. {@code 0x} + 64 hex 로 <b>길이가 항상 정확히 66</b> 이라
     * {@code schema.sql} 이 {@code CHAR(66)} 으로 잡아 두었다.
     *
     * <p>{@code length = 66} 만 적으면 Hibernate 는 {@code VARCHAR(66)} 을 기대해
     * 운영 프로파일({@code ddl-auto: validate})에서 <b>기동 자체가 실패한다.</b>
     * 2026-09-07 backend/master #10 이 이것 때문에 헬스체크 실패로 롤백됐다.
     * 테스트 프로파일은 {@code create-drop} 이라 엔티티가 시키는 대로 테이블을
     * 만들어버려 드러나지 않았다.
     *
     * <p>그래서 {@code columnDefinition} 으로 실제 타입을 못박는다 —
     * {@code WalletNonce.nonce} · {@code EvidenceFile.fileHash} ·
     * {@code Material.evidenceHash} 가 이미 쓰는 방식이다.
     */
    @Column(name = "tx_hash", nullable = false, columnDefinition = "CHAR(66)")
    private String txHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false)
    private BlockchainReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    /**
     * 호출한 컨트랙트 함수 이름. 검증 단계 판정이 이 값을 본다
     * ({@code verifySource} / {@code verifyTransaction} / {@code verifyProcessing}).
     *
     * <p>ENUM 이 아니라 {@code VARCHAR(100)} 이다 — 컨트랙트 함수는 계속 늘어나고
     * 값 추가마다 DDL 을 부르지 않으려는 선택으로 보인다. 그래서 <b>비교는 상수로만</b>
     * 하고 문자열을 코드에 흩지 않는다.
     */
    @Column(name = "function_name", nullable = false, length = 100)
    private String functionName;

    @Column(name = "from_wallet", nullable = false, length = 42)
    private String fromWallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BlockchainTransactionStatus status;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "log_index")
    private Integer logIndex;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 원래 {@code insertable = false} 였다 — 읽기 전용이라 DB 기본값
     * ({@code DEFAULT CURRENT_TIMESTAMP}) 에 맡겼다. {@link #confirmed} 가 생기면서 바꿨다.
     *
     * <p><b>테스트 프로파일이 {@code create-drop} 인 것이 이유다.</b> Hibernate 가 엔티티대로
     * 테이블을 만드는데 {@code columnDefinition} 이 없으면 기본값이 따라가지 않는다. 그래서
     * {@code NOT NULL} 컬럼에 아무것도 넣지 않은 INSERT 가 되어 H2 에서만 터졌다 —
     * MySQL 에서는 기본값이 채워 주므로 <b>운영에서는 드러나지 않았을 차이</b>다.
     *
     * <p>{@code Clock} 을 주입받은 호출부가 값을 넣는다. Auditing 리스너를 쓰지 않는 것은
     * 테스트가 시계를 고정할 수 있게 하기 위해서다.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * 확정된 트랜잭션 하나를 기록한다.
     *
     * <p><b>{@code SUCCESS} 로만 만들어진다.</b> 화면은 {@code tx.wait()} 이 영수증을
     * 돌려준 뒤에야 이 경로를 부르므로, 서버가 아는 트랜잭션은 이미 채굴되어 성공한 것이다.
     * 실패한 트랜잭션은 화면에서 예외로 끝나고 서버까지 오지 않는다.
     *
     * <p>{@code logIndex} 를 {@code null} 로 둔다. 우리가 기록하는 것은 <b>트랜잭션</b>이지
     * 그 안의 특정 로그가 아니다. UNIQUE {@code (chain_id, tx_hash, log_index)} 에서 MySQL 은
     * NULL 을 서로 다르게 보므로 이 행들은 중복 제약에 걸리지 않는다 — 같은 해시를 두 번
     * 보내면 두 행이 생긴다. 그 방지는 참조 대상 엔티티가 한다
     * ({@code assignChainListingId} 등이 이미 배정된 경우 예외를 던진다).
     *
     * @param functionName 호출한 컨트랙트 함수. 검증 판정이 이 값을 보므로 상수로만 넘긴다
     */
    public static BlockchainTransaction confirmed(
            Long chainId,
            String contractAddress,
            String txHash,
            BlockchainReferenceType referenceType,
            Long referenceId,
            String functionName,
            String fromWallet,
            Long blockNumber,
            LocalDateTime confirmedAt) {
        BlockchainTransaction transaction = new BlockchainTransaction();
        transaction.chainId = requirePositive(chainId, "chain id");
        transaction.contractAddress = requireAddress(contractAddress, "contract address");
        transaction.txHash = requireTxHash(txHash);
        transaction.referenceType = requireNotNull(referenceType, "reference type");
        transaction.referenceId = requirePositive(referenceId, "reference id");
        transaction.functionName = requireText(functionName, "function name", FUNCTION_NAME_MAX_LENGTH);
        transaction.fromWallet = requireAddress(fromWallet, "from wallet");
        transaction.status = BlockchainTransactionStatus.SUCCESS;
        transaction.blockNumber = blockNumber;
        transaction.createdAt = requireNotNull(confirmedAt, "confirmed at");
        transaction.confirmedAt = confirmedAt;
        return transaction;
    }

    private static final int FUNCTION_NAME_MAX_LENGTH = 100;
    private static final int TX_HASH_LENGTH = 66;
    private static final int ADDRESS_LENGTH = 42;

    /**
     * {@code CHAR(66)} 이라 길이가 어긋나면 DB 가 조용히 자르거나 공백으로 채운다.
     * 잘린 해시는 체인에서 조회되지 않으므로 기록이 있어도 검증할 수 없다.
     */
    private static String requireTxHash(String txHash) {
        if (txHash == null || txHash.length() != TX_HASH_LENGTH || !txHash.startsWith("0x")) {
            throw new IllegalArgumentException(
                    "Blockchain transaction hash must be 0x + 64 hex characters");
        }
        return txHash.toLowerCase();
    }

    /** 주소는 소문자로 저장한다 — {@code user_wallet} 과 같은 규칙이라 대조가 성립한다. */
    private static String requireAddress(String address, String field) {
        if (address == null || address.length() != ADDRESS_LENGTH || !address.startsWith("0x")) {
            throw new IllegalArgumentException(
                    "Blockchain transaction " + field + " must be a 0x-prefixed 42 character address");
        }
        return address.toLowerCase();
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Blockchain transaction " + field + " must be 1.." + maxLength + " characters");
        }
        return value;
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    "Blockchain transaction " + field + " must be positive");
        }
        return value;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Blockchain transaction " + field + " must not be null");
        }
        return value;
    }
}
