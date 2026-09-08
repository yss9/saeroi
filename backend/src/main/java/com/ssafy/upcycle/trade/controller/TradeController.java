package com.ssafy.upcycle.trade.controller;

import com.ssafy.upcycle.blockchain.dto.ChainLinkRequest;
import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.dto.PaymentApproveRequest;
import com.ssafy.upcycle.trade.dto.PaymentReadyResponse;
import com.ssafy.upcycle.trade.dto.TradeCancelRequest;
import com.ssafy.upcycle.trade.dto.TradeCreateRequest;
import com.ssafy.upcycle.trade.dto.TradeQuoteResponse;
import com.ssafy.upcycle.trade.dto.TradeReceiptResponse;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.dto.TradeSearchCondition;
import com.ssafy.upcycle.trade.dto.TradeShipRequest;
import com.ssafy.upcycle.trade.dto.TradeShipResponse;
import com.ssafy.upcycle.trade.service.PaymentService;
import com.ssafy.upcycle.trade.service.TradeChainLinkService;
import com.ssafy.upcycle.trade.service.TradeCommandService;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import com.ssafy.upcycle.trade.service.TradeReceiptService;
import com.ssafy.upcycle.trade.service.TradeShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소재 거래 — 구매 요청부터 수령 확인까지. <b>인증이 필요하다.</b>
 *
 * <p>결제를 별도 컨트롤러로 나누지 않았다. 결제는 거래의 <b>상태 전이</b>이지 독립된 리소스가
 * 아니다 — 결제만 조회하거나 목록으로 볼 일이 없고, 항상 "어떤 거래의 결제" 다.
 *
 * <p><b>경로에 {@code /v1} 이 있다.</b> 발송·수령이 먼저 {@code /api/v1/trades} 로 자리를
 * 잡았고, 같은 리소스를 두 버전으로 쪼갤 수 없어 구매·결제도 여기로 맞췄다.
 * 다만 {@code MaterialController} 는 {@code /api/materials} 로 무버전이다 —
 * <b>프로젝트 전체가 아직 한 관례로 통일되지 않았다.</b> 통일은 프론트와 같은 주기에 한 번에 한다.
 */
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeCommandService tradeCommandService;
    private final TradeQueryService tradeQueryService;
    private final PaymentService paymentService;
    private final TradeChainLinkService tradeChainLinkService;
    private final TradeShipmentService tradeShipmentService;
    private final TradeReceiptService tradeReceiptService;

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

    /**
     * 내 거래 목록. <b>남의 목록은 볼 수 없다</b> — 사용자 id 를 파라미터로 받지 않고
     * JWT 에서 강제한다.
     *
     * <p>{@code side=BUY}(기본)는 내가 산 것, {@code side=SELL}은 내가 판 것이다.
     * 결제 후 상태를 확인하는 브랜드와 발송 대기 건을 보는 판매자가 같은 엔드포인트를 쓴다.
     */
    @GetMapping
    public ApiResponse<PageResponse<TradeResponse>> findMyTrades(
            @AuthenticationPrincipal Jwt jwt, @ModelAttribute TradeSearchCondition condition) {
        return ApiResponse.success(tradeQueryService.findMyTrades(userId(jwt), condition));
    }

    /** 거래 상세. 당사자(판매자·구매자)만 볼 수 있다. */
    @GetMapping("/{tradeId}")
    public ApiResponse<TradeResponse> findDetail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long tradeId) {
        return ApiResponse.success(tradeQueryService.findDetail(userId(jwt), tradeId));
    }

    /**
     * 결제 준비. 구매자만 가능하다. <b>아직 청구되지 않는다.</b>
     *
     * <p>응답의 {@code redirectUrl} 로 사용자를 보내면 카카오페이 결제창이 열린다. 그 값이
     * 비어 있으면 결제 키가 설정되지 않아 Mock 게이트웨이가 붙은 것이고, 화면은 이동 없이
     * 바로 승인 단계로 갈 수 있다 ({@code live} 로 판단한다).
     */
    @PostMapping("/{tradeId}/payment/ready")
    public ApiResponse<PaymentReadyResponse> readyPayment(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long tradeId) {
        return ApiResponse.success(paymentService.ready(userId(jwt), tradeId));
    }

    /**
     * 결제 승인. <b>이 시점에 실제로 청구된다.</b> 구매자만 가능하다.
     *
     * <p>결제창이 복귀 URL 로 돌려준 {@code pgToken} 만 받는다. 금액과 {@code tid} 는 받지
     * 않는다 — 금액은 서버가 PG 응답에서 확인하고 거래 금액과 대조하며, tid 는 준비 단계에서
     * 거래에 저장해 두었다. 둘 다 클라이언트가 말하게 두면 조작 경로가 된다.
     */
    @PostMapping("/{tradeId}/payment/approve")
    public ApiResponse<TradeResponse> approvePayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long tradeId,
            @Valid @RequestBody PaymentApproveRequest request) {
        return ApiResponse.success(paymentService.approve(userId(jwt), tradeId, request.pgToken()));
    }

    /**
     * 거래 취소. 당사자 양쪽 모두 가능하고 <b>발송 전까지만</b>이다.
     *
     * <p>판매 글의 잔여 수량이 함께 복구된다. 결제창을 닫은 사용자를 이 경로로 보내지 않으면
     * 그만큼의 판매 수량이 영구히 묶인다 — 화면의 결제 취소 흐름이 반드시 호출해야 한다.
     *
     * <p>{@code DELETE} 가 아니라 {@code POST /cancel} 인 것은 거래 행을 지우지 않기 때문이다.
     * 취소된 거래도 이력이고, 같은 판매 글에서 몇 건이 취소됐는지가 분쟁 때 근거가 된다.
     *
     * <p><b>바디는 없어도 된다.</b> 사유를 적을 자리가 없는 취소(결제창 이탈 등)가 정상
     * 경로이고, 기존 호출부도 바디 없이 부르고 있었다. 시각은 사유와 무관하게 기록된다.
     */
    @PostMapping("/{tradeId}/cancel")
    public ApiResponse<TradeResponse> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long tradeId,
            @Valid @RequestBody(required = false) TradeCancelRequest request) {
        TradeCancelRequest cancelRequest =
                request == null ? TradeCancelRequest.empty() : request;
        return ApiResponse.success(
                paymentService.cancel(userId(jwt), tradeId, cancelRequest.reason()));
    }

    /**
     * 판매자 발송 처리. 송장 정보는 선택이다.
     *
     * <p>잠금과 보유량은 여기서 바뀌지 않는다. 소유권은 구매자 수령 확인 시점에 넘어간다.
     *
     * <p>역할 검사만으로는 부족하다 — 그 역할이면 누구나 남의 거래를 발송 처리할 수 있게
     * 되기 때문이다. 실제 판매자인지는 서비스가 거래의 {@code seller_id} 와 대조한다.
     *
     * <p>{@code SUPPLIER} 만인 것이 {@link ListingController} 의 판매 등록 자격과 같다.
     * 두 목록은 <b>항상 같아야 한다</b> — 어긋나면 팔리고 결제까지 됐는데 발송이 막히는
     * 거래가 생기고, 판매자가 그 거래를 끝낼 방법이 없어진다.
     */
    @PatchMapping("/{tradeId}/ship")
    @PreAuthorize("hasRole('SUPPLIER')")
    public ApiResponse<TradeShipResponse> ship(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long tradeId,
            @Valid @RequestBody TradeShipRequest request) {
        return ApiResponse.success(tradeShipmentService.ship(userId(jwt), tradeId, request));
    }

    /**
     * 구매자 수령 확인. 소유권이 실제로 넘어가는 자리다.
     *
     * <p>요청 바디가 없다. API 명세는 프론트가 온체인 {@code confirmReceipt()} 를
     * 서명·전송한 뒤 {@code txHash} 를 넘기도록 정의하지만, 지금은 그 해시를 남길
     * {@code blockchain_transaction} 매핑이 없다. 받아서 버리면 기록된 것처럼 보이므로
     * 온체인 연동이 붙을 때 파라미터를 추가한다.
     */
    @PostMapping("/{tradeId}/receive/confirm")
    @PreAuthorize("hasRole('BRAND')")
    public ApiResponse<TradeReceiptResponse> confirmReceipt(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long tradeId) {
        return ApiResponse.success(tradeReceiptService.confirmReceipt(userId(jwt), tradeId));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로 넣는다.
     * 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부한다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    /**
     * 거래의 온체인 주문 번호를 연결한다. <b>구매자 본인만</b> 부를 수 있다.
     *
     * <p>화면이 {@code Marketplace.createOrder(chainListingId, quantity)} 를 구매자 지갑으로
     * 보내고, {@code OrderCreated} 이벤트의 {@code orderId} 를 여기로 알려 준다.
     *
     * <p><b>이 번호가 있어야 발송·수령 확인을 온체인에서 부를 수 있다</b> —
     * {@code markShipped(orderId)} · {@code confirmReceipt(orderId)} 가 이 값을 인자로 받는다.
     * 그래서 이것이 연결되지 않은 거래는 오프체인으로만 진행된다.
     */
    @PostMapping("/{tradeId}/chain")
    public ApiResponse<Void> linkOnChain(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long tradeId,
            @Valid @RequestBody ChainLinkRequest request) {
        tradeChainLinkService.linkTrade(userId(jwt), tradeId, request);
        return ApiResponse.success(null);
    }
}
