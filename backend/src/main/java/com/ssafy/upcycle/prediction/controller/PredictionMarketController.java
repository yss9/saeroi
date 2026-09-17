package com.ssafy.upcycle.prediction.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.prediction.dto.*;
import com.ssafy.upcycle.prediction.service.PredictionMarketService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/prediction-markets")
@RequiredArgsConstructor
public class PredictionMarketController {
    private final PredictionMarketService service;

    @GetMapping
    public ApiResponse<List<PredictionMarketResponse>> list() { return ApiResponse.success(service.list()); }

    @GetMapping("/{marketId}")
    public ApiResponse<PredictionMarketResponse> detail(@PathVariable Long marketId) {
        return ApiResponse.success(service.detail(marketId));
    }

    @PostMapping("/{marketId}/responses")
    @PreAuthorize("hasRole('CONSUMER')")
    public ApiResponse<PredictionSurveyResponse> respond(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long marketId,
            @Valid @RequestBody PredictionSurveyRequest request) {
        return ApiResponse.success(service.recordSurvey(Long.valueOf(jwt.getSubject()), marketId, request));
    }

    @PostMapping("/points/welcome")
    @PreAuthorize("hasRole('CONSUMER')")
    public ApiResponse<PredictionPointGrantResponse> welcome(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.grantWelcomePoint(Long.valueOf(jwt.getSubject())));
    }
}

