package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.product.dto.BrandProductOrderResponse;
import com.ssafy.upcycle.product.dto.ProductOrderCancelRequest;
import com.ssafy.upcycle.product.dto.ProductOrderCreateRequest;
import com.ssafy.upcycle.product.dto.ProductOrderPaymentReadyResponse;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.dto.ProductOrderSearchCondition;
import com.ssafy.upcycle.product.dto.ProductOrderShipRequest;
import com.ssafy.upcycle.product.dto.ProductOrderShipResponse;
import com.ssafy.upcycle.product.dto.ProductOrderStatusSummaryResponse;
import com.ssafy.upcycle.product.service.BrandProductOrderQueryService;
import com.ssafy.upcycle.product.service.ProductOrderCommandService;
import com.ssafy.upcycle.product.service.ProductOrderDeliveryService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제품 주문 API. <b>소비자와 브랜드가 같은 주문을 양쪽에서 다룬다.</b>
 *
 * <pre>
 *   CREATED ──결제 승인──▶ PAID ──브랜드 발송──▶ SHIPPED ──소비자 수령 확인──▶ DELIVERED
 * </pre>
 *
 * <p>소비자는 사고({@code POST}), 결제하고, 발송 전까지 취소하고, 받았다고 확인한다.
 * 브랜드는 들어온 주문을 보고({@code /brand/me}) 발송한다({@code /\{orderId\}/ship}).
 *
 * <p>사용자는 언제나 JWT 의 {@code sub} 에서 얻는다. 경로나 조건으로 사람을 지정하게 하면
 * 남의 주문을, 그리고 그 배송지를 들여다볼 길이 생긴다.
 *
 * <p><b>결제·취소는 묶이지 않은 주문만 받는다.</b> 장바구니로 만든 주문을 여기로 보내면
 * {@code PRODUCT_ORDER_BELONGS_TO_GROUP} 으로 거절하고 갈 묶음을 상세에 실어 답한다 —
 * 결제가 묶음 단위라 주문 한 건만 따로 결제하거나 취소하면 돈과 주문이 어긋난다. 발송과
 * 수령 확인은 그렇지 않다: 브랜드가 따로 보내므로 배송은 묶음 주문에서도 주문 단위다.
 */
@RestController
@RequestMapping("/api/v1/product-orders")
@RequiredArgsConstructor
@Validated
public class ProductOrderController {

    private final ProductOrderQueryService productOrderQueryService;
    private final ProductOrderCommandService productOrderCommandService;
    private final ProductOrderPaymentService productOrderPaymentService;
    private final ProductOrderDeliveryService productOrderDeliveryService;
    private final BrandProductOrderQueryService brandProductOrderQueryService;

    /**
     * 브랜드 본인에게 들어온 주문 목록. 최신순이고 <b>배송지가 함께 나간다.</b>
     *
     * <p>발송할 건만 보려면 {@code ?status=PAID} 로 거른다 — 결제가 끝났고 아직
     * 보내지 않은 주문이 정확히 그 상태다. 건수만 필요하면 같은 조건으로 {@code size=1} 을
     * 불러 {@code totalElements} 를 읽으면 된다.
     *
     * <p>범위는 언제나 JWT 의 사용자 id 다. 경로나 조건으로 브랜드를 지정하게 하면
     * 남의 주문과 그 배송지를 들여다볼 길이 생긴다 — 배송지는 남의 집 주소다.
     */
    @GetMapping("/brand/me")
    @PreAuthorize("hasRole('BRAND')")
    public ApiResponse<PageResponse<BrandProductOrderResponse>> findIncomingOrders(
            @AuthenticationPrincipal Jwt jwt,
            @ModelAttribute ProductOrderSearchCondition condition) {
        return ApiResponse.success(
                brandProductOrderQueryService.findIncomingOrders(userId(jwt), condition));
    }

    /**
     * 브랜드가 제품을 발송한다. 결제가 끝난 주문만 가능하다.
     *
     * <p>{@code @PreAuthorize} 는 BRAND 역할까지만 가린다. <b>그 주문을 판 브랜드인지는
     * 서비스가 주문의 {@code brand_id} 로 다시 판정한다</b> — 역할만 보면 다른 브랜드의
     * 주문을 발송 처리할 수 있게 된다.
     *
     * <p>발송 이후에는 취소가 막힌다. 물건이 나갔는데 주문만 없애면 그 개체가 어디에도
     * 속하지 않게 된다.
     */
    @PatchMapping("/{orderId}/ship")
    @PreAuthorize("hasRole('BRAND')")
    public ApiResponse<ProductOrderShipResponse> ship(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "주문 id는 양수여야 합니다.") Long orderId,
            @Valid @RequestBody(required = false) ProductOrderShipRequest request) {
        return ApiResponse.success(productOrderDeliveryService.ship(
                userId(jwt), orderId,
                request == null ? new ProductOrderShipRequest(null, null) : request));
    }

    /**
     * 소비자가 수령을 확인한다. <b>주문이 끝나는 지점이다.</b>
     *
     * <p>구매자 본인만 할 수 있다. 브랜드가 대신 눌러 완료시킬 수 있으면 2-Track 을 둔
     * 의미가 없어진다 — 받았다고 말할 수 있는 사람은 받은 사람뿐이다.
     *
     * <p>소재 거래와 달리 서명이 없다. 소비자는 지갑을 쓰지 않으므로 확인 행위 자체가
     * 유일한 근거다.
     */
    @PostMapping("/{orderId}/receive/confirm")
    public ApiResponse<ProductOrderResponse> confirmReceipt(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "주문 id는 양수여야 합니다.") Long orderId) {
        return ApiResponse.success(
                productOrderDeliveryService.confirmReceipt(userId(jwt), orderId));
    }

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

    /**
     * 본인 주문 한 건.
     *
     * <p><b>결제창에서 돌아온 화면이 그 주문을 다시 읽는 경로다.</b> 이 경로가 없으면 화면은
     * 주문 목록을 페이지마다 훑으며 id 가 같은 건을 찾아야 한다 — 주문이 쌓일수록 복귀 한
     * 번에 요청이 여러 번 나간다.
     *
     * <p>남의 주문을 가리키면 <b>"없음" 으로 답한다.</b> 403 은 그 id 의 주문이 존재한다는
     * 사실을 흘리고, 주문 id 는 순번이라 하나씩 올려 보며 남의 주문 유무를 셀 수 있다.
     * 묶음 조회({@code GET /api/v1/product-order-groups/\{orderGroupId\}})와 같은 방식이다.
     *
     * <p>응답은 목록·생성·승인과 같은 {@link ProductOrderResponse} 다. 단건 전용 모양을
     * 따로 만들면 같은 주문이 화면마다 다른 형태로 도착한다.
     */
    @GetMapping("/{orderId}")
    public ApiResponse<ProductOrderResponse> findMyOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "주문 id는 양수여야 합니다.") Long orderId) {
        return ApiResponse.success(productOrderQueryService.findMyOrder(userId(jwt), orderId));
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
