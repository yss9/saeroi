package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.blockchain.service.OnChainRoleGrantGateway;
import com.ssafy.upcycle.blockchain.service.RoleGrantResult;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.CompanyRoleApprovalErrorCode;
import com.ssafy.upcycle.user.dto.CompanyRoleApplicationResponse;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyRoleApprovalService {
    private static final List<UserRole> BUSINESS_ROLES =
            List.of(UserRole.SUPPLIER, UserRole.BRAND, UserRole.PROCESSOR);

    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final UserWalletRepository userWalletRepository;
    private final OnChainRoleGrantGateway roleGrantGateway;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CompanyRoleApplicationResponse> findAll() {
        return userRepository.findAllByRoleInOrderByCreatedAtDesc(BUSINESS_ROLES).stream()
                .map(this::response)
                .toList();
    }

    /** 체인 트랜잭션이 성공한 뒤에만 승인 상태를 확정한다. */
    @Transactional
    public CompanyRoleApplicationResponse approve(Long adminId, Long userId) {
        User user = requireBusiness(userId);
        CompanyProfile profile = requireProfile(userId);
        UserWallet wallet = userWalletRepository.findByUserIdAndActiveTrue(userId)
                .filter(UserWallet::isVerified)
                .orElseThrow(() -> new BusinessException(CompanyRoleApprovalErrorCode.WALLET_REQUIRED));

        RoleGrantResult result = roleGrantGateway.grant(user.getRole(), wallet.getWalletAddress());
        profile.approveRole(
                wallet.getWalletAddress(),
                String.join(",", result.transactionHashes()),
                adminId,
                LocalDateTime.now(clock));
        return response(user);
    }

    @Transactional
    public CompanyRoleApplicationResponse reject(Long adminId, Long userId) {
        User user = requireBusiness(userId);
        requireProfile(userId).rejectRole(adminId, LocalDateTime.now(clock));
        return response(user);
    }

    private User requireBusiness(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CompanyRoleApprovalErrorCode.COMPANY_NOT_FOUND));
        if (!BUSINESS_ROLES.contains(user.getRole())) {
            throw new BusinessException(CompanyRoleApprovalErrorCode.COMPANY_NOT_FOUND);
        }
        return user;
    }

    private CompanyProfile requireProfile(Long userId) {
        return companyProfileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CompanyRoleApprovalErrorCode.COMPANY_NOT_FOUND));
    }

    private CompanyRoleApplicationResponse response(User user) {
        CompanyProfile profile = requireProfile(user.getId());
        UserWallet wallet = userWalletRepository.findByUserIdAndActiveTrue(user.getId()).orElse(null);
        boolean ready = wallet != null && wallet.isVerified();
        String status = ready ? profile.getRoleApprovalStatus().name() : "WAITING_WALLET";
        List<String> hashes = profile.getRoleApprovalTxHashes() == null
                || profile.getRoleApprovalTxHashes().isBlank()
                ? List.of()
                : Arrays.asList(profile.getRoleApprovalTxHashes().split(","));
        return new CompanyRoleApplicationResponse(
                user.getId(), profile.getCompanyName(), profile.getBusinessNumber(),
                user.getName(), user.getEmail(), user.getRole(),
                wallet == null ? null : wallet.getWalletAddress(), ready, status,
                profile.getRoleApprovedWallet(), hashes, profile.getRoleReviewedAt());
    }
}
