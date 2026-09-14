package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.product.dto.ProductOrderCreateRequest;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.dto.ProductOrderSearchCondition;
import com.ssafy.upcycle.product.dto.ProductOrderStatusSummaryResponse;
import com.ssafy.upcycle.product.service.ProductOrderCommandService;
import com.ssafy.upcycle.product.service.ProductOrderQueryService;
import com.ssafy.upcycle.trade.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 소비자 본인의 제품 주문 API. */
@RestController
@RequestMapping("/api/v1/product-orders")
@RequiredArgsConstructor
public class ProductOrderController {

    private final ProductOrderQueryService productOrderQueryService;
    private final ProductOrderCommandService productOrderCommandService;

    /**
     * 제품을 주문한다. 한 주문은 개체 하나다.
     *
     * <p>구매자는 JWT {@code sub} 에서 얻고 금액도 서버가 계산한다 — 요청에
     * 가격이 없는 이유다. 수량도, 어느 개체를 팔지도 받지 않는다
     * ({@code ProductOrderCreateRequest} 문서 참고).
     *
     * <p>여기 {@code @PreAuthorize} 가 없다고 해서 아무나 살 수 있는 것이 아니다 —
     * {@code ProductOrderCommandService} 가 DB 의 현재 역할을 읽어 CONSUMER 만 통과시킨다.
     * 토큰의 역할 클레임은 발급 시점의 스냅샷이라 정지되거나 역할이 바뀐 계정이
     * 만료 전까지 통과한다.
     */
    @PostMapping
    public ApiResponse<ProductOrderResponse> place(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProductOrderCreateRequest request) {
        return ApiResponse.success(productOrderCommandService.place(userId(jwt), request));
    }

    /** 최신순 본인 주문 목록. 구매자 id 는 JWT 에서만 얻는다. */
    @GetMapping("/me")
    public ApiResponse<PageResponse<ProductOrderResponse>> findMyOrders(
            @AuthenticationPrincipal Jwt jwt,
            @ModelAttribute ProductOrderSearchCondition condition) {
        return ApiResponse.success(productOrderQueryService.findMyOrders(userId(jwt), condition));
    }

    /** 마이페이지 주문 진행 현황용 상태별 전체 건수. */
    @GetMapping("/me/summary")
    public ApiResponse<ProductOrderStatusSummaryResponse> summarizeMyOrders(
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(productOrderQueryService.summarizeMyOrders(userId(jwt)));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
