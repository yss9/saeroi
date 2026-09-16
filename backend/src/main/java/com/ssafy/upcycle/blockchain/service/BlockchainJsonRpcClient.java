package com.ssafy.upcycle.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface BlockchainJsonRpcClient {
    JsonNode transactionReceipt(String txHash);
}
