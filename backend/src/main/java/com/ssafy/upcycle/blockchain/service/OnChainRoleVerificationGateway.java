package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.user.entity.UserRole;
import java.util.List;

public interface OnChainRoleVerificationGateway {
    void verifyGranted(UserRole role, String walletAddress, List<String> transactionHashes);
}
