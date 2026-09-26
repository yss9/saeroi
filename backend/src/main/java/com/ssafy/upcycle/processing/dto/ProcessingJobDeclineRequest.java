package com.ssafy.upcycle.processing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 가공사가 일을 거절하는 이유. {@code PATCH /api/v1/processing-jobs/{jobId}/reject}.
 *
 * <p>사유를 필수로 받는 이유는 <b>브랜드가 다음에 무엇을 해야 하는지</b>를 알아야 하기
 * 때문이다. "설비 점검 중" 과 "이 소재는 취급하지 않는다" 는 브랜드가 취할 행동이 다르다 —
 * 앞은 기다리거나 다른 업체를 찾는 것이고, 뒤는 이 업체에 다시 맡기지 않는 것이다.
 *
 * <p>기한이 지나 자동으로 거절되는 경우에만 사유가 비어 있다. 그때는 아무도 적지 않았다는
 * 것 자체가 사실이라, 서버가 그럴듯한 문장을 지어내지 않는다.
 */
public record ProcessingJobDeclineRequest(

        @NotBlank(message = "거절 사유는 필수입니다.")
        @Size(max = 500, message = "거절 사유는 500자를 넘을 수 없습니다.")
        String reason) {
}
