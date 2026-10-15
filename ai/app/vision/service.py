"""Orchestrate image validation, OCR, and weight parsing."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, replace
from typing import Any

import numpy as np

from app import ocr
from app.vision.image_validation import validate_image
from app.vision.preprocessing import preprocess_variants
from app.vision.reading import (
    TextToken,
    WeightReading,
    find_weight_readings,
    tokens_from_ocr,
    unit_from_zoom,
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

# 확대 재판독은 비싸다(전처리 다섯 번 × OCR). 화면의 본값 후보 — 가장 큰 숫자와 비슷한 크기 —
# 에만 한다. 작은 숫자(버튼 라벨, 용량 표기)까지 확대하면 느려지고 엉뚱한 글자를 단위로 붙인다.
ZOOM_MIN_HEIGHT_RATIO = 0.5
ZOOM_SCALE = 3

# 여러 전처리의 합의는 "가장 큰 글자" 무리 안에서만 본다. 작은 글씨가 여러 번 읽힌다고
# 본값을 이기면 안 된다.
AGREEMENT_HEIGHT_RATIO = 0.85

# 소수점이 붙은 g 값의 상한 확신. SUCCESS 문턱(0.90) 바로 아래라 LOW_CONFIDENCE 가 되어 사용자가
# 확인한다. backend 가 confidence 를 숫자·단위 확신의 최솟값과 정확히 대조하므로, 상태만 바꾸지
# 않고 **단위 확신을 낮춘다** — "이 g 는 kg 일 수 있다" 는 뜻과도 맞다.
DECIMAL_GRAM_UNIT_CONFIDENCE = 0.89

UnitReader = Callable[[np.ndarray, TextToken], "tuple[str, float] | None"]


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
        unit_reader: UnitReader | None = None,
    ) -> None:
        self.image_validator = image_validator
        self.preprocessor = preprocessor
        self.ocr_engine = ocr_engine
        self.weight_parser = weight_parser
        self.unit_reader = unit_reader or self._read_unit_by_zooming

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

        best = self._agreed(valid)
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

        **단위 확대 재판독은 두 번째 차례다.** 먼저 전체 사진의 OCR 만으로 고르고, 단위가 붙은
        값을 하나도 못 찾았을 때만 이미 읽은 토큰의 오른쪽을 확대해 다시 본다. 전체 사진을
        다시 읽지 않으므로, 잘 읽히던 사진은 원래 속도 그대로다.
        """
        engine = self.ocr_engine or ocr.get_engine()
        token_sets: list[list[TextToken]] = []
        for _name, variant in self.preprocessor(image):
            result, _elapsed = engine(np.asarray(variant))
            if result:
                token_sets.append(tokens_from_ocr(result))

        candidates = self._candidates_from(token_sets, None)
        valid = [candidate for candidate in candidates if candidate.parsed]
        # 확대는 두 경우에만 한다 — 단위 붙은 값을 못 찾았거나, 찾았어도 확신이 낮을 때.
        if valid and self._confidence(self._agreed(valid).reading) >= SUCCESS_CONFIDENCE:
            return candidates

        zoomed: dict[tuple[int, int, int, int], tuple[str, float] | None] = {}

        def zoom_unit(number: TextToken, tallest: float) -> tuple[str, float] | None:
            if number.height < ZOOM_MIN_HEIGHT_RATIO * tallest:
                return None
            # 변형마다 같은 숫자를 다시 읽는다 — 같은 자리는 한 번만 확대한다.
            key = (
                round(number.left / 8), round(number.top / 8),
                round(number.right / 8), round(number.bottom / 8),
            )
            if key not in zoomed:
                zoomed[key] = self.unit_reader(image, number)
            return zoomed[key]

        return self._candidates_from(token_sets, zoom_unit)

    def _candidates_from(
        self,
        token_sets: list[list[TextToken]],
        zoom_unit: Callable[[TextToken, float], "tuple[str, float] | None"] | None,
    ) -> list[_Candidate]:
        candidates: list[_Candidate] = []
        for tokens in token_sets:
            tallest = max(
                (token.height for token in tokens if any(c.isdigit() for c in token.text)),
                default=0.0,
            )
            reader = None if zoom_unit is None else (lambda number, t=tallest: zoom_unit(number, t))
            for reading in find_weight_readings(tokens, reader):
                parsed = self.weight_parser(reading.text)
                decimals = _gram_decimals(parsed)
                if decimals >= 2:
                    # `20.10g`·`3.720g`: 0.01 g 를 재는 저울은 대상이 아니다. 가짜 소수점이거나
                    # kg 를 g 로 읽은 것이라, 틀린 값을 성공으로 내느니 후보에서 뺀다.
                    parsed = None
                elif decimals == 1:
                    # `18.7g`·`349.1g`: 있을 수는 있지만 kg 를 g 로, 3491 을 349.1 로 읽은
                    # 경우와 구분이 안 된다. 값은 주되 사용자가 확인하게 한다.
                    reading = replace(
                        reading,
                        unit_confidence=min(reading.unit_confidence, DECIMAL_GRAM_UNIT_CONFIDENCE),
                    )
                candidates.append(_Candidate(reading=reading, parsed=parsed))

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

    @staticmethod
    def _agreed(valid: list[_Candidate]) -> _Candidate:
        """여러 전처리가 같게 읽은 값을 고른다 — 가장 큰 글자 무리 안에서만.

        7세그먼트는 전처리에 따라 다르게 읽힌다. 한 변형이 2010 을 20.10 으로 읽은 사진이
        있었다(가짜 소수점). 다수가 같게 읽은 값이 맞을 가능성이 높다. 다만 작은 글씨가
        여러 번 읽힌다고 본값을 이기면 안 되므로, 가장 큰 글자와 비슷한 크기의 후보만 센다.
        표가 같으면 글자가 크고 확신이 높은 쪽이다(valid 는 이미 그 순서다).
        """
        tallest = valid[0].reading.height
        contenders = [c for c in valid if c.reading.height >= AGREEMENT_HEIGHT_RATIO * tallest]
        # 표를 센다. 딕셔너리는 넣은 순서를 지키므로, 동점이면 먼저 나온(글자가 크고 확신이
        # 높은) 값이 이긴다 — max 는 같은 값이면 앞의 것을 돌려준다.
        votes: dict[int, int] = {}
        for candidate in contenders:
            assert candidate.parsed is not None
            votes[candidate.parsed.gram] = votes.get(candidate.parsed.gram, 0) + 1
        winner = max(votes, key=votes.__getitem__)
        # 같은 값을 읽은 후보 중 가장 확신 있게 읽은 것을 보고한다.
        agreeing = [c for c in contenders if c.parsed is not None and c.parsed.gram == winner]
        return max(agreeing, key=lambda c: WeightRecognitionService._confidence(c.reading))

    def _read_unit_by_zooming(self, image: np.ndarray, number: TextToken) -> tuple[str, float] | None:
        """숫자 바로 오른쪽을 잘라 확대한 뒤 단위를 다시 읽는다.

        표시창의 단위가 숫자보다 훨씬 작게 찍힌 저울이 있다(CAS·METTLER 사진 두 장에서
        숫자 높이의 절반 이하). 전체 사진의 OCR 은 그 글자를 아예 놓친다. 숫자 끝보다 살짝
        오른쪽에서 잘라야 한다 — 마지막 자리 일부가 들어오면 그 조각을 6·9 로 읽는다.
        """
        import cv2

        engine = self.ocr_engine or ocr.get_engine()
        height = number.height
        y1 = int(max(0, number.top))
        y2 = int(min(image.shape[0], number.bottom + 0.3 * height))
        glyphs: list[tuple[str, float, float]] = []
        # 두 창으로 본다. OCR 이 숫자 상자를 작은 g 까지 넓게 잡는 사진이 있어(31291 g), 숫자 끝의
        # 바깥만 보면 g 를 지나친다. 안쪽 창에는 마지막 자리 조각이 들어오지만 그 조각은 숫자
        # 높이만큼 커서 unit_from_zoom 의 크기 규칙이 걸러낸다.
        for start, end in ((0.03, 0.9), (-0.35, 0.6)):
            x1 = int(max(0, number.right + start * height))
            x2 = int(min(image.shape[1], number.right + end * height))
            if x2 - x1 < 4 or y2 - y1 < 4:
                continue
            crop = cv2.resize(
                image[y1:y2, x1:x2], None, fx=ZOOM_SCALE, fy=ZOOM_SCALE, interpolation=cv2.INTER_CUBIC
            )
            for _name, variant in self.preprocessor(crop):
                result, _elapsed = engine(np.asarray(variant))
                for box, raw_text, raw_score in result or ():
                    points = np.asarray(box, dtype=float)
                    glyph_height = (points[:, 1].max() - points[:, 1].min()) / ZOOM_SCALE
                    glyphs.append((str(raw_text), float(raw_score), glyph_height / height))
        return unit_from_zoom(glyphs)


def _gram_decimals(parsed: ParsedWeight | None) -> int:
    """g 단위 값의 소수점 아래 자릿수. kg 이거나 값이 없으면 0."""
    if parsed is None:
        return 0
    text = parsed.normalized_text.strip().lower()
    if text.endswith("kg") or not text.endswith("g"):
        return 0
    number = text[:-1].strip()
    return len(number.split(".", 1)[1]) if "." in number else 0

