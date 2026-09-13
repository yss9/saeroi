package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.dto.ProductOrderSearchCondition;
import com.ssafy.upcycle.product.dto.ProductOrderStatusSummaryResponse;
import com.ssafy.upcycle.product.service.ProductOrderQueryService;
import com.ssafy.upcycle.trade.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 소비자 본인의 제품 주문 조회 API. */
@RestController
@RequestMapping("/api/v1/product-orders")
@RequiredArgsConstructor
public class ProductOrderController {

    private final ProductOrderQueryService productOrderQueryService;

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
