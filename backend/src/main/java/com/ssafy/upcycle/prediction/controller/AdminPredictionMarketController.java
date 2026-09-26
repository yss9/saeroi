package com.ssafy.upcycle.prediction.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.prediction.dto.PredictionMarketCreateRequest;
import com.ssafy.upcycle.prediction.dto.PredictionMarketEligibleBatchResponse;
import com.ssafy.upcycle.prediction.dto.PredictionMarketResponse;
import com.ssafy.upcycle.prediction.service.PredictionMarketService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/prediction-markets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPredictionMarketController {
    private final PredictionMarketService service;

    @GetMapping("/eligible-batches")
    public ApiResponse<List<PredictionMarketEligibleBatchResponse>> eligibleBatches() {
        return ApiResponse.success(service.eligibleBatches());
    }

    @PostMapping
    public ApiResponse<PredictionMarketResponse> create(
            @Valid @RequestBody PredictionMarketCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    /** 시장을 무효로 돌리고 참여자 스테이크를 환불 대상으로 만든다. 되돌릴 수 없다. */
    @PostMapping("/{marketId}/cancel")
    public ApiResponse<PredictionMarketResponse> cancel(@PathVariable Long marketId) {
        return ApiResponse.success(service.cancel(marketId));
    }
}

