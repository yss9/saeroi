package com.ssafy.upcycle.processing.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.service.ProcessorProfileService;
import com.ssafy.upcycle.processing.service.ProcessorProfileImageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.ssafy.upcycle.common.storage.FileContent;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/processors/me", "/api/v1/processors/me"})
public class ProcessorProfileController {
    private final ProcessorProfileService service;
    private final ProcessorProfileImageService imageService;

    @GetMapping("/profile")
    public ApiResponse<ProcessorProfileResponse> profile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.profile(userId(jwt)));
    }

    @PutMapping("/profile")
    public ApiResponse<ProcessorProfileResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProcessorProfileRequest request) {
        return ApiResponse.success(service.updateProfile(userId(jwt), request));
    }

    @PostMapping(value = "/profile/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProcessorProfileImageResponse> uploadProfileImage(
            @AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(imageService.upload(userId(jwt), file));
    }

    @GetMapping("/profile/image")
    public ResponseEntity<Resource> profileImage(@AuthenticationPrincipal Jwt jwt) {
        FileContent file = imageService.read(userId(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.stream()));
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

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
