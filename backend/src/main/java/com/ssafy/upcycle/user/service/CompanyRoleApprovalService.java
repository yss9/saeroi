package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.blockchain.service.OnChainRoleVerificationGateway;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.CompanyRoleApprovalErrorCode;
import com.ssafy.upcycle.user.dto.CompanyRoleApplicationResponse;
import com.ssafy.upcycle.user.dto.CompanyRoleApprovalRequest;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.CompanyRoleApprovalStatus;
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
import java.util.Set;
import java.util.stream.Collectors;
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
    private final OnChainRoleVerificationGateway roleVerificationGateway;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CompanyRoleApplicationResponse> findAll() {
        Set<Long> pendingUserIds = companyProfileRepository
                .findAllByRoleApprovalStatus(CompanyRoleApprovalStatus.PENDING).stream()
                .map(CompanyProfile::getUserId)
                .collect(Collectors.toSet());
        return userRepository.findAllByRoleInOrderByCreatedAtDesc(BUSINESS_ROLES).stream()
                .filter(user -> pendingUserIds.contains(user.getId()))
                .map(this::response)
                .toList();
    }

    /** 관리자 MetaMask 트랜잭션과 실제 온체인 역할을 검증한 뒤에만 승인 상태를 확정한다. */
    @Transactional
    public CompanyRoleApplicationResponse approve(
            Long adminId, Long userId, CompanyRoleApprovalRequest request) {
        User user = requireBusiness(userId);
        CompanyProfile profile = requireProfile(userId);
        UserWallet wallet = userWalletRepository.findByUserIdAndActiveTrue(userId)
                .filter(UserWallet::isVerified)
                .orElseThrow(() -> new BusinessException(CompanyRoleApprovalErrorCode.WALLET_REQUIRED));

        roleVerificationGateway.verifyGranted(
                user.getRole(), wallet.getWalletAddress(), request.transactionHashes());
        profile.approveRole(
                wallet.getWalletAddress(),
                String.join(",", request.transactionHashes()),
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
