package com.ssafy.upcycle.blockchain.entity;

public enum BlockchainOperationStatus {
    CREATED,
    SUBMITTED,
    CHAIN_CONFIRMED,
    CHAIN_FAILED,
    DELAYED,
    DB_PROJECTED,
    INCONSISTENT
}
