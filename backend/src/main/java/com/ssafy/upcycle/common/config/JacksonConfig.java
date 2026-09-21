package com.ssafy.upcycle.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 요청 본문 파싱 정책.
 *
 * <h2>정수 자리에 온 소수를 버리지 않는다</h2>
 *
 * <p>Jackson 기본값({@link DeserializationFeature#ACCEPT_FLOAT_AS_INT})은 {@code 50.5} 를
 * {@code Long} 50 으로 <b>조용히 버린다.</b> 소재 수량에서는 그 {@code 0.5} 가 500g 이었고,
 * 요청은 200 으로 성공하면서 저장된 값만 어긋난다 — 사용자도 서버도 알아챌 방법이 없다.
 *
 * <p>그동안은 {@code IntegralLongDeserializer} 를 필드마다 붙여 막았다. 그 목록이 새
 * 엔드포인트를 따라가지 못해 <b>장바구니 수량·찜 상품id·문의 주문id 가 전부 뚫려 있었다</b>
 * (2026-09-21 확인). 필드를 더 칠하는 대신 기본값 자체를 끈다.
 *
 * <p>필드 단위 애노테이션은 그대로 둔다. 중복이지만 이 설정이 되돌려져도 수량처럼 가장 아픈
 * 자리는 계속 막혀 있다.
 *
 * <h2>왜 {@code application.yml} 이 아닌가</h2>
 *
 * <p>{@code spring.jackson.deserialization.accept-float-as-int: false} 로 적으면 <b>테스트에서
 * 적용되지 않는다.</b> {@code src/test/resources/application.yml} 이 같은 이름으로 클래스패스
 * 앞에 있어 운영 설정 파일을 통째로 가리기 때문이다. 실제로 그렇게 넣었다가 새 테스트가
 * 실패해서 알았다 — 설정은 통과하는데 동작은 안 바뀌는, 가장 놓치기 쉬운 모양이었다.
 * 빈으로 두면 두 환경이 같은 코드를 지나므로 테스트가 실제 동작을 확인한다.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictIntegerDeserialization() {
        return builder -> builder.featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
