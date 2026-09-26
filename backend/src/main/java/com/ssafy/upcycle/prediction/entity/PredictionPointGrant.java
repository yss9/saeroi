package com.ssafy.upcycle.prediction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter @Entity @Table(name="prediction_point_grant") @NoArgsConstructor(access=AccessLevel.PROTECTED) @EntityListeners(AuditingEntityListener.class)
public class PredictionPointGrant {
    @Id @Column(name="user_id") private Long userId;
    @Column(name="wallet_address", nullable=false, length=42) private String walletAddress;
    @Column(name="amount", nullable=false, length=78) private String amount;
    @Column(name="tx_hash", nullable=false, unique=true, columnDefinition="CHAR(66)") private String txHash;
    @CreatedDate @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    public static PredictionPointGrant of(Long userId,String wallet,String amount,String tx){ PredictionPointGrant value=new PredictionPointGrant(); value.userId=userId; value.walletAddress=wallet.toLowerCase(); value.amount=amount; value.txHash=tx.toLowerCase(); return value; }
}
