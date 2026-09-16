package com.ssafy.upcycle.processing.controller;
import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping({"/processing-jobs","/api/processing-jobs","/api/v1/processing-jobs"})
public class ProcessingJobController {
    private final ProcessingJobWorkflowService workflow;
    private final ProcessingSignatureService signatures;
    private final ProcessingPaymentService payments;
    private final ProcessingLifecycleService lifecycle;
    private final ProcessingResultService legacyResults;
    private final ProcessingChainService chain;
    @GetMapping public ApiResponse<java.util.List<ProcessingJobResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(workflow.list(userId(jwt)));
    }
    @GetMapping("/custody-materials")
    public ApiResponse<java.util.List<ProcessingCustodyMaterialResponse>> custodyMaterials(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(workflow.custodyMaterials(userId(jwt)));
    }
    @GetMapping("/{jobId}") public ApiResponse<ProcessingJobResponse> detail(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(workflow.detail(userId(jwt),jobId)); }
    @GetMapping("/{jobId}/chain") public ApiResponse<ProcessingChainPreparationResponse> prepareChain(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long jobId) {
        return ApiResponse.success(chain.prepare(userId(jwt), jobId));
    }
    @PostMapping("/{jobId}/payment") public ApiResponse<ProcessingPaymentReadyResponse> ready(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId) { return ApiResponse.success(payments.ready(Long.valueOf(jwt.getSubject()),jobId)); }
    @PostMapping("/{jobId}/payment/confirm") public ApiResponse<ProcessingJobResponse> confirm(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingPaymentConfirmRequest input) {
        return ApiResponse.success(payments.confirm(Long.valueOf(jwt.getSubject()),jobId,input.pgToken())); }
    @PatchMapping("/{jobId}/ship") public ApiResponse<ProcessingJobResponse> ship(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingJobShipRequest input) {
        return ApiResponse.success(workflow.ship(userId(jwt), jobId, input));
    }
    @PostMapping("/{jobId}/chain") public ApiResponse<ProcessingJobResponse> linkChain(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingChainJobLinkRequest input) {
        return ApiResponse.success(chain.linkJob(userId(jwt), jobId, input));
    }
    @PatchMapping("/{jobId}/receive") public ApiResponse<ProcessingJobResponse> receive(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingReceiveConfirmRequest input) {
        return ApiResponse.success(chain.receive(userId(jwt), jobId, input));
    }
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
    @PostMapping("/{jobId}/reject") public ApiResponse<ProcessingJobResponse> reject(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody RejectProcessingResultRequest input) {
        return ApiResponse.success(workflow.reject(userId(jwt), jobId, input));
    }
    @PatchMapping("/{jobId}/return/ship") public ApiResponse<ProcessingJobResponse> shipReturn(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingReturnShipRequest input) {
        return ApiResponse.success(chain.shipReturn(userId(jwt), jobId, input));
    }
    @PostMapping("/{jobId}/return/receive") public ApiResponse<ProcessingJobResponse> receiveReturn(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long jobId, @Valid @RequestBody ProcessingReturnReceiveRequest input) {
        return ApiResponse.success(chain.receiveReturn(userId(jwt), jobId, input));
    }
    @PostMapping("/{jobId}/return/rejected") public ApiResponse<ProcessingJobResponse> returnRejected(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long jobId,
            @Valid @RequestBody ProcessingRejectedReturnRequest input) {
        return ApiResponse.success(chain.returnRejected(userId(jwt), jobId, input));
    }
    @PostMapping("/{jobId}/shipment") public ApiResponse<ProcessingJobResponse> legacyShip(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long jobId) {
        return ApiResponse.success(lifecycle.ship(userId(jwt), jobId));
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
    private Long userId(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
}
