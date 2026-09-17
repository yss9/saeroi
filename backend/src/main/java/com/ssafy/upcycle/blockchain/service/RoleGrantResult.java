package com.ssafy.upcycle.blockchain.service;

import java.util.List;

public record RoleGrantResult(List<String> transactionHashes) {
    public RoleGrantResult {
        transactionHashes = List.copyOf(transactionHashes);
    }
}
