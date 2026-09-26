package com.ssafy.upcycle.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.response.ValidationError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTests {

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void handlesBusinessException() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void handlesBeanValidationErrorsWithoutRejectedValue() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("name"))
                .andExpect(jsonPath("$.data[0].message").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.data[0].rejectedValue").doesNotExist());
    }

    @Test
    void keepsLexicographicallySmallestRequestBodyValidationMessage() throws Exception {
        String body = mockMvc.perform(post("/test/validation-duplicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"s3cr3t-input\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].field").value("name"))
                .andExpect(jsonPath("$.data[0].message").value("A 3자 이하여야 합니다."))
                .andExpect(jsonPath("$.data[0].rejectedValue").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("s3cr3t-input");
    }

    @Test
    void handlesConstraintViolations() {
        ConstraintTarget target = new ConstraintTarget("");
        Set<ConstraintViolation<ConstraintTarget>> violations = validator.validate(target);
        ConstraintViolationException exception = new ConstraintViolationException(violations);

        ResponseEntity<ApiResponse<List<ValidationError>>> response =
                new GlobalExceptionHandler().handleConstraintViolationException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getData())
                .containsExactly(new ValidationError("value", "값은 필수입니다."));
    }

    @ParameterizedTest
    @MethodSource("duplicateConstraintViolationOrders")
    void keepsLexicographicallySmallestConstraintMessageRegardlessOfEncounterOrder(
            List<String> messages) {
        Set<ConstraintViolation<DuplicateConstraintTarget>> available =
                validator.validate(new DuplicateConstraintTarget("s3cr3t-input"));
        Set<ConstraintViolation<?>> ordered = new LinkedHashSet<>();
        messages.stream()
                .map(message -> available.stream()
                        .filter(violation -> violation.getMessage().equals(message))
                        .findFirst()
                        .orElseThrow())
                .forEach(ordered::add);
        ConstraintViolationException exception =
                new EncounterOrderedConstraintViolationException(ordered);

        ResponseEntity<ApiResponse<List<ValidationError>>> response =
                new GlobalExceptionHandler().handleConstraintViolationException(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData())
                .containsExactly(new ValidationError("value", "A 메시지"));
    }

    @Test
    void handlesNativeMvcMethodValidationWithSafeDeterministicDetails() throws Exception {
        String body = mockMvc.perform(get("/test/method-validation")
                        .param("name", "s3cr3t-input")
                        .param("zeta", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].field").value("name"))
                .andExpect(jsonPath("$.data[0].message").value("A 3자 이하여야 합니다."))
                .andExpect(jsonPath("$.data[1].field").value("zeta"))
                .andExpect(jsonPath("$.data[1].message").value("Z 값은 필수입니다."))
                .andExpect(jsonPath("$.data[0].rejectedValue").doesNotExist())
                .andExpect(jsonPath("$.data[1].rejectedValue").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("s3cr3t-input");
    }

    @Test
    void handlesMalformedJson() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void masksUnexpectedExceptionDetails() throws Exception {
        String body = mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("internal detail", "IllegalStateException");
    }

    @Test
    void handlesPathVariableTypeMismatchAsBadRequestNotServerError() throws Exception {
        // /brands/{id} 처럼 Long 경로변수에 숫자로 못 바꾸는 값이 오면 500 이 아니라 400 이어야
        // 한다. 이 방어가 없으면 permitAll 경로에서 인증 없이 ERROR 로그를 무한히 채울 수 있다.
        mockMvc.perform(get("/test/type-mismatch/1e10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void handlesPathVariableOverflowAsBadRequest() throws Exception {
        mockMvc.perform(get("/test/type-mismatch/99999999999999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void handlesUnsupportedMediaTypeAsFourFifteenNotServerError() throws Exception {
        // JSON 을 기대하는 엔드포인트에 text/plain 이 오면 500 이 아니라 415 여야 한다.
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void handlesUnsupportedMethod() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET"));

        ResponseEntity<ApiResponse<Void>> response =
                new GlobalExceptionHandler().handleMethodNotSupportedException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void handlesMissingResource() {
        NoResourceFoundException exception =
                new NoResourceFoundException(HttpMethod.GET, "/missing");

        ResponseEntity<ApiResponse<Void>> response =
                new GlobalExceptionHandler().handleNoResourceFoundException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business")
        ApiResponse<Void> business() {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/type-mismatch/{id}")
        ApiResponse<Void> typeMismatch(@org.springframework.web.bind.annotation.PathVariable Long id) {
            return ApiResponse.success(null);
        }

        @GetMapping("/unexpected")
        ApiResponse<Void> unexpected() {
            throw new IllegalStateException("internal detail");
        }

        @PostMapping("/validation")
        ApiResponse<Void> validation(@Valid @RequestBody TestRequest request) {
            return ApiResponse.success(null);
        }

        @PostMapping("/validation-duplicates")
        ApiResponse<Void> validationDuplicates(
                @Valid @RequestBody DuplicateMessageRequest request) {
            return ApiResponse.success(null);
        }

        @GetMapping("/method-validation")
        ApiResponse<Void> methodValidation(
                @RequestParam(name = "name")
                @Size(max = 3, message = "A 3자 이하여야 합니다.")
                @Pattern(regexp = "[A-Z]+", message = "Z 대문자만 허용됩니다.")
                String name,
                @RequestParam(name = "zeta")
                @NotBlank(message = "Z 값은 필수입니다.")
                String zeta) {
            return ApiResponse.success(null);
        }
    }

    record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {
    }

    record DuplicateMessageRequest(
            @Size(max = 3, message = "A 3자 이하여야 합니다.")
            @Pattern(regexp = "[A-Z]+", message = "Z 대문자만 허용됩니다.")
            String name) {
    }

    record ConstraintTarget(@NotBlank(message = "값은 필수입니다.") String value) {
    }

    record DuplicateConstraintTarget(
            @Size(max = 3, message = "A 메시지")
            @Pattern(regexp = "[A-Z]+", message = "Z 메시지")
            String value) {
    }

    private static Stream<Arguments> duplicateConstraintViolationOrders() {
        return Stream.of(
                Arguments.of(List.of("Z 메시지", "A 메시지")),
                Arguments.of(List.of("A 메시지", "Z 메시지")));
    }

    private static final class EncounterOrderedConstraintViolationException
            extends ConstraintViolationException {

        private final Set<ConstraintViolation<?>> orderedViolations;

        private EncounterOrderedConstraintViolationException(
                Set<ConstraintViolation<?>> orderedViolations) {
            super(orderedViolations);
            this.orderedViolations = orderedViolations;
        }

        @Override
        public Set<ConstraintViolation<?>> getConstraintViolations() {
            return orderedViolations;
        }
    }
}
