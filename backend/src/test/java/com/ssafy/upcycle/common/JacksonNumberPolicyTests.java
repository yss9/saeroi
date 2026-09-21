package com.ssafy.upcycle.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 정수 필드에 소수가 오면 <b>조용히 버리지 않고 거절한다.</b>
 *
 * <p>Jackson 기본값({@code ACCEPT_FLOAT_AS_INT})은 {@code 50.5} 를 {@code 50} 으로 만든다.
 * 요청은 200 으로 성공하고 저장된 값만 어긋나서, 사용자도 서버도 알아챌 방법이 없다.
 *
 * <p>그동안은 {@code IntegralLongDeserializer} 를 필드마다 붙여 막았는데 그 목록이 새
 * 엔드포인트를 따라가지 못했다 — 장바구니 수량·찜 상품id·문의 주문id 가 전부 뚫려 있었다
 * (2026-09-21 확인). 그래서 매퍼 기본값 자체를 껐고, 이 테스트가 그것을 고정한다.
 *
 * <p><b>애플리케이션 컨텍스트의 매퍼를 그대로 쓴다.</b> 새 {@code ObjectMapper} 를 만들어
 * 검사하면 설정이 실제로 적용됐는지가 아니라 테스트가 만든 매퍼를 검사하게 된다.
 */
@SpringBootTest
class JacksonNumberPolicyTests {

    private record LongHolder(Long value) {}

    private record IntHolder(Integer value) {}

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rejectsAFractionalValueForALongField() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":50.5}", LongHolder.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void rejectsAFractionalValueForAnIntegerField() {
        // 장바구니 수량이 Integer 였다 — Long 전용 디시리얼라이저로는 막을 수 없던 자리다.
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\":2.7}", IntHolder.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void stillAcceptsPlainIntegers() throws Exception {
        assertThat(objectMapper.readValue("{\"value\":50}", LongHolder.class).value())
                .isEqualTo(50L);
    }
}
