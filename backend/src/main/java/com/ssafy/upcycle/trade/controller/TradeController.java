package com.ssafy.upcycle.trade.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.trade.dto.PaymentConfirmRequest;
import com.ssafy.upcycle.trade.dto.TradeCreateRequest;
import com.ssafy.upcycle.trade.dto.TradeQuoteResponse;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.service.PaymentService;
import com.ssafy.upcycle.trade.service.TradeCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소재 거래와 결제. <b>인증이 필요하다.</b>
 *
 * <p>결제를 별도 컨트롤러로 나누지 않았다. 결제는 거래의 <b>상태 전이</b>이지 독립된 리소스가
 * 아니다 — 결제만 조회하거나 목록으로 볼 일이 없고, 항상 "어떤 거래의 결제" 다.
 * 그래서 {@code /trades/{id}/payment} 하위 경로로 둔다.
 *
 * <p>배송 처리와 수령 확인({@code /ship}, {@code /receipt})은 여기 없다. 스토리 -30·-31 의
 * 범위이고 담당이 다르다. 같은 컨트롤러에 붙게 되겠지만 지금 빈 메서드를 만들어 두지는 않는다.
 */
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeCommandService tradeCommandService;
    private final PaymentService paymentService;

    /**
     * 구매 전 금액 미리보기. <b>아무것도 바꾸지 않는다.</b>
     *
     * <p>{@code GET} 인 이유가 여기 있다 — 화면이 수량 입력을 바꿀 때마다 부담 없이 부를 수
     * 있어야 한다. 다만 화면도 같은 계산을 하므로 매 입력마다 부를 필요는 없고,
     * 구매 버튼을 누르기 직전 한 번 확인하는 용도다.
     */
    @GetMapping("/quote")
    public ApiResponse<TradeQuoteResponse> quote(
            @RequestParam Long listingId,
            @RequestParam long quantity,
            @RequestParam MaterialQuantityUnit unit) {
        return ApiResponse.success(tradeCommandService.quote(listingId, quantity, unit));
    }

    /**
     * 구매 요청. 거래가 {@code CREATED} 로 만들어지고 <b>판매 잔여 수량이 즉시 줄어든다.</b>
     *
     * <p>결제 전인데 수량을 먼저 줄이는 이유는, 결제창에 머무는 동안 다른 사람이 같은 수량을
     * 사가면 결제를 마치고 돌아온 사용자에게 없는 물건을 팔게 되기 때문이다. 이탈한 거래는
     * {@code /cancel} 로 수량이 되돌아온다.
     *
     * <p>구매자는 JWT {@code sub} 에서 얻는다. 금액도 서버가 계산한다 — 요청에
     * {@code totalPrice} 가 없는 이유다.
     */
    @PostMapping
    public ApiResponse<TradeResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TradeCreateRequest request) {
        return ApiResponse.success(tradeCommandService.create(userId(jwt), request));
    }

    /** 거래 상세. 당사자(판매자·구매자)만 볼 수 있다. */
    @GetMapping("/{tradeId}")
    public ApiResponse<TradeResponse> findDetail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long tradeId) {
        return ApiResponse.success(tradeCommandService.findDetail(userId(jwt), tradeId));
    }

    /**
     * 결제 승인 통보. 구매자만 가능하며 승인 금액이 거래 금액과 같아야 한다.
     *
     * <p>PG 연동 전이라 클라이언트가 결과를 알려주는 구조다. 그래서 금액 대조가 필수이며,
     * 서버가 PG 를 직접 부르게 되면 이 엔드포인트의 요청 바디만 바뀐다.
     */
    @PostMapping("/{tradeId}/payment")
    public ApiResponse<TradeResponse> confirmPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long tradeId,
            @Valid @RequestBody PaymentConfirmRequest request) {
        return ApiResponse.success(paymentService.confirm(userId(jwt), tradeId, request));
    }

    /**
     * 거래 취소. 당사자 양쪽 모두 가능하고 <b>발송 전까지만</b>이다.
     *
     * <p>판매 글의 잔여 수량이 함께 복구된다. 결제창을 닫은 사용자를 이 경로로 보내지 않으면
     * 그만큼의 판매 수량이 영구히 묶인다 — 화면의 결제 취소 흐름이 반드시 호출해야 한다.
     *
     * <p>{@code DELETE} 가 아니라 {@code POST /cancel} 인 것은 거래 행을 지우지 않기 때문이다.
     * 취소된 거래도 이력이고, 같은 판매 글에서 몇 건이 취소됐는지가 분쟁 때 근거가 된다.
     */
    @PostMapping("/{tradeId}/cancel")
    public ApiResponse<TradeResponse> cancel(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long tradeId) {
        return ApiResponse.success(paymentService.cancel(userId(jwt), tradeId));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
