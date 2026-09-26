package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.product.dto.ProductOrderCancelRequest;
import com.ssafy.upcycle.product.dto.ProductOrderGroupPaymentReadyResponse;
import com.ssafy.upcycle.product.dto.ProductOrderGroupResponse;
import com.ssafy.upcycle.product.service.ProductOrderGroupPaymentService;
import com.ssafy.upcycle.product.service.ProductOrderQueryService;
import com.ssafy.upcycle.trade.dto.PaymentApproveRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장바구니로 만든 주문 묶음. <b>결제와 취소의 단위가 여기다.</b>
 *
 * <p>브랜드가 둘 이상이면 주문은 브랜드 수만큼 생기지만 결제는 한 번이다. 그래서 결제
 * 준비·승인·취소가 주문이 아니라 묶음을 가리킨다. 단일 주문("바로 구매")은 기존
 * {@code /api/v1/product-orders/\{orderId\}} 경로를 그대로 쓴다.
 *
 * <p><b>부분 취소가 없다.</b> 브랜드 한 곳만 취소할 수는 없고 묶음 전체가 취소된다 —
 * 결제가 한 번이라 부분 환불 금액을 나눌 근거가 없다. 화면은 그 사실을 취소 확인 문구에
 * 담아야 하고, 지금 취소할 수 있는지는 조회 응답의 {@code cancelable} 로 알 수 있다.
 *
 * <p>구매자는 JWT {@code sub} 에서만 얻는다. 남의 묶음을 가리키면 "없음" 으로 답한다.
 */
@RestController
@RequestMapping("/api/v1/product-order-groups")
@RequiredArgsConstructor
@Validated
public class ProductOrderGroupController {

    private final ProductOrderGroupPaymentService productOrderGroupPaymentService;
    private final ProductOrderQueryService productOrderQueryService;

    /**
     * 묶음 한 건. 결제·취소 화면이 읽는다.
     *
     * <p>브랜드별 주문이 안에 들어 있고 배송도 상태도 주문마다다 — 브랜드가 따로 보내기
     * 때문이다. 합계와 결제 상태는 묶음이 갖는다.
     */
    @GetMapping("/{orderGroupId}")
    public ApiResponse<ProductOrderGroupResponse> findMyGroup(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "주문 묶음 id는 양수여야 합니다.") Long orderGroupId) {
        return ApiResponse.success(
                productOrderQueryService.findMyGroup(userId(jwt), orderGroupId));
    }

    /**
     * 결제 준비. <b>아직 청구되지 않는다.</b>
     *
     * <p>응답의 {@code redirectUrl} 로 사용자를 보내면 결제창이 열린다. 비어 있으면 결제
     * 키가 없어 Mock 게이트웨이가 붙은 것이고({@code live} 가 {@code false}), 화면은 이동
     * 없이 바로 승인 단계로 갈 수 있다.
     */
    @PostMapping("/{orderGroupId}/payment/ready")
    public ApiResponse<ProductOrderGroupPaymentReadyResponse> readyPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "주문 묶음 id는 양수여야 합니다.") Long orderGroupId) {
        return ApiResponse.success(
                productOrderGroupPaymentService.ready(userId(jwt), orderGroupId));
    }

    /**
     * 결제 승인. <b>이 시점에 실제로 청구된다.</b>
     *
     * <p>결제창이 복귀 URL 로 돌려준 {@code pgToken} 만 받는다. 금액과 {@code tid} 는 받지
     * 않는다 — 둘 다 클라이언트가 말하게 두면 조작 경로가 된다. 단일 주문과 같다.
     *
     * <p>승인이 끝나면 속한 주문이 전부 결제됨으로 바뀌고, 주문에 들어간 장바구니 항목이
     * 비워진다. 사지 못한 항목은 남는다.
     */
    @PostMapping("/{orderGroupId}/payment/approve")
    public ApiResponse<ProductOrderGroupResponse> approvePayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "주문 묶음 id는 양수여야 합니다.") Long orderGroupId,
            @Valid @RequestBody PaymentApproveRequest request) {
        return ApiResponse.success(productOrderGroupPaymentService.approve(
                userId(jwt), orderGroupId, request.pgToken()));
    }

    /**
     * 묶음 전체 취소. 구매자만, <b>묶음의 모든 주문이 발송 전일 때만</b>이다.
     *
     * <p><b>브랜드 한 곳만 취소할 수 없다.</b> 한 건이라도 발송됐으면 아직 발송되지 않은
     * 다른 브랜드 주문도 취소되지 않고, 어느 주문이 발송됐는지가 실패 응답의 상세에 실린다.
     *
     * <p><b>결제창에서 이탈했을 때 화면이 반드시 불러야 하는 경로다.</b> 부르지 않으면 잡아
     * 둔 개체가 {@code RESERVED} 로 남는다. 탭을 그냥 닫아 호출이 오지 못한 경우는 결제
     * 마감 뒤 {@code ProductOrderExpiryWorker} 가 정리한다.
     *
     * <p>응답 본문이 없다. 취소된 주문과 묶음은 행 자체가 사라져 돌려줄 것이 없다.
     */
    @PostMapping("/{orderGroupId}/cancel")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "주문 묶음 id는 양수여야 합니다.") Long orderGroupId,
            @Valid @RequestBody(required = false) ProductOrderCancelRequest request) {
        productOrderGroupPaymentService.cancel(
                userId(jwt), orderGroupId, request == null ? null : request.reason());
        return ApiResponse.success(null);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
