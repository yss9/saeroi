package com.ssafy.upcycle.prediction.dto;
import com.ssafy.upcycle.prediction.entity.PredictionPointGrant;
public record PredictionPointGrantResponse(String walletAddress,String amount,String transactionHash){ public static PredictionPointGrantResponse from(PredictionPointGrant value){return new PredictionPointGrantResponse(value.getWalletAddress(),value.getAmount(),value.getTxHash());} }
