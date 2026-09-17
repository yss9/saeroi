package com.ssafy.upcycle.vision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.vision.VisionErrorCode;
import com.ssafy.upcycle.vision.config.VisionProperties;
import com.ssafy.upcycle.vision.dto.WeightRecognitionResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WeightVisionClientTests {

    private static final String BASE_URL = "https://vision.test";
    private static final byte[] IMAGE = "scale-photo-bytes".getBytes(StandardCharsets.UTF_8);

    private MockRestServiceServer server;
    private WeightVisionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WeightVisionClient(
                new VisionProperties(BASE_URL, Duration.ofSeconds(2), Duration.ofSeconds(30)),
                builder);
    }

    @Test
    void sendsTheImagePartAndPreservesTheAiResponseContract() {
        server.expect(once(), requestTo(BASE_URL + "/v1/vision/weight"))
                .andExpect(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.POST))
                .andExpect(request -> assertThat(request.getHeaders().getContentType())
                        .isNotNull()
                        .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                                .isTrue()))
                .andExpect(content().string(containsString("name=\"image\"")))
                .andExpect(content().string(containsString("filename=\"scale.jpg\"")))
                .andExpect(content().string(containsString("Content-Type: image/jpeg")))
                .andExpect(content().string(containsString("scale-photo-bytes")))
                .andRespond(withSuccess("""
                        {
                          "status": "SUCCESS",
                          "weight": {"value": 2350, "unit": "g"},
                          "ocr": {"rawText": "2.35 kg", "confidence": 0.97},
                          "detection": {
                            "scaleConfidence": 0.95,
                            "displayConfidence": 0.96
                          },
                          "confidence": 0.95,
                          "message": null
                        }
                        """, MediaType.APPLICATION_JSON));

        WeightRecognitionResponse response = client.recognize(image("scale.jpg"));

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.weight().value()).isEqualTo(2350L);
        assertThat(response.weight().unit()).isEqualTo("g");
        assertThat(response.ocr().rawText()).isEqualTo("2.35 kg");
        assertThat(response.ocr().confidence()).isEqualTo(0.97);
        assertThat(response.detection().scaleConfidence()).isEqualTo(0.95);
        assertThat(response.detection().displayConfidence()).isEqualTo(0.96);
        assertThat(response.confidence()).isEqualTo(0.95);
        assertThat(response.message()).isNull();
        server.verify();
    }

    @Test
    void keepsUnknownAiStatusesAndNullableFailureDetailsUnchanged() {
        server.expect(requestTo(BASE_URL + "/v1/vision/weight"))
                .andRespond(withSuccess("""
                        {
                          "status": "FUTURE_AI_STATUS",
                          "weight": null,
                          "ocr": null,
                          "detection": null,
                          "confidence": 0.0,
                          "message": "다시 촬영해주세요."
                        }
                        """, MediaType.APPLICATION_JSON));

        WeightRecognitionResponse response = client.recognize(image("scale.png"));

        assertThat(response.status()).isEqualTo("FUTURE_AI_STATUS");
        assertThat(response.weight()).isNull();
        assertThat(response.ocr()).isNull();
        assertThat(response.detection()).isNull();
        assertThat(response.message()).isEqualTo("다시 촬영해주세요.");
        server.verify();
    }

    @Test
    void stripsClientPathsFromTheForwardedFilename() {
        server.expect(requestTo(BASE_URL + "/v1/vision/weight"))
                .andExpect(content().string(containsString("filename=\"scale.jpg\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret-folder"))))
                .andRespond(withSuccess("""
                        {"status":"OCR_FAILED","confidence":0.0,"message":"OCR failed"}
                        """, MediaType.APPLICATION_JSON));

        client.recognize(image("C:\\secret-folder\\scale.jpg"));

        server.verify();
    }

    @Test
    void translatesDownstreamHttpFailuresToServiceUnavailable() {
        server.expect(requestTo(BASE_URL + "/v1/vision/weight"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertUnavailable(() -> client.recognize(image("scale.jpg")));
        server.verify();
    }

    @Test
    void translatesAnEmptyResponseBodyToServiceUnavailable() {
        server.expect(requestTo(BASE_URL + "/v1/vision/weight"))
                .andRespond(withSuccess().header(HttpHeaders.CONTENT_LENGTH, "0"));

        assertUnavailable(() -> client.recognize(image("scale.jpg")));
        server.verify();
    }

    @Test
    void translatesMalformedJsonToServiceUnavailable() {
        server.expect(requestTo(BASE_URL + "/v1/vision/weight"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertUnavailable(() -> client.recognize(image("scale.jpg")));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"status\":null}",
        "{\"status\":\"\"}",
        "{\"status\":\"   \"}"
    })
    void translatesMissingNullOrBlankStatusToServiceUnavailable(String responseBody) {
        server.expect(requestTo(BASE_URL + "/v1/vision/weight"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertUnavailable(() -> client.recognize(image("scale.jpg")));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"status\":\"SUCCESS\",\"weight\":null,\"ocr\":{\"rawText\":\"2 kg\",\"confidence\":0.9},\"detection\":{\"scaleConfidence\":0.9,\"displayConfidence\":0.9},\"confidence\":0.9}",
        "{\"status\":\"SUCCESS\",\"weight\":{\"value\":0,\"unit\":\"g\"},\"ocr\":{\"rawText\":\"0 g\",\"confidence\":0.9},\"detection\":{\"scaleConfidence\":0.9,\"displayConfidence\":0.9},\"confidence\":0.9}",
        "{\"status\":\"SUCCESS\",\"weight\":{\"value\":100001,\"unit\":\"g\"},\"ocr\":{\"rawText\":\"100001 g\",\"confidence\":0.9},\"detection\":{\"scaleConfidence\":0.9,\"displayConfidence\":0.9},\"confidence\":0.9}",
        "{\"status\":\"SUCCESS\",\"weight\":{\"value\":2000,\"unit\":\"kg\"},\"ocr\":{\"rawText\":\"2 kg\",\"confidence\":0.9},\"detection\":{\"scaleConfidence\":0.9,\"displayConfidence\":0.9},\"confidence\":0.9}",
        "{\"status\":\"SUCCESS\",\"weight\":{\"value\":2000,\"unit\":\"g\"},\"ocr\":null,\"detection\":{\"scaleConfidence\":0.9,\"displayConfidence\":0.9},\"confidence\":0.9}",
        "{\"status\":\"SUCCESS\",\"weight\":{\"value\":2000,\"unit\":\"g\"},\"ocr\":{\"rawText\":\"\",\"confidence\":0.9},\"detection\":{\"scaleConfidence\":0.9,\"displayConfidence\":0.9},\"confidence\":0.9}",
        "{\"status\":\"SUCCESS\",\"weight\":{\"value\":2000,\"unit\":\"g\"},\"ocr\":{\"rawText\":\"2 kg\",\"confidence\":\"NaN\"},\"detection\":{\"scaleConfidence\":0.9,\"displayConfidence\":0.9},\"confidence\":0.9}",
        "{\"status\":\"LOW_CONFIDENCE\",\"weight\":{\"value\":2000,\"unit\":\"g\"},\"ocr\":{\"rawText\":\"2 kg\",\"confidence\":0.9},\"detection\":null,\"confidence\":0.6}",
        "{\"status\":\"LOW_CONFIDENCE\",\"weight\":{\"value\":2000,\"unit\":\"g\"},\"ocr\":{\"rawText\":\"2 kg\",\"confidence\":0.9},\"detection\":{\"scaleConfidence\":0.9,\"displayConfidence\":0.9},\"confidence\":\"Infinity\"}",
        "{\"status\":\"SUCCESS\",\"weight\":{\"value\":2000,\"unit\":\"g\"},\"ocr\":{\"rawText\":\"2 kg\",\"confidence\":0.95},\"detection\":{\"scaleConfidence\":0.95,\"displayConfidence\":0.95},\"confidence\":0.89}",
        "{\"status\":\"LOW_CONFIDENCE\",\"weight\":{\"value\":2000,\"unit\":\"g\"},\"ocr\":{\"rawText\":\"2 kg\",\"confidence\":0.95},\"detection\":{\"scaleConfidence\":0.95,\"displayConfidence\":0.95},\"confidence\":0.95}",
        "{\"status\":\"SUCCESS\",\"weight\":{\"value\":2000,\"unit\":\"g\"},\"ocr\":{\"rawText\":\"2 kg\",\"confidence\":0.96},\"detection\":{\"scaleConfidence\":0.95,\"displayConfidence\":0.94},\"confidence\":0.95}"
    })
    void translatesMalformedCandidateResponsesToServiceUnavailable(String responseBody) {
        server.expect(requestTo(BASE_URL + "/v1/vision/weight"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertUnavailable(() -> client.recognize(image("scale.jpg")));
        server.verify();
    }

    private MockMultipartFile image(String filename) {
        return new MockMultipartFile("image", filename, MediaType.IMAGE_JPEG_VALUE, IMAGE);
    }

    private void assertUnavailable(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(VisionErrorCode.VISION_SERVICE_UNAVAILABLE));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
