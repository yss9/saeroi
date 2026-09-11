package com.ssafy.upcycle.product.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * QR 주소가 없으면 <b>기동하지 않는다</b>는 결정.
 *
 * <p>다른 설정들은 빠져도 넘어간다 — 블록체인 주소가 없으면 기록만 501 로 거절되고, 결제
 * 키가 없으면 Mock 게이트웨이가 붙는다. 되돌릴 수 있기 때문이다.
 *
 * <p>QR 은 다르다. 잘못된 주소로 만든 QR 은 <b>인쇄되어 제품에 붙어 나간다.</b> 그때는 물건을
 * 회수하지 않는 한 고칠 방법이 없다. 그래서 뜨지 않는 편이 낫다는 것이 이 티켓의 결정이고,
 * 이 테스트가 그것을 고정한다.
 *
 * <p>스프링 컨텍스트를 띄우지 않고 {@code @PostConstruct} 메서드를 직접 부른다 — 확인하려는
 * 것은 그 판정이지 컨텍스트 로딩 방식이 아니고, 실패하는 컨텍스트를 띄우는 테스트는 캐시에
 * 남아 다른 테스트를 흔든다.
 */
class ProductQrConfigTests {

    @Test
    void refusesToStartWithoutABaseUrl() {
        assertThatThrownBy(() -> configWith(null).requireBaseUrl())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.qr.base-url");
        assertThatThrownBy(() -> configWith("   ").requireBaseUrl())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startsWithABaseUrl() {
        configWith("https://upcycle.example.com/passport").requireBaseUrl();
    }

    @Test
    void normalizesTheTrailingSlash() {
        // 주소를 조립할 때 // 가 생기면 라우트가 안 맞는 곳도 있다.
        assertThat(new ProductQrProperties("https://a/passport/").normalizedBaseUrl())
                .isEqualTo("https://a/passport");
        assertThat(new ProductQrProperties("https://a/passport").normalizedBaseUrl())
                .isEqualTo("https://a/passport");
    }

    private ProductQrConfig configWith(String baseUrl) {
        return new ProductQrConfig(new ProductQrProperties(baseUrl));
    }
}
