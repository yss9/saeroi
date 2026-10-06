package com.ssafy.upcycle.blockchain.controller;

import com.ssafy.upcycle.blockchain.dto.*;
import com.ssafy.upcycle.blockchain.service.BlockchainOperationService;
import com.ssafy.upcycle.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/blockchain/operations")
public class BlockchainOperationController {
    private final BlockchainOperationService service;
    @PostMapping
    public ApiResponse<BlockchainOperationResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BlockchainOperationCreateRequest request) {
        return ApiResponse.success(service.create(Long.valueOf(jwt.getSubject()), request));
    }
    @PutMapping("/{operationId}/transaction")
    public ApiResponse<BlockchainOperationResponse> submit(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^0x[0-9a-fA-F]{64}$") String operationId,
            @Valid @RequestBody BlockchainOperationSubmitRequest request) {
        return ApiResponse.success(service.submit(Long.valueOf(jwt.getSubject()), operationId, request));
    }
    @GetMapping("/{operationId}")
    public ApiResponse<BlockchainOperationResponse> find(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^0x[0-9a-fA-F]{64}$") String operationId) {
        return ApiResponse.success(service.find(Long.valueOf(jwt.getSubject()), operationId));
    }
}
