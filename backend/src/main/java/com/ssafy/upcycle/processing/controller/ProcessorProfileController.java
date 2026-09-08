package com.ssafy.upcycle.processing.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.service.ProcessorProfileService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/processors/me", "/api/v1/processors/me"})
public class ProcessorProfileController {
    private final ProcessorProfileService service;

    @GetMapping("/profile")
    public ApiResponse<ProcessorProfileResponse> profile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.profile(userId(jwt)));
    }

    @PutMapping("/profile")
    public ApiResponse<ProcessorProfileResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProcessorProfileRequest request) {
        return ApiResponse.success(service.updateProfile(userId(jwt), request));
    }

    @GetMapping("/capabilities")
    public ApiResponse<List<ProcessorCapabilityResponse>> capabilities(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.capabilities(userId(jwt)));
    }

    @PutMapping("/capabilities")
    public ApiResponse<List<ProcessorCapabilityResponse>> updateCapabilities(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProcessorCapabilitiesUpdateRequest request) {
        return ApiResponse.success(service.updateCapabilities(userId(jwt), request));
    }

    @GetMapping("/lead-times")
    public ApiResponse<List<ProcessorLeadTimeResponse>> leadTimes(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.leadTimes(userId(jwt)));
    }

    @PutMapping("/lead-times")
    public ApiResponse<List<ProcessorLeadTimeResponse>> updateLeadTimes(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProcessorLeadTimesUpdateRequest request) {
        return ApiResponse.success(service.updateLeadTimes(userId(jwt), request));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
