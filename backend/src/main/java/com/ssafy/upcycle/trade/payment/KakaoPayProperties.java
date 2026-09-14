package com.ssafy.upcycle.trade.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오페이 설정.
 *
 * <p><b>{@code secretKey} 가 비어 있으면 실제 PG 를 붙이지 않는다</b> —
 * {@code PaymentGatewayConfig} 가 {@code MockPaymentGateway} 를 대신 등록한다.
 * 키가 없는 팀원과 시연 환경에서도 결제 흐름 전체가 돌아야 하기 때문이다.
 *
 * <p>키를 코드나 {@code application.yml} 에 적지 않는다. 환경변수로만 받는다 —
 * 결제 시크릿이 저장소에 들어가면 회수할 방법이 없다.
 *
 * @param baseUrl     카카오페이 API 호스트
 * @param cid         가맹점 코드. 테스트용은 {@code TC0ONETIME} 이다
 * @param secretKey   개발자센터에서 발급하는 Secret key. 비어 있으면 Mock 으로 떨어진다
 * @param returnUrl   결제창에서 돌아올 프론트 주소. {@code {tradeId}} 를 치환해서 쓴다
 * @param timeout     PG 호출 타임아웃
 */
@ConfigurationProperties(prefix = "app.payment.kakaopay")
public record KakaoPayProperties(
        String baseUrl,
        String cid,
        String secretKey,
        String returnUrl,
        String productOrderReturnUrl,
        java.time.Duration timeout) {

    private static final String DEFAULT_BASE_URL = "https://open-api.kakaopay.com";

    /** 카카오페이가 공개한 테스트 가맹점 코드. 실제 청구가 일어나지 않는다. */
    private static final String TEST_CID = "TC0ONETIME";

    private static final String DEFAULT_RETURN_URL =
            "http://localhost:5173/trades/{tradeId}/payment/return";

    private static final String DEFAULT_PRODUCT_ORDER_RETURN_URL =
            "http://localhost:5173/orders/{orderId}/payment/return";

    /** {@link #returnUrl} 에서 잘라 낼 지점. 이 앞이 프론트의 기준 주소다. */
    private static final String TRADE_PATH_MARKER = "/trades/";

    /** 기준 주소 뒤에 붙일 제품 주문 복귀 경로. */
    private static final String PRODUCT_ORDER_PATH = "/orders/{orderId}/payment/return";

    private static final java.time.Duration DEFAULT_TIMEOUT = java.time.Duration.ofSeconds(10);

    public KakaoPayProperties {
        baseUrl = orDefault(baseUrl, DEFAULT_BASE_URL);
        cid = orDefault(cid, TEST_CID);
        returnUrl = orDefault(returnUrl, DEFAULT_RETURN_URL);
        // returnUrl 이 먼저 확정되어야 한다 — 제품 주문 복귀 주소를 그것에서 끌어내기 때문이다.
        productOrderReturnUrl =
                orDefault(productOrderReturnUrl, derivedProductOrderReturnUrl(returnUrl));
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    }

    /**
     * 제품 주문 복귀 주소를 <b>거래 복귀 주소에서 끌어낸다.</b>
     *
     * <p>둘은 같은 프론트의 서로 다른 라우트라 도메인과 basename(예: {@code /test})이 언제나
     * 같다. 그래서 따로 설정하지 않아도 {@code KAKAOPAY_RETURN_URL} 하나만 맞으면 이 값도
     * 맞는다.
     *
     * <pre>
     *   https://j15e104.p.ssafy.io/test/trades/{tradeId}/payment/return
     *   →  https://j15e104.p.ssafy.io/test/orders/{orderId}/payment/return
     * </pre>
     *
     * <p><b>왜 상수 기본값을 그대로 두지 않는가.</b> 그 값은 {@code localhost:5173} 이고,
     * 배포 환경이 새 환경변수를 넘겨주지 않으면 <b>조용히</b> 그쪽으로 떨어진다. 카카오페이는
     * 그런 {@code approval_url} 을 거부하므로 결제가 통째로 실패하는데, 화면에는 "결제 처리 중
     * 오류" 만 뜨고 원인은 서버 로그를 봐야 안다. 이 프로젝트는 같은 함정을 이미 한 번 밟았다
     * ({@code docs/INFRA_SETUP.md} 의 2026-09-11 항목).
     *
     * <p>거래 주소가 {@code /trades/} 를 담지 않는 형태면 끌어낼 근거가 없어 상수로 물러난다.
     * 그 경우는 누군가 주소 규칙 자체를 바꾼 것이므로 이 값도 함께 지정해야 한다 — 기동을
     * 막지는 않는다. 여기서 실패시키면 제품 주문과 무관한 소재 거래 배포까지 함께 멈춘다.
     */
    private static String derivedProductOrderReturnUrl(String tradeReturnUrl) {
        int marker = tradeReturnUrl.lastIndexOf(TRADE_PATH_MARKER);
        if (marker < 0) {
            return DEFAULT_PRODUCT_ORDER_RETURN_URL;
        }
        return tradeReturnUrl.substring(0, marker) + PRODUCT_ORDER_PATH;
    }

    /** 실제 PG 를 붙일 수 있는 상태인가. */
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    /**
     * 결제창에서 돌아올 주소. 거래마다 달라야 <b>어느 거래의 승인인지</b>를 복귀 시점에 알 수 있다.
     *
     * <p>거래 id 를 쿼리스트링이 아니라 경로에 넣는다. 쿼리로 두면 사용자가 주소를 고쳐 다른
     * 거래를 승인시킬 여지가 생기는데, 어차피 서버가 당사자 검사를 하므로 막히기는 하지만
     * 그런 요청이 도달하지 않게 하는 편이 낫다.
     */
    public String returnUrlFor(Long tradeId) {
        return returnUrl.replace("{tradeId}", String.valueOf(tradeId));
    }

    /**
     * 제품 주문의 복귀 주소. 소재 거래와 <b>화면이 다르므로</b> 경로도 달라야 한다 —
     * 소비자는 거래 상세 화면을 열 수 없다.
     *
     * <p>{@link #returnUrl} 하나를 나눠 쓰지 않고 별도 설정을 두는 이유는, 두 흐름의 복귀
     * 화면이 서로 다른 라우트이기 때문이다. 한 값에 {@code {tradeId\}} 와
     * {@code {orderId\}} 를 섞어 넣으면 한쪽이 치환되지 않은 채로 결제창에 넘어간다.
     */
    public String productOrderReturnUrlFor(Long orderId) {
        return productOrderReturnUrl.replace("{orderId}", String.valueOf(orderId));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
