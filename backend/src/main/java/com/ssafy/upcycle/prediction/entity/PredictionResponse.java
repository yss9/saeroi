package com.ssafy.upcycle.prediction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter @Entity @Table(name="prediction_response", uniqueConstraints={
        @UniqueConstraint(name="uk_prediction_response_market_user", columnNames={"market_id","user_id"}),
        @UniqueConstraint(name="uk_prediction_response_tx", columnNames={"bet_tx_hash"})})
@NoArgsConstructor(access=AccessLevel.PROTECTED) @EntityListeners(AuditingEntityListener.class)
public class PredictionResponse {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") private Long id;
    @Column(name="market_id", nullable=false) private Long marketId;
    @Column(name="user_id", nullable=false) private Long userId;
    @Column(name="wallet_address", nullable=false, length=42) private String walletAddress;
    @Column(name="bucket_index", nullable=false) private int bucketIndex;
    @Column(name="stake_amount", nullable=false, length=78) private String stakeAmount;
    @Enumerated(EnumType.STRING) @Column(name="reason_code", nullable=false) private PredictionReasonCode reasonCode;
    @Column(name="reason_text", length=500) private String reasonText;
    @Column(name="bet_tx_hash", nullable=false, length=66) private String betTxHash;
    @CreatedDate @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    public static PredictionResponse of(Long marketId, Long userId, String wallet, int bucket, String amount, PredictionReasonCode code, String text, String tx) {
        PredictionResponse value=new PredictionResponse(); value.marketId=marketId; value.userId=userId; value.walletAddress=wallet.toLowerCase(); value.bucketIndex=bucket; value.stakeAmount=amount; value.reasonCode=code; value.reasonText=text==null||text.isBlank()?null:text.trim(); value.betTxHash=tx.toLowerCase(); return value;
    }
}
