package com.ssafy.upcycle.vision.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.exception.GlobalExceptionHandler;
import com.ssafy.upcycle.common.security.config.SecurityConfig;
import com.ssafy.upcycle.common.security.handler.RestAccessDeniedHandler;
import com.ssafy.upcycle.common.security.handler.RestAuthenticationEntryPoint;
import com.ssafy.upcycle.vision.VisionErrorCode;
import com.ssafy.upcycle.vision.dto.WeightRecognitionResponse;
import com.ssafy.upcycle.vision.service.WeightVisionClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WeightVisionController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class WeightVisionControllerTests {

    private static final String PATH = "/api/materials/recognize-weight";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeightVisionClient client;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(multipart(PATH).file(image()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(client, never()).recognize(any());
    }

    @Test
    void returnsTheAiContractInTheCommonEnvelopeForAnAuthenticatedSupplier() throws Exception {
        WeightRecognitionResponse result = new WeightRecognitionResponse(
                "LOW_CONFIDENCE",
                new WeightRecognitionResponse.Weight(2350L, "g"),
                new WeightRecognitionResponse.Ocr("2.35 kg", 0.82),
                new WeightRecognitionResponse.Detection(0.91, 0.86),
                0.82,
                "결과를 확인해주세요.");
        when(client.recognize(any())).thenReturn(result);

        mockMvc.perform(multipart(PATH)
                        .file(image())
                        .with(jwt()
                                .jwt(token -> token.subject("17").claim("roles", java.util.List.of("SUPPLIER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPLIER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("LOW_CONFIDENCE"))
                .andExpect(jsonPath("$.data.weight.value").value(2350))
                .andExpect(jsonPath("$.data.weight.unit").value("g"))
                .andExpect(jsonPath("$.data.ocr.rawText").value("2.35 kg"))
                .andExpect(jsonPath("$.data.ocr.confidence").value(0.82))
                .andExpect(jsonPath("$.data.detection.scaleConfidence").value(0.91))
                .andExpect(jsonPath("$.data.detection.displayConfidence").value(0.86))
                .andExpect(jsonPath("$.data.confidence").value(0.82))
                .andExpect(jsonPath("$.data.message").value("결과를 확인해주세요."));
    }

    @Test
    void doesNotAddARoleGateBeyondTheGlobalAuthenticationRule() throws Exception {
        when(client.recognize(any())).thenReturn(new WeightRecognitionResponse(
                "OCR_FAILED", null, null, null, 0.0, null));

        mockMvc.perform(multipart(PATH)
                        .file(image())
                        .with(jwt().jwt(token -> token.subject("21"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OCR_FAILED"));
    }

    @Test
    void rejectsAnEmptyImageBeforeCallingTheAiService() throws Exception {
        MockMultipartFile empty =
                new MockMultipartFile("image", "scale.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

        mockMvc.perform(multipart(PATH)
                        .file(empty)
                        .with(jwt().jwt(token -> token.subject("17"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FILE_EMPTY"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(client, never()).recognize(any());
    }

    @Test
    void exposesClientAvailabilityFailuresAsTheCommon503Response() throws Exception {
        when(client.recognize(any()))
                .thenThrow(new BusinessException(VisionErrorCode.VISION_SERVICE_UNAVAILABLE));

        mockMvc.perform(multipart(PATH)
                        .file(image())
                        .with(jwt().jwt(token -> token.subject("17"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VISION_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private MockMultipartFile image() {
        return new MockMultipartFile(
                "image",
                "scale.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "scale-photo".getBytes(StandardCharsets.UTF_8));
    }
}
