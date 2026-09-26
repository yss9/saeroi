package com.ssafy.upcycle.prediction.service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public interface PredictionChainGateway {
    MarketCreated createMarket(long batchId,long productModelId,long unitPrice,LocalDateTime bettingEndsAt,LocalDateTime salesStartsAt,LocalDateTime salesEndsAt,List<Integer> bounds);
    String submitResult(long chainMarketId,int soldQuantity,String resultHash);
    String cancelMarket(long chainMarketId);
    String mintPoint(String walletAddress, BigInteger amount);
    boolean verifyBet(String txHash,long chainMarketId,String walletAddress,int bucketIndex,BigInteger amount);
    record MarketCreated(long chainMarketId,String transactionHash) { }
}
