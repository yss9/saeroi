package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/** 관리자가 MetaMask로 직접 실행한 역할 부여 트랜잭션 목록. */
public record CompanyRoleApprovalRequest(
        @NotNull List<@Pattern(regexp = "^0x[0-9a-fA-F]{64}$") String> transactionHashes) {
}
