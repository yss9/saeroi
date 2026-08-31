package com.ssafy.upcycle.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseTests {

    @Test
    void createsDefaultSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("SUCCESS");
        assertThat(response.getMessage()).isEqualTo("요청에 성공했습니다.");
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void createsCustomSuccessResponse() {
        ApiResponse<Long> response = ApiResponse.success("CREATED", "생성되었습니다.", 1L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("CREATED");
        assertThat(response.getMessage()).isEqualTo("생성되었습니다.");
        assertThat(response.getData()).isEqualTo(1L);
    }

    @Test
    void createsErrorResponseWithAndWithoutDetails() {
        ApiResponse<Void> withoutDetails = ApiResponse.error(CommonErrorCode.RESOURCE_NOT_FOUND);
        ValidationError detail = new ValidationError("name", "이름은 필수입니다.");
        ApiResponse<ValidationError> withDetails =
                ApiResponse.error(CommonErrorCode.VALIDATION_ERROR, detail);

        assertThat(withoutDetails.isSuccess()).isFalse();
        assertThat(withoutDetails.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(withoutDetails.getData()).isNull();
        assertThat(withDetails.isSuccess()).isFalse();
        assertThat(withDetails.getData()).isEqualTo(detail);
    }
}
