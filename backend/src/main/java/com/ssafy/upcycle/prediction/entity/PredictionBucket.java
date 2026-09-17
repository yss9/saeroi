package com.ssafy.upcycle.prediction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Entity @Table(name="prediction_bucket", uniqueConstraints=@UniqueConstraint(name="uk_prediction_bucket_market_index", columnNames={"market_id","bucket_index"}))
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PredictionBucket {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") private Long id;
    @Column(name="market_id", nullable=false) private Long marketId;
    @Column(name="bucket_index", nullable=false) private int bucketIndex;
    @Column(name="lower_bound", nullable=false) private int lowerBound;
    @Column(name="upper_bound", nullable=false) private int upperBound;
    private PredictionBucket(Long marketId, int index, int lower, int upper) { this.marketId=marketId; this.bucketIndex=index; this.lowerBound=lower; this.upperBound=upper; }
    public static PredictionBucket of(Long marketId, int index, int lower, int upper) { return new PredictionBucket(marketId,index,lower,upper); }
}
