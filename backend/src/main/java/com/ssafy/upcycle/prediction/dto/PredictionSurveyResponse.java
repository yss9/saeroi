package com.ssafy.upcycle.prediction.dto;
import com.ssafy.upcycle.prediction.entity.PredictionResponse;
public record PredictionSurveyResponse(Long id,Long marketId,int bucketIndex,String stakeAmount,String reasonCode,String reasonText,String transactionHash){
 public static PredictionSurveyResponse from(PredictionResponse value){return new PredictionSurveyResponse(value.getId(),value.getMarketId(),value.getBucketIndex(),value.getStakeAmount(),value.getReasonCode().name(),value.getReasonText(),value.getBetTxHash());}
}
