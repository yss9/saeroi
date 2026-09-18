package com.ssafy.upcycle.user.security;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.CompanyRoleApprovalErrorCode;
import com.ssafy.upcycle.user.entity.CompanyRoleApprovalStatus;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 관리자 승인이 끝나기 전 사업자 계정의 업무 API 사용을 한 곳에서 차단한다. */
@Component
@RequiredArgsConstructor
public class CompanyRoleApprovalInterceptor implements HandlerInterceptor {

    private static final List<String> BUSINESS_ROLES = List.of(
            UserRole.SUPPLIER.name(), UserRole.BRAND.name(), UserRole.PROCESSOR.name());

    private final CompanyProfileRepository companyProfiles;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isOnboardingPath(request) || !isBusinessRequest()) return true;

        Long userId = authenticatedUserId();
        boolean approved = companyProfiles.findById(userId)
                .map(profile -> profile.getRoleApprovalStatus() == CompanyRoleApprovalStatus.APPROVED)
                .orElse(false);
        if (!approved) {
            throw new BusinessException(CompanyRoleApprovalErrorCode.APPROVAL_REQUIRED);
        }
        return true;
    }

    private boolean isBusinessRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) return false;
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.stream().anyMatch(BUSINESS_ROLES::contains);
    }

    private Long authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new BusinessException(CompanyRoleApprovalErrorCode.APPROVAL_REQUIRED);
        }
    }

    private boolean isOnboardingPath(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/wallets")
                || path.startsWith("/api/v1/companies/me")
                || path.startsWith("/api/v1/users/me")
                || path.startsWith("/api/v1/public/")
                || path.equals("/api/health")
                || path.equals("/error");
    }
}
