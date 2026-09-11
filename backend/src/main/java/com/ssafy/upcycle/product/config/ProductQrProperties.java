package com.ssafy.upcycle.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QR 이 담을 Passport 주소.
 *
 * <p>QR 에 토큰만 넣으면 스캔했을 때 <b>문자열이 뜨고 끝</b>이다. 손님은 휴대폰 기본 카메라로
 * 찍으므로, 브라우저가 바로 열리는 주소여야 한다.
 *
 * <pre>
 *   base-url = https://upcycle.example.com/passport
 *   →  https://upcycle.example.com/passport/a3f97b2c...
 * </pre>
 *
 * <p><b>프론트의 Passport 라우트와 반드시 같아야 한다.</b> 어긋나면 이미 인쇄되어 제품에
 * 붙어 나간 QR 이 전부 죽고, 그건 되돌릴 수 없다.
 *
 * @param baseUrl 끝의 {@code /} 는 있어도 없어도 된다 — 조립할 때 정리한다
 */
@ConfigurationProperties(prefix = "app.qr")
public record ProductQrProperties(String baseUrl) {

    /** 설정됐는가. 비어 있으면 {@code ProductQrConfig} 가 기동을 실패시킨다. */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    /** 끝의 {@code /} 를 떼어 낸 주소. 토큰을 붙일 때 {@code //} 가 생기지 않게 한다. */
    public String normalizedBaseUrl() {
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
