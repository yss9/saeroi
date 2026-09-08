package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.user.dto.EmailLoginRequest;
import com.ssafy.upcycle.user.dto.EmailLoginResponse;
import com.ssafy.upcycle.user.service.EmailLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이메일 로그인 엔드포인트. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class EmailAuthController {

    private final EmailLoginService emailLoginService;

    @PostMapping("/login")
    public ApiResponse<EmailLoginResponse> login(@Valid @RequestBody EmailLoginRequest request) {
        return ApiResponse.success(emailLoginService.login(request));
    }
}
