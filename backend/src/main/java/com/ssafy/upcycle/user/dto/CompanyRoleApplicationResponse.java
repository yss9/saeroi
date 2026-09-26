package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.user.entity.UserRole;
import java.time.LocalDateTime;
import java.util.List;

public record CompanyRoleApplicationResponse(
        Long userId,
        String companyName,
        String businessNumber,
        String representativeName,
        String email,
        UserRole role,
        String walletAddress,
        boolean walletVerified,
        String status,
        String approvedWallet,
        List<String> transactionHashes,
        LocalDateTime reviewedAt) {
}
