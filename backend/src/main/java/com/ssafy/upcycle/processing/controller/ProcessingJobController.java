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
    private final ProcessingJobWorkflowService workflow;
    private final ProcessingSignatureService signatures;
    private final ProcessingPaymentService payments;
    private final ProcessingLifecycleService lifecycle;
    private final ProcessingResultService legacyResults;
    @GetMapping public ApiResponse<java.util.List<ProcessingJobResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(workflow.list(userId(jwt)));
    }
    @GetMapping("/{jobId}") public ApiResponse<ProcessingJobResponse> detail(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(workflow.detail(userId(jwt),jobId)); }
    @PostMapping("/{jobId}/payment") public ApiResponse<ProcessingPaymentReadyResponse> ready(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(payments.ready(Long.valueOf(jwt.getSubject()),jobId)); }
    @PostMapping("/{jobId}/payment/confirm") public ApiResponse<ProcessingJobResponse> confirm(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingPaymentConfirmRequest input) {
        return ApiResponse.success(payments.confirm(Long.valueOf(jwt.getSubject()),jobId,input.pgToken())); }
    @PatchMapping("/{jobId}/ship") public ApiResponse<ProcessingJobResponse> ship(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingJobShipRequest input) {
        return ApiResponse.success(workflow.ship(userId(jwt), jobId, input));
    }
    @PatchMapping("/{jobId}/receive") public ApiResponse<ProcessingJobResponse> receive(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(workflow.receive(userId(jwt), jobId)); }
    @PatchMapping("/{jobId}/start") public ApiResponse<ProcessingJobResponse> start(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(workflow.start(userId(jwt), jobId)); }
    @PostMapping("/{jobId}/results") public ApiResponse<ProcessingJobResponse> result(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingResultRequest input) {
        return ApiResponse.success(workflow.saveResult(userId(jwt), jobId, input));
    }
    @PostMapping("/{jobId}/signature-challenge")
    public ApiResponse<ProcessingSignatureChallengeResponse> challenge(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingSignatureChallengeRequest input) {
        return ApiResponse.success(signatures.issue(userId(jwt), jobId, input));
    }
    @PostMapping("/{jobId}/results/submit") public ApiResponse<ProcessingJobResponse> submit(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingSignedCommandRequest input) {
        return ApiResponse.success(workflow.submitResult(userId(jwt), jobId, input));
    }
    @PostMapping("/{jobId}/approve") public ApiResponse<ProcessingJobResponse> approve(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingSignedCommandRequest input) {
        return ApiResponse.success(workflow.approve(userId(jwt), jobId, input));
    }
    @PostMapping("/{jobId}/reject") public ApiResponse<ProcessingJobResponse> reject(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody RejectProcessingResultRequest input) {
        return ApiResponse.success(workflow.reject(userId(jwt), jobId, input));
    }
    @PostMapping("/{jobId}/shipment") public ApiResponse<ProcessingJobResponse> legacyShip(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long jobId) {
        return ApiResponse.success(lifecycle.ship(userId(jwt), jobId));
    }
    @PostMapping("/{jobId}/receipt") public ApiResponse<ProcessingJobResponse> legacyReceive(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long jobId) {
        return ApiResponse.success(lifecycle.receive(userId(jwt), jobId));
    }
    @PostMapping("/{jobId}/start") public ApiResponse<ProcessingJobResponse> legacyStart(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long jobId) {
        return ApiResponse.success(lifecycle.start(userId(jwt), jobId));
    }
    @PostMapping("/{jobId}/result") public ApiResponse<ProcessingJobResponse> legacySubmitResult(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long jobId,
            @Valid @RequestBody SubmitProcessingResultRequest input) {
        return ApiResponse.success(legacyResults.submit(userId(jwt), jobId, input));
    }
    @PostMapping("/{jobId}/result/confirm") public ApiResponse<ProcessingJobResponse> legacyConfirmResult(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long jobId) {
        return ApiResponse.success(legacyResults.confirm(userId(jwt), jobId));
    }
    private Long userId(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
}
