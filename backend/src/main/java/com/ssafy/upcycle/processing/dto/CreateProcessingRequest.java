package com.ssafy.upcycle.processing.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.processing.entity.ProcessType;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 가공 요청. {@code POST /api/v1/processing-requests}.
 *
 * <p><b>금액이 없다.</b> 견적이 사라지면서 화면이 금액을 보내지 않는다 — 보내면 서버는
 * 그 값을 믿을지 말지를 정해야 하고, 믿지 않는다면 애초에 받을 이유가 없다. 서버가
 * 가공사가 등록해 둔 단가로 계산해 확정한다 ({@code ProcessingEstimate}).
 *
 * <p>결제 수단도 받지 않는다. 지금 붙어 있는 결제 게이트웨이가 무엇인지는 서버만 알고,
 * 화면이 고를 수 있는 것이 아니다 — 모의 게이트웨이가 붙은 환경에서 화면이 카카오페이를
 * 보내면 환불 경로가 있지도 않은 결제를 취소하려 든다.
 */
public record CreateProcessingRequest(

        @NotNull @Positive Long materialId,

        @NotNull @Positive Long processorId,

        @NotNull @Positive @JsonDeserialize(using = GramQuantityDeserializer.class) Long quantity,

        @NotEmpty @Size(max = 8) List<@NotNull ProcessType> processTypes) {
}
