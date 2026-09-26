package com.ssafy.upcycle.trade.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 결제창 복귀 주소가 어떻게 정해지는가.
 *
 * <p><b>여기서 막는 것은 배포에서 복귀 주소가 조용히 {@code localhost} 로 떨어지는 일이다.</b>
 * 카카오페이는 그런 {@code approval_url} 을 거부해 결제가 통째로 실패하는데, 화면에는
 * "결제 처리 중 오류" 만 뜨고 원인은 서버 로그에만 남는다. 제품 주문 결제를 붙일 때 실제로
 * 이 일이 일어났다 — 새 환경변수를 배포 쪽에 추가하지 않아 기본값이 적용됐다.
 */
class KakaoPayPropertiesTests {

    private static final String SECRET = "DEV_SECRET";

    // ── 제품 주문 복귀 주소 ─────────────────────────────────

    @Test
    void theProductOrderUrlFollowsTheTradeUrlWithoutBeingConfigured() {
        // 배포가 KAKAOPAY_RETURN_URL 하나만 넘겨도 제품 주문 결제가 동작해야 한다.
        KakaoPayProperties properties = properties(
                "https://j15e104.p.ssafy.io/trades/{tradeId}/payment/return", null);

        assertThat(properties.productOrderReturnUrlFor(12L))
                .isEqualTo("https://j15e104.p.ssafy.io/orders/12/payment/return");
    }

    @Test
    void theFrontendBasenameIsCarriedOverToo() {
        // test 배포는 /test 프리픽스 아래에 있다. 이걸 잃으면 결제 후 라우팅이 깨진다.
        KakaoPayProperties properties = properties(
                "https://j15e104.p.ssafy.io/test/trades/{tradeId}/payment/return", null);

        assertThat(properties.productOrderReturnUrlFor(12L))
                .isEqualTo("https://j15e104.p.ssafy.io/test/orders/12/payment/return");
    }

    @Test
    void anExplicitSettingAlwaysWins() {
        // 두 라우트가 갈라지는 날이 오면 따로 지정할 수 있어야 한다.
        KakaoPayProperties properties = properties(
                "https://j15e104.p.ssafy.io/test/trades/{tradeId}/payment/return",
                "https://j15e104.p.ssafy.io/test/my-orders/{orderId}/done");

        assertThat(properties.productOrderReturnUrlFor(12L))
                .isEqualTo("https://j15e104.p.ssafy.io/test/my-orders/12/done");
    }

    @Test
    void localDevelopmentStillPointsAtTheDevServer() {
        // 아무것도 설정하지 않으면 두 값 모두 localhost 다. 로컬에서는 그게 맞다.
        KakaoPayProperties properties = properties(null, null);

        assertThat(properties.productOrderReturnUrlFor(12L))
                .isEqualTo("http://localhost:5173/orders/12/payment/return");
    }

    @Test
    void anUnrecognisableTradeUrlFallsBackInsteadOfProducingNonsense() {
        // 주소 규칙 자체를 바꾼 경우다. 끌어낼 근거가 없으므로 상수로 물러나고,
        // 기동은 막지 않는다 — 제품 주문과 무관한 소재 거래 배포까지 멈추면 안 된다.
        KakaoPayProperties properties = properties(
                "https://j15e104.p.ssafy.io/pay/callback", null);

        assertThat(properties.productOrderReturnUrlFor(12L))
                .isEqualTo("http://localhost:5173/orders/12/payment/return");
    }

    // ── 주문 묶음 복귀 주소 ─────────────────────────────────

    @Test
    void theOrderGroupUrlIsDerivedTheSameWay() {
        // 장바구니 주문도 배포가 KAKAOPAY_RETURN_URL 하나만 넘겨도 동작해야 한다.
        KakaoPayProperties properties = properties(
                "https://j15e104.p.ssafy.io/test/trades/{tradeId}/payment/return", null);

        assertThat(properties.productOrderGroupReturnUrlFor(12L))
                .isEqualTo("https://j15e104.p.ssafy.io/test/order-groups/12/payment/return");
    }

