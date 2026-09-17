package com.ssafy.upcycle.vision.dto;

/** AI 서버의 저울 사진 인식 응답. 상태 문자열은 재해석하지 않고 그대로 전달한다. */
public record WeightRecognitionResponse(
        String status,
        Weight weight,
        Ocr ocr,
        Detection detection,
        Double confidence,
        String message) {

    public record Weight(Long value, String unit) {
    }

    public record Ocr(String rawText, Double confidence) {
    }

    public record Detection(Double scaleConfidence, Double displayConfidence) {
    }
}
