package com.ssafy.upcycle.inquiry.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.inquiry.dto.ConsumerInquiryCreateRequest;
import com.ssafy.upcycle.inquiry.dto.ConsumerInquiryResponse;
import com.ssafy.upcycle.inquiry.service.ConsumerInquiryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CONSUMER 계정 본인의 1:1 문의. {@code ConsumerAddressController}/
 * {@code ConsumerProfileController} 와 같은 "내 리소스" 관례를 따른다 — 경로에 사용자 id
 * 가 없고, 남의 문의를 가리키는 요청 자체가 만들어지지 않는다.
 */
@RestController
@RequestMapping("/api/v1/consumers/me/inquiries")
@RequiredArgsConstructor
public class ConsumerInquiryController {

    private final ConsumerInquiryService consumerInquiryService;

    @GetMapping
    public ApiResponse<List<ConsumerInquiryResponse>> myInquiries(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(consumerInquiryService.myInquiries(userId(jwt)));
    }

    @PostMapping
    public ApiResponse<ConsumerInquiryResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConsumerInquiryCreateRequest request) {
        return ApiResponse.success(consumerInquiryService.create(userId(jwt), request));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
