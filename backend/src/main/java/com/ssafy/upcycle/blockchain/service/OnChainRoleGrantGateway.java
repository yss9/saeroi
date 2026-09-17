package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.user.entity.UserRole;

public interface OnChainRoleGrantGateway {
    RoleGrantResult grant(UserRole role, String walletAddress);
}
