package com.ssafy.upcycle.processing.controller;
import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.processing.dto.*;
import com.ssafy.upcycle.processing.service.ProcessingJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping({"/quotes","/api/v1/quotes"})
public class ProcessingQuoteController {
    private final ProcessingJobService service;
    @PostMapping("/{quoteId}/select") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProcessingJobResponse> select(@AuthenticationPrincipal Jwt jwt, @PathVariable Long quoteId,
            @Valid @RequestBody SelectProcessingQuoteRequest input) {
        return ApiResponse.success(service.select(Long.valueOf(jwt.getSubject()), quoteId, input));
    }
}
