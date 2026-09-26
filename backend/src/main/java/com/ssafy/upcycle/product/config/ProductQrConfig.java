package com.ssafy.upcycle.product.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 이 프로젝트는 {@code @ConfigurationPropertiesScan} 을 쓰지 않는다 — 어떤 설정이 살아 있는지
 * 한 곳에서 보이게 하려고 도메인마다 명시적으로 등록한다 ({@code BlockchainConfig} 와 같다).
 */
@Configuration
@EnableConfigurationProperties(ProductQrProperties.class)
@RequiredArgsConstructor
@Slf4j
public class ProductQrConfig {

    private final ProductQrProperties properties;

    /**
     * 주소가 비어 있으면 <b>기동을 실패시킨다.</b>
     *
     * <p>다른 설정들은 빠져도 501 이나 Mock 으로 넘어간다 — 블록체인 주소가 없으면 기록만
     * 거절되고, 결제 키가 없으면 Mock 게이트웨이가 붙는다. 되돌릴 수 있기 때문이다.
     *
     * <p>QR 은 다르다. 잘못된 주소로 만든 QR 은 <b>인쇄되어 제품에 붙어 나간다.</b> 그때는
     * 물건을 회수하지 않는 한 고칠 방법이 없다. 기동하지 않는 편이 낫다.
     */
    @PostConstruct
    void requireBaseUrl() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "app.qr.base-url is required. QR codes are printed onto physical products, "
                            + "so a wrong or empty address cannot be corrected afterwards. "
                            + "Set QR_BASE_URL or app.qr.base-url in application-local.yml.");
        }
        log.info("Product QR base url: {}", properties.normalizedBaseUrl());
    }
}