    @Test
    void theOrderGroupUrlIsSeparateFromTheSingleOrderUrl() {
        // 묶음은 브랜드별 주문 여러 건을 한 화면에 보여 준다. 단일 주문 라우트로 보내면
        // 화면이 주문 한 건을 찾다 실패한다.
        KakaoPayProperties properties = properties(null, null);

        assertThat(properties.productOrderGroupReturnUrlFor(12L))
                .isEqualTo("http://localhost:5173/order-groups/12/payment/return");
        assertThat(properties.productOrderReturnUrlFor(12L))
                .isEqualTo("http://localhost:5173/orders/12/payment/return");
    }

    @Test
    void anExplicitOrderGroupSettingAlsoWins() {
        KakaoPayProperties properties = properties(
                "https://j15e104.p.ssafy.io/trades/{tradeId}/payment/return",
                null,
                "https://j15e104.p.ssafy.io/carts/{orderGroupId}/done");

        assertThat(properties.productOrderGroupReturnUrlFor(12L))
                .isEqualTo("https://j15e104.p.ssafy.io/carts/12/done");
    }

    // ── 치환 ────────────────────────────────────────────────

    @Test
    void eachOrderGetsItsOwnReturnAddress() {
        // 복귀 시점에 어느 주문의 승인인지 알아야 한다.
        KakaoPayProperties properties = properties(null, null);

        assertThat(properties.productOrderReturnUrlFor(7L)).endsWith("/orders/7/payment/return");
        assertThat(properties.productOrderReturnUrlFor(8L)).endsWith("/orders/8/payment/return");
    }

    @Test
    void theTradeUrlIsUntouched() {
        // 소재 거래는 이미 동작하고 있다. 제품 주문을 붙이면서 그쪽을 건드리면 안 된다.
        KakaoPayProperties properties = properties(
                "https://j15e104.p.ssafy.io/test/trades/{tradeId}/payment/return", null);

        assertThat(properties.returnUrlFor(3L))
                .isEqualTo("https://j15e104.p.ssafy.io/test/trades/3/payment/return");
    }

    // ── 대화창 복귀 주소 ────────────────────────────────────

    @Test
    void theAgentUrlFollowsTheTradeUrlWithoutBeingConfigured() {
        // 배포가 KAKAOPAY_RETURN_URL 하나만 넘겨도 대화 안에서 시작한 결제가 돌아와야 한다.
        KakaoPayProperties properties = properties(
                "https://j15e104.p.ssafy.io/trades/{tradeId}/payment/return", null);

        assertThat(properties.agentReturnUrlFor(12L))
                .isEqualTo("https://j15e104.p.ssafy.io/agent/payment/callback?tradeId=12");
    }

    @Test
    void theAgentUrlKeepsTheFrontendBasename() {
        KakaoPayProperties properties = properties(
                "https://j15e104.p.ssafy.io/test/trades/{tradeId}/payment/return", null);

        assertThat(properties.agentReturnUrlFor(12L))
                .isEqualTo("https://j15e104.p.ssafy.io/test/agent/payment/callback?tradeId=12");
    }

    @Test
    void theAgentUrlIsNotTheTradeDetailPage() {
        // 거래 상세로 보내면 대화창이 다른 화면으로 바뀐다. 대화 안에서 결제까지 끝내는
        // 것이 이 기능의 요점이라, 두 주소가 같아지는 순간 기능이 무너진다.
        KakaoPayProperties properties = properties(
                "https://j15e104.p.ssafy.io/trades/{tradeId}/payment/return", null);

        assertThat(properties.agentReturnUrlFor(12L))
                .isNotEqualTo(properties.returnUrlFor(12L))
                .contains("/agent/");
    }

    @Test
    void theAgentUrlFallsBackToTheDevServerLocally() {
        KakaoPayProperties properties = properties(null, null);

        assertThat(properties.agentReturnUrlFor(12L))
                .isEqualTo("http://localhost:5173/agent/payment/callback?tradeId=12");
    }

    // ── helpers ─────────────────────────────────────────────

    private KakaoPayProperties properties(String returnUrl, String productOrderReturnUrl) {
        return properties(returnUrl, productOrderReturnUrl, null);
    }

    private KakaoPayProperties properties(
            String returnUrl, String productOrderReturnUrl, String productOrderGroupReturnUrl) {
        return new KakaoPayProperties(
                null, null, SECRET, returnUrl, productOrderReturnUrl, productOrderGroupReturnUrl,
                null, Duration.ofSeconds(10));
    }
}
