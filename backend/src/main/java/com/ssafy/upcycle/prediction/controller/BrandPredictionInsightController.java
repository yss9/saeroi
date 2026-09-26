package com.ssafy.upcycle.prediction.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.prediction.dto.BrandPredictionInsightResponse;
import com.ssafy.upcycle.prediction.service.BrandPredictionInsightService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/brand/prediction-markets")
@RequiredArgsConstructor
public class BrandPredictionInsightController {
    private final BrandPredictionInsightService service;

    @GetMapping("/insights")
    @PreAuthorize("hasRole('BRAND')")
    public ApiResponse<List<BrandPredictionInsightResponse>> findMine(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.findMine(Long.valueOf(jwt.getSubject())));
    }
}
