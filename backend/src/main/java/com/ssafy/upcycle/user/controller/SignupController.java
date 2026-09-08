package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.user.dto.BusinessSignupRequest;
import com.ssafy.upcycle.user.dto.ConsumerSignupRequest;
import com.ssafy.upcycle.user.dto.UserSummaryResponse;
import com.ssafy.upcycle.user.service.SignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 개인회원과 사업자회원 가입 엔드포인트. */
@RestController
@RequestMapping("/api/v1/auth/signup")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    @PostMapping("/consumer")
    public ApiResponse<UserSummaryResponse> registerConsumer(
            @Valid @RequestBody ConsumerSignupRequest request) {
        return ApiResponse.success(signupService.registerConsumer(request));
    }

    @PostMapping("/business")
    public ApiResponse<UserSummaryResponse> registerBusiness(
            @Valid @RequestBody BusinessSignupRequest request) {
        return ApiResponse.success(signupService.registerBusiness(request));
    }
}
