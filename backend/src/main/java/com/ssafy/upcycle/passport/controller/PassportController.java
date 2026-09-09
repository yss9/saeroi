package com.ssafy.upcycle.passport.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.passport.dto.PassportResponse;
import com.ssafy.upcycle.passport.service.PassportQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * QR Passport 공개 조회 (FR-051). <b>인증이 없다.</b>
 *
 * <p>{@code SecurityConfig} 의 permitAll 목록에 {@code GET /api/v1/qr/**} 를 추가해야
 * 이 경로가 401 없이 응답한다 — 그 변경 없이는 이 컨트롤러가 있어도 막혀 있다.
 *
 * <p>비회원이 QR 을 찍고 바로 들어오는 진입점이라 토큰·역할을 요구하면 서비스 목적이
 * 무너진다. 대신 {@code qrToken} 자체가 추측 불가능한 값이라 접근 통제 역할을 한다
 * ({@code ProductItem} 참고).
 */
@RestController
@RequestMapping("/api/v1/qr")
@RequiredArgsConstructor
public class PassportController {

    private final PassportQueryService passportQueryService;

    @GetMapping("/{qrToken}")
    public ApiResponse<PassportResponse> findByQrToken(@PathVariable String qrToken) {
        return ApiResponse.success(passportQueryService.findByQrToken(qrToken));
    }
}
