package com.ssafy.upcycle.processing.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.service.ProcessorSearchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProcessorSearchController {
    private final ProcessorSearchService service;

    @GetMapping({"/processors/search","/api/v1/processors/search"})
    public ApiResponse<ProcessorSearchResponse> search(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue="") String materialType,
            @RequestParam(defaultValue="") String quantity,
            @RequestParam(required=false) List<String> processTypes,
            @RequestParam(defaultValue="0") String page,
            @RequestParam(defaultValue="12") String size) {
        return ApiResponse.success(service.search(Long.valueOf(jwt.getSubject()),materialType,quantity,processTypes,page,size));
    }

    @GetMapping({"/processing-requests/materials","/api/v1/processing-requests/materials"})
    public ApiResponse<List<ProcessingMaterialResponse>> materials(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.materials(Long.valueOf(jwt.getSubject())));
    }
}
