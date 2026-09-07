package com.ssafy.upcycle.trade.payment;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 어떤 결제 게이트웨이를 붙일지 정한다.
 *
 * <p><b>키가 있으면 실제 카카오페이, 없으면 Mock 이다.</b> 프로파일로 가르지 않은 것은
 * 의도적이다 — 프로파일을 쓰면 "운영인데 키를 안 넣은" 경우를 잡지 못하고 조용히 실결제
 * 없이 돌아간다. 키의 유무가 곧 판단 기준이면 그런 상태가 생기지 않는다.
 *
 * <p>어느 쪽이 붙었는지 <b>시작 로그에 반드시 남긴다.</b> 시연 중에 "결제가 왜 그냥
 * 되지" 를 로그 한 줄로 답할 수 있어야 한다.
 */
@Configuration
@EnableConfigurationProperties(KakaoPayProperties.class)
public class PaymentGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayConfig.class);

    @Bean
    public PaymentGateway paymentGateway(
            KakaoPayProperties properties, RestClient.Builder builder, Clock clock) {
        if (!properties.isConfigured()) {
            log.warn("KakaoPay secret key is not set — using MockPaymentGateway. "
                    + "No real payment will be charged. Set APP_PAYMENT_KAKAOPAY_SECRET_KEY "
                    + "to enable the live gateway.");
            return new MockPaymentGateway(clock);
        }
        log.info("KakaoPay gateway enabled (cid={}, baseUrl={})",
                properties.cid(), properties.baseUrl());
        return new KakaoPayGateway(properties, builder);
    }
}
