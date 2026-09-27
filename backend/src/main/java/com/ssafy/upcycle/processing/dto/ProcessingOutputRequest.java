package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

public record ProcessingOutputRequest(
        /**
         * <b>무시된다.</b> 산출물의 소재 종류는 투입 소재에서 그대로 따라오므로
         * ({@code ProcessingResultService#submit}) 요청이 정할 수 있는 값이 아니다.
         *
         * <p>필드를 지우지 않고 남겨 둔 이유는 배포 순서다 — 프론트와 백엔드가 각자
         * 배포되므로, 아직 이 값을 보내는 화면이 400 을 받지 않아야 한다. 보내든
         * 말든 결과가 같다.
         */
        MaterialType materialType,
        @NotBlank @Pattern(regexp = "[ABC]") String grade,
        @NotBlank @Size(max=500) String gradeReason,
        /**
         * <b>무시된다.</b> 소재 이름도 종류처럼 투입 소재에서 그대로 따라온다 — 가공을
         * 거쳐도 같은 소재이므로 단계마다 이름이 바뀌지 않는다. 남겨 둔 이유는
         * {@link #materialType} 과 같다.
         */
        String name,
        @NotNull @Positive @JsonDeserialize(using = GramQuantityDeserializer.class) Long quantity) {}
