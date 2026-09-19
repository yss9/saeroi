package com.ssafy.upcycle.processing.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.entity.ProcessingRequestStatus;
import com.ssafy.upcycle.processing.service.ProcessingRequestQueryService;
import com.ssafy.upcycle.processing.service.ProcessingRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/processing-requests", "/api/v1/processing-requests"})
@RequiredArgsConstructor
public class ProcessingRequestController {
    private final ProcessingRequestService service;
    private final ProcessingRequestQueryService queryService;

    @GetMapping
    public ApiResponse<ProcessingRequestListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        return ApiResponse.success(queryService.list(Long.valueOf(jwt.getSubject()), status, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProcessingRequestResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProcessingRequest input) {
        return ApiResponse.success(service.create(Long.valueOf(jwt.getSubject()), input));
    }

    @PostMapping("/{requestId}/cancel")
    public ApiResponse<ProcessingRequestStatus> cancel(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long requestId) {
        return ApiResponse.success(service.cancel(Long.valueOf(jwt.getSubject()), requestId));
    }

    @PatchMapping("/{requestId}/cancel")
    public ApiResponse<ProcessingRequestStatus> cancelPatch(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long requestId) {
        return ApiResponse.success(service.cancel(Long.valueOf(jwt.getSubject()), requestId));
    }

    @GetMapping("/{requestId}")
    public ApiResponse<ProcessingRequestDetailResponse> detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long requestId) {
        return ApiResponse.success(queryService.detail(Long.valueOf(jwt.getSubject()), requestId));
    }

    @PostMapping("/{requestId}/reject")
    public ApiResponse<ProcessingRequestStatus> reject(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long requestId) {
        return ApiResponse.success(service.reject(Long.valueOf(jwt.getSubject()), requestId));
    }

}
