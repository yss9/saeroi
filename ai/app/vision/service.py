"""Orchestrate image validation, OCR, and weight parsing."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

import numpy as np

from app import ocr
from app.vision.image_validation import validate_image
from app.vision.preprocessing import preprocess_variants
from app.vision.reading import (
    WeightReading,
    find_weight_readings,
    tokens_from_ocr,
)
from app.vision.schemas import (
    DetectionResult,
    OcrResult,
    ParsedWeight,
    VisionStatus,
    WeightRecognitionResponse,
    WeightValue,
)
from app.vision.weight_parser import parse_weight


SUCCESS_CONFIDENCE = 0.90


@dataclass(frozen=True)
class _Candidate:
    reading: WeightReading
    parsed: ParsedWeight | None


class WeightRecognitionService:
    """Run the recognition pipeline without coupling tests to the OCR model.

    사진을 자르지 않는다. 산업용 저울은 표시부가 본체와 떨어져 있어, 본체를 잘라내면
    숫자가 함께 잘려 나간다 — 어느 부분을 읽을지는 잘라내기가 아니라 `reading` 의
    선택 규칙이 정한다.
    """

    def __init__(
        self,
        *,
        image_validator: Callable[[bytes], np.ndarray] = validate_image,
        preprocessor: Callable[[np.ndarray], list[tuple[str, np.ndarray]]] = (
            preprocess_variants
        ),
        ocr_engine: Callable[[np.ndarray], Any] | None = None,
        weight_parser: Callable[[str], ParsedWeight | None] = parse_weight,
    ) -> None:
        self.image_validator = image_validator
        self.preprocessor = preprocessor
        self.ocr_engine = ocr_engine
        self.weight_parser = weight_parser

    def recognize(self, data: bytes) -> WeightRecognitionResponse:
        try:
            image = self.image_validator(data)
        except ValueError:
            return self._failure(
                VisionStatus.INVALID_IMAGE,
                "이미지를 확인할 수 없습니다. 선명한 JPG 또는 PNG 사진을 사용해주세요.",
            )

        candidates = self._read_candidates(image)
        if not candidates:
            return self._failure(
                VisionStatus.OCR_FAILED,
                "사진에서 글자를 읽지 못했습니다. 표시창이 선명하게 보이도록 촬영해주세요.",
            )

        valid = [candidate for candidate in candidates if candidate.parsed]
        if not valid:
            best = candidates[0]
            return WeightRecognitionResponse(
                status=VisionStatus.INVALID_WEIGHT_FORMAT,
                ocr=OcrResult(
                    raw_text=best.reading.text,
                    # detection 과 같은 자리로 반올림한다 — backend 가 confidence 를
                    # 이 값들의 최솟값과 **정확히** 비교하므로 자리가 어긋나면 503 이다.
                    confidence=round(best.reading.number_confidence, 4),
                ),
                detection=self._detection(best.reading),
                confidence=self._confidence(best.reading),
                message="인식한 값에서 지원되는 g 또는 kg 무게를 찾지 못했습니다.",
            )

        # find_weight_readings 가 이미 글자 큰 순으로 준다 — 저울은 재고 있는 값을
        # 가장 크게 보여준다.
        best = valid[0]
        assert best.parsed is not None
        confidence = self._confidence(best.reading)
        status = (
            VisionStatus.SUCCESS
            if confidence >= SUCCESS_CONFIDENCE
            else VisionStatus.LOW_CONFIDENCE
        )
        message = None
        if status == VisionStatus.LOW_CONFIDENCE:
            message = "인식 신뢰도가 낮습니다. 사진과 무게를 확인해주세요."

        return WeightRecognitionResponse(
            status=status,
            weight=WeightValue(value=best.parsed.gram),
            ocr=OcrResult(
                raw_text=best.parsed.raw_text,
                confidence=round(best.reading.number_confidence, 4),
            ),
            detection=self._detection(best.reading),
            confidence=confidence,
            message=message,
        )

    def _read_candidates(self, image: np.ndarray) -> list[_Candidate]:
        """전처리 변형마다 OCR 을 돌려 후보를 모은다.

        변형이 필요한 이유는 7세그먼트 LED 때문이다. 원본에서 `34.8` 이 `866` 으로
        읽히는 사진이 있었고, 같은 사진이 `gray`·`clahe` 에서는 정확히 읽혔다.
        """
        engine = self.ocr_engine or ocr.get_engine()
        candidates: list[_Candidate] = []
        for _name, variant in self.preprocessor(image):
            result, _elapsed = engine(np.asarray(variant))
            if not result:
                continue
            readings = find_weight_readings(tokens_from_ocr(result))
            for reading in readings:
                candidates.append(
                    _Candidate(reading=reading, parsed=self.weight_parser(reading.text))
                )

        # 변형마다 같은 값을 다시 읽으므로 후보가 겹친다. 글자가 크고 확신이 높은
        # 것부터 본다.
        candidates.sort(
            key=lambda candidate: (
                candidate.reading.height,
                candidate.reading.number_confidence,
            ),
            reverse=True,
        )
        return candidates

    @staticmethod
    def _detection(reading: WeightReading) -> DetectionResult:
        """백엔드가 요구하는 두 확률 값.

        이름은 저울·표시창을 따로 탐지하던 시절에서 왔다. 그 단계는 없어졌지만
        backend 의 응답 검증이 이 두 필드를 필수로 보므로, 새 파이프라인에서 뜻이
        통하는 값으로 채운다 — 숫자를 읽은 확신과 단위를 읽은 확신이다.
        """
        return DetectionResult(
            scale_confidence=round(reading.number_confidence, 4),
            display_confidence=round(reading.unit_confidence, 4),
        )

    @staticmethod
    def _confidence(reading: WeightReading) -> float:
        """backend 는 confidence 가 세 값의 최솟값과 **정확히** 같기를 요구한다.

        ocr.confidence 와 scale_confidence 가 모두 숫자 토큰의 확신이므로 결과는
        숫자와 단위 중 낮은 쪽이 된다. 반올림 자리를 detection 과 맞추지 않으면
        검증이 어긋나 503 으로 떨어진다.
        """
        return min(
            round(reading.number_confidence, 4),
            round(reading.unit_confidence, 4),
        )

    @staticmethod
    def _failure(status: VisionStatus, message: str) -> WeightRecognitionResponse:
        return WeightRecognitionResponse(status=status, message=message)
