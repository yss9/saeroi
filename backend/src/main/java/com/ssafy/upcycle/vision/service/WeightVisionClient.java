package com.ssafy.upcycle.vision.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.vision.VisionErrorCode;
import com.ssafy.upcycle.vision.config.VisionProperties;
import com.ssafy.upcycle.vision.dto.WeightRecognitionResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

/** 내부 AI 서비스에 저울 사진을 전달하는 HTTP 경계. */
@Slf4j
public class WeightVisionClient {

    private static final double SUCCESS_CONFIDENCE = 0.90;

    private static final String RECOGNITION_PATH = "/v1/vision/weight";
    private static final String FALLBACK_FILENAME = "image";

    private final RestClient restClient;

    public WeightVisionClient(VisionProperties properties, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    public WeightRecognitionResponse recognize(MultipartFile image) {
        long startedAt = System.nanoTime();
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("image", imageResource(image))
                    .contentType(contentType(image));

            WeightRecognitionResponse response = restClient.post()
                    .uri(RECOGNITION_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(WeightRecognitionResponse.class);

            if (response == null) {
                log.warn(
                        "Vision recognition failed: status=EMPTY_BODY latencyMs={}",
                        elapsedMillis(startedAt));
                throw unavailable();
            }
            if (!StringUtils.hasText(response.status())) {
                log.warn(
                        "Vision recognition failed: status=INVALID_BODY latencyMs={}",
                        elapsedMillis(startedAt));
                throw unavailable();
            }
            if (isCandidate(response.status()) && !isValidCandidate(response)) {
                log.warn(
                        "Vision recognition failed: status=INVALID_CANDIDATE_BODY latencyMs={}",
                        elapsedMillis(startedAt));
                throw unavailable();
            }

            log.info("Vision recognition completed: status={} latencyMs={}",
                    response.status(), elapsedMillis(startedAt));
            return response;
        } catch (RestClientResponseException failure) {
            log.warn("Vision recognition failed: status={} latencyMs={}",
                    failure.getStatusCode().value(), elapsedMillis(startedAt));
            throw unavailable();
        } catch (RestClientException | IOException failure) {
            log.warn(
                    "Vision recognition failed: status=UNAVAILABLE latencyMs={}",
                    elapsedMillis(startedAt));
            throw unavailable();
        }
    }

    private ByteArrayResource imageResource(MultipartFile image) throws IOException {
        String filename = sanitizedFilename(image.getOriginalFilename());
        return new ByteArrayResource(image.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private MediaType contentType(MultipartFile image) {
        String supplied = image.getContentType();
        if (!StringUtils.hasText(supplied)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(supplied);
        } catch (IllegalArgumentException invalidContentType) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String sanitizedFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return FALLBACK_FILENAME;
        }

        String normalized = originalFilename.replace('\\', '/');
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('"', '_')
                .trim();
        return StringUtils.hasText(leaf) ? leaf : FALLBACK_FILENAME;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private boolean isCandidate(String status) {
        return "SUCCESS".equals(status) || "LOW_CONFIDENCE".equals(status);
    }

    private boolean isValidCandidate(WeightRecognitionResponse response) {
        WeightRecognitionResponse.Weight weight = response.weight();
        WeightRecognitionResponse.Ocr ocr = response.ocr();
        WeightRecognitionResponse.Detection detection = response.detection();
        boolean hasValidFields = weight != null
                && weight.value() != null
                && weight.value() >= 1
                && weight.value() <= 100_000
                && "g".equals(weight.unit())
                && ocr != null
                && StringUtils.hasText(ocr.rawText())
                && isProbability(ocr.confidence())
                && detection != null
                && isProbability(detection.scaleConfidence())
                && isProbability(detection.displayConfidence())
                && isProbability(response.confidence());
        if (!hasValidFields) {
            return false;
        }

        double expectedConfidence = Math.min(
                Math.min(ocr.confidence(), detection.scaleConfidence()),
                detection.displayConfidence());
        boolean statusMatchesConfidence = response.confidence() >= SUCCESS_CONFIDENCE
                ? "SUCCESS".equals(response.status())
                : "LOW_CONFIDENCE".equals(response.status());
        return statusMatchesConfidence
                && Double.compare(response.confidence(), expectedConfidence) == 0;
    }

    private boolean isProbability(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private BusinessException unavailable() {
        return new BusinessException(VisionErrorCode.VISION_SERVICE_UNAVAILABLE);
    }
}
