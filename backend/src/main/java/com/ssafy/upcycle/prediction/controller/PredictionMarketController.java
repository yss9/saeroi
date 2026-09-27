package com.ssafy.upcycle.prediction.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.prediction.dto.*;
import com.ssafy.upcycle.prediction.service.PredictionMarketService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    /**
     * 시장의 제품 사진. 목록처럼 로그인 없이 열린다(SecurityConfig).
     *
     * <p>저장 키가 바뀌지 않는 한 같은 사진이라 오래 캐시해도 된다. 쇼핑몰 공개 썸네일과 같은 헤더다.
     */
    @GetMapping("/{marketId}/thumbnail")
    public ResponseEntity<Resource> thumbnail(
            @PathVariable Long marketId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        PredictionMarketService.MarketThumbnail thumbnail = service.thumbnail(marketId);
        String etag = "\"" + thumbnail.storageKey().hashCode() + "\"";
        if (etag.equals(ifNoneMatch)) return ResponseEntity.status(304).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(thumbnail.content().contentType()))
                .contentLength(thumbnail.content().size())
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400, immutable")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(thumbnail.content().stream()));
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

