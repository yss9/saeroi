package com.ssafy.upcycle.processing.controller;
import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping({"/processing-jobs","/api/v1/processing-jobs"})
public class ProcessingJobController {
    private final ProcessingJobService jobs; private final ProcessingPaymentService payments;
    @GetMapping("/{jobId}") public ApiResponse<ProcessingJobResponse> detail(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(jobs.detail(Long.valueOf(jwt.getSubject()),jobId)); }
    @PostMapping("/{jobId}/payment") public ApiResponse<ProcessingPaymentReadyResponse> ready(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(payments.ready(Long.valueOf(jwt.getSubject()),jobId)); }
    @PostMapping("/{jobId}/payment/confirm") public ApiResponse<ProcessingJobResponse> confirm(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingPaymentConfirmRequest input) {
        return ApiResponse.success(payments.confirm(Long.valueOf(jwt.getSubject()),jobId,input.pgToken())); }
}
