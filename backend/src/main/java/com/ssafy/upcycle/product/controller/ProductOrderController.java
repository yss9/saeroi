package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.product.dto.ProductOrderCancelRequest;
import com.ssafy.upcycle.product.dto.ProductOrderCreateRequest;
import com.ssafy.upcycle.product.dto.ProductOrderPaymentReadyResponse;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.dto.ProductOrderSearchCondition;
import com.ssafy.upcycle.product.dto.ProductOrderStatusSummaryResponse;
import com.ssafy.upcycle.product.service.ProductOrderCommandService;
import com.ssafy.upcycle.product.service.ProductOrderPaymentService;
import com.ssafy.upcycle.product.service.ProductOrderQueryService;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.dto.PaymentApproveRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 소비자 본인의 제품 주문 API. */
@RestController
@RequestMapping("/api/v1/product-orders")
@RequiredArgsConstructor
@Validated
public class ProductOrderController {

    private final ProductOrderQueryService productOrderQueryService;
    private final ProductOrderCommandService productOrderCommandService;
    private final ProductOrderPaymentService productOrderPaymentService;

    /**
     * 결제 준비. 구매자만 가능하다. <b>아직 청구되지 않는다.</b>
     *
     * <p>응답의 {@code redirectUrl} 로 사용자를 보내면 카카오페이 결제창이 열린다. 그 값이
     * 비어 있으면 결제 키가 설정되지 않아 Mock 게이트웨이가 붙은 것이고, 화면은 이동 없이
     * 바로 승인 단계로 갈 수 있다 ({@code live} 로 판단한다).
     */
    @PostMapping("/{orderId}/payment/ready")
    public ApiResponse<ProductOrderPaymentReadyResponse> readyPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "주문 id는 양수여야 합니다.") Long orderId) {
        return ApiResponse.success(productOrderPaymentService.ready(userId(jwt), orderId));
    }

    /**
     * 결제 승인. <b>이 시점에 실제로 청구된다.</b> 구매자만 가능하다.
     *
     * <p>결제창이 복귀 URL 로 돌려준 {@code pgToken} 만 받는다. 금액과 {@code tid} 는 받지
     * 않는다 — 금액은 서버가 PG 응답에서 확인하고 주문 금액과 대조하며, tid 는 준비 단계에서
     * 주문에 저장해 두었다. 둘 다 클라이언트가 말하게 두면 조작 경로가 된다.
     *
     * <p>승인이 끝나야 개체가 팔린 것으로 기록된다.
     */
    @PostMapping("/{orderId}/payment/approve")
    public ApiResponse<ProductOrderResponse> approvePayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "주문 id는 양수여야 합니다.") Long orderId,
            @Valid @RequestBody PaymentApproveRequest request) {
        return ApiResponse.success(
                productOrderPaymentService.approve(userId(jwt), orderId, request.pgToken()));
    }

    /**
     * 주문 취소. 구매자만, <b>발송 전까지만</b>이다.
     *
     * <p><b>결제창에서 이탈했을 때 화면이 반드시 불러야 하는 경로다.</b> 부르지 않으면 그
     * 주문이 잡아 둔 개체가 {@code RESERVED} 로 남고, 제품 개체는 한 점씩이라 그 제품이
     * 바로 품절로 보인다. 탭을 그냥 닫아 이 호출이 오지 못한 경우는 결제 마감 뒤
     * {@code ProductOrderExpiryWorker} 가 정리한다.
     *
     * <p>응답 본문이 없다. 취소된 주문은 행 자체가 사라져 돌려줄 것이 없다
     * ({@code CanceledProductOrder} 문서 참고).
     */
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "주문 id는 양수여야 합니다.") Long orderId,
            @Valid @RequestBody(required = false) ProductOrderCancelRequest request) {
        productOrderPaymentService.cancel(
                userId(jwt), orderId, request == null ? null : request.reason());
        return ApiResponse.success(null);
    }

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
