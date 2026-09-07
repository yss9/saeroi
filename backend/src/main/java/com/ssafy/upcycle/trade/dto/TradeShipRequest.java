package com.ssafy.upcycle.trade.dto;

import jakarta.validation.constraints.Size;

/**
 * 발송 처리 요청.
 *
 * <p>택배사와 운송장 번호는 <b>둘 다 선택</b>이다. 직접 수령이나 화물처럼 송장이 없는
 * 전달 방식도 있어 schema.sql 의 두 컬럼 모두 NULL 을 허용한다.
 *
 * <p>다만 운송장 번호만 오고 택배사가 없으면 조회할 방법이 없으므로 그 조합은 거부한다.
 */
public record TradeShipRequest(

        @Size(max = 50, message = "택배사는 50자를 넘을 수 없습니다.")
        String carrier,

        @Size(max = 100, message = "운송장 번호는 100자를 넘을 수 없습니다.")
        String trackingNumber) {
}
