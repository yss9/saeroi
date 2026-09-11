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
    private final ProcessingLifecycleService lifecycle; private final ProcessingResultService results;
    @GetMapping("/{jobId}") public ApiResponse<ProcessingJobResponse> detail(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(jobs.detail(Long.valueOf(jwt.getSubject()),jobId)); }
    @PostMapping("/{jobId}/payment") public ApiResponse<ProcessingPaymentReadyResponse> ready(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(payments.ready(Long.valueOf(jwt.getSubject()),jobId)); }
    @PostMapping("/{jobId}/payment/confirm") public ApiResponse<ProcessingJobResponse> confirm(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingPaymentConfirmRequest input) {
        return ApiResponse.success(payments.confirm(Long.valueOf(jwt.getSubject()),jobId,input.pgToken())); }
    @PostMapping("/{jobId}/shipment") public ApiResponse<ProcessingJobResponse> ship(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(lifecycle.ship(Long.valueOf(jwt.getSubject()), jobId)); }
    @PostMapping("/{jobId}/receipt") public ApiResponse<ProcessingJobResponse> receive(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(lifecycle.receive(Long.valueOf(jwt.getSubject()), jobId)); }
    @PostMapping("/{jobId}/start") public ApiResponse<ProcessingJobResponse> start(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(lifecycle.start(Long.valueOf(jwt.getSubject()), jobId)); }
    @PostMapping("/{jobId}/result") public ApiResponse<ProcessingJobResponse> submitResult(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody SubmitProcessingResultRequest input) {
        return ApiResponse.success(results.submit(Long.valueOf(jwt.getSubject()), jobId, input)); }
    @PostMapping("/{jobId}/result/confirm") public ApiResponse<ProcessingJobResponse> confirmResult(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long jobId) {
        return ApiResponse.success(results.confirm(Long.valueOf(jwt.getSubject()), jobId)); }
}
