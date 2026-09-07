package com.ssafy.upcycle.material.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * 소수점이 붙은 수를 {@code Long} 으로 받지 않는다.
 *
 * <p><b>Jackson 은 기본적으로 {@code 50.5} 를 {@code Long} 50 으로 버린다</b>
 * ({@code DeserializationFeature.ACCEPT_FLOAT_AS_INT} 가 켜져 있다). 소재 수량에서는
 * 그 0.5 가 500g 이고, 요청은 200 으로 성공하면서 등록된 수량만 조용히 달라진다.
 * 사용자도 서버도 어긋난 사실을 알 수 없다.
 *
 * <p>매퍼 전역 설정을 끄지 않고 필드 단위로 막는 것은 의도적이다. 전역으로 끄면 다른 파트가
 * 만든 엔드포인트의 파싱 동작까지 같은 커밋에서 바뀐다 — 그건 이 티켓의 범위가 아니고,
 * 남의 엔드포인트가 갑자기 400 을 내기 시작하는 편이 더 위험하다.
 *
 * <p><b>같은 함정이 프로젝트의 다른 정수 필드에도 그대로 남아 있다</b> (금액, chainId 등).
 * 전역 정책은 팀 합의가 필요하므로 공지에 남긴다.
 */
public class IntegralLongDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            return (Long) context.handleWeirdNumberValue(
                    Long.class,
                    parser.getDoubleValue(),
                    "정수만 허용됩니다. 소수점이 있는 수량은 받지 않습니다.");
        }
        return parser.getLongValue();
    }
}
