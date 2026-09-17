package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.user.dto.CompanyRoleApplicationResponse;
import com.ssafy.upcycle.user.service.CompanyRoleApprovalService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/company-role-applications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCompanyRoleController {
    private final CompanyRoleApprovalService approvalService;

    @GetMapping
    public ApiResponse<List<CompanyRoleApplicationResponse>> findAll() {
        return ApiResponse.success(approvalService.findAll());
    }

    @PostMapping("/{userId}/approve")
    public ApiResponse<CompanyRoleApplicationResponse> approve(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return ApiResponse.success(approvalService.approve(Long.valueOf(jwt.getSubject()), userId));
    }

    @PostMapping("/{userId}/reject")
    public ApiResponse<CompanyRoleApplicationResponse> reject(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return ApiResponse.success(approvalService.reject(Long.valueOf(jwt.getSubject()), userId));
    }
}
