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
 * <h2>지금은 읽기 전용이다</h2>
 *
 * <p>쓰기 경로는 두 곳에서 생기는데 둘 다 아직 없다.
 *
 * <ol>
 *   <li>Tx 를 보내는 경로 — {@code PENDING} 으로 넣고 receipt 로 갱신 (S15P21E104-153,
 *       컨트랙트 주소 S15P21E104-374 대기)</li>
 *   <li>이벤트 리스너 — 우리 서버를 거치지 않은 직접 호출을 줍는다 (S15P21E104-205)</li>
 * </ol>
 *
 * <p>그래서 <b>생성자·수정 메서드를 두지 않았다.</b> 쓰기 주체가 정해지지 않은 상태에서
 * 만들어 두면 그 주체가 자기 규칙(상태 전이, 중복 방지)을 못 걸고 이 클래스의 빈 메서드에
 * 맞추게 된다. 필요한 쪽이 생길 때 그쪽이 자기 불변식과 함께 얹는다.
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

    @Column(name = "tx_hash", nullable = false, length = 66)
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
     * {@code created_at} 은 DB 기본값이 채운다. 이 엔티티는 쓰기를 하지 않으므로
     * Auditing 리스너를 붙이지 않았다 — 붙이면 "이 엔티티로 INSERT 한다" 는 신호가 된다.
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
}
