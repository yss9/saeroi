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

    private static final java.time.Duration DEFAULT_TIMEOUT = java.time.Duration.ofSeconds(10);

    public KakaoPayProperties {
        baseUrl = orDefault(baseUrl, DEFAULT_BASE_URL);
        cid = orDefault(cid, TEST_CID);
        returnUrl = orDefault(returnUrl, DEFAULT_RETURN_URL);
        productOrderReturnUrl =
                orDefault(productOrderReturnUrl, DEFAULT_PRODUCT_ORDER_RETURN_URL);
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
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
