"""인식 파이프라인의 분기와 선택 규칙.

OCR 엔진을 가짜로 세워 모델 없이 규칙만 본다. 실제 사진으로 끝까지 확인하는 것은
`test_vision_real_photos.py` 가 맡는다.
"""

from __future__ import annotations

from collections.abc import Callable

import numpy as np
import pytest

from app.vision.schemas import VisionStatus
from app.vision.service import WeightRecognitionService


VALID_JPEG = b"valid-jpeg"
IMAGE = np.zeros((600, 800, 3), dtype=np.uint8)


def token(
    text: str,
    confidence: float,
    *,
    left: float = 0,
    top: float = 0,
    width: float = 120,
    height: float = 60,
):
    """OCR 한 덩어리. 글자 높이가 선택을 가르므로 자리를 직접 준다."""
    return [
        [left, top],
        [left + width, top],
        [left + width, top + height],
        [left, top + height],
    ], text, confidence


def ocr_result(*tokens):
    return list(tokens), None


@pytest.fixture
def service_factory() -> Callable[..., WeightRecognitionService]:
    def make_service(
        *,
        text: str = "2.35 kg",
        ocr: float = 0.94,
        validator: Callable[[bytes], np.ndarray] | None = None,
        ocr_engine: Callable[[np.ndarray], object] | None = None,
        variants: int = 1,
    ) -> WeightRecognitionService:
        return WeightRecognitionService(
            image_validator=validator or (lambda _data: IMAGE.copy()),
            preprocessor=lambda image: [
                (f"variant-{index}", image) for index in range(variants)
            ],
            ocr_engine=ocr_engine or (lambda _image: ocr_result(token(text, ocr))),
        )

    return make_service


def test_invalid_image_maps_to_invalid_image_status(service_factory):
    def reject(_data: bytes) -> np.ndarray:
        raise ValueError("cannot decode")

    result = service_factory(validator=reject).recognize(b"broken")

    assert result.status == VisionStatus.INVALID_IMAGE
    assert result.message
    assert result.weight is None


def test_no_ocr_text_maps_to_ocr_failed(service_factory):
    service = service_factory(ocr_engine=lambda _image: (None, None))

    result = service.recognize(VALID_JPEG)

    assert result.status == VisionStatus.OCR_FAILED
    assert result.message
    assert result.weight is None


def test_text_without_any_number_maps_to_ocr_failed(service_factory):
    """숫자가 하나도 없으면 고를 후보 자체가 없다."""
    result = service_factory(text="SORTED CLOTHING").recognize(VALID_JPEG)

    assert result.status == VisionStatus.OCR_FAILED
    assert result.weight is None


def test_number_without_unit_maps_to_invalid_weight_format(service_factory):
    """숫자는 읽었는데 g·kg 이 붙지 않았다면 무게라고 단정하지 않는다."""
    result = service_factory(text="1250").recognize(VALID_JPEG)

    assert result.status == VisionStatus.INVALID_WEIGHT_FORMAT
    assert result.message
    assert result.weight is None
    assert result.ocr.raw_text == "1250"


def test_tallest_number_wins_over_a_smaller_one(service_factory):
    """저울은 지금 재고 있는 값을 가장 크게 보여준다."""
    engine_result = ocr_result(
        token("0.85 kg", 0.99, top=0, height=30),
        token("2.35 kg", 0.80, top=100, height=90),
    )

    result = service_factory(ocr_engine=lambda _image: engine_result).recognize(
        VALID_JPEG
    )

    assert result.weight.value == 2350
    assert result.ocr.raw_text == "2.35 kg"


def test_tare_labelled_value_is_not_selected(service_factory):
    """TARE 줄이 본값보다 크게 읽혀도 집지 않는다.

    0.0 을 무게로 집으면 화면은 인식에 성공했다고 말하면서 0 g 을 채운다.
    """
    engine_result = ocr_result(
        token("TARE:", 0.99, left=0, top=100, width=80, height=90),
        token("0.0 kg", 0.99, left=90, top=100, width=120, height=90),
        token("34.8 kg", 0.80, left=0, top=300, width=120, height=60),
    )

    result = service_factory(ocr_engine=lambda _image: engine_result).recognize(
        VALID_JPEG
    )

    assert result.weight.value == 34_800


def test_unit_read_as_a_separate_token_is_attached(service_factory):
    """표시창은 숫자와 단위를 따로 찍는다 — 붙여 읽지 않으면 무게가 되지 않는다."""
    engine_result = ocr_result(
        token("18.7", 0.99, left=0, top=0, width=110, height=60),
        token("kg", 0.97, left=115, top=30, width=30, height=20),
    )

    result = service_factory(ocr_engine=lambda _image: engine_result).recognize(
        VALID_JPEG
    )

    assert result.weight.value == 18_700
    assert result.confidence == 0.97


def test_kg_is_preferred_when_both_unit_lamps_are_read(service_factory):
    """`g` 와 `kg` 표시등이 나란한 저울에서 잘못 고르면 1000 배가 어긋난다."""
    engine_result = ocr_result(
        token("18.7", 0.99, left=0, top=0, width=110, height=60),
        token("g", 0.95, left=112, top=35, width=20, height=18),
        token("kg", 0.97, left=136, top=32, width=30, height=22),
    )

    result = service_factory(ocr_engine=lambda _image: engine_result).recognize(
        VALID_JPEG
    )

    assert result.weight.value == 18_700


def test_best_valid_candidate_is_selected_across_variants(service_factory):
    """전처리 변형마다 다르게 읽힌다 — 7세그먼트 LED 는 원본에서 자주 틀린다."""
    responses = iter(
        [
            ocr_result(token("866", 0.96, height=90)),
            ocr_result(token("noise", 0.99, height=90)),
            ocr_result(token("34.8 kg", 0.94, height=90)),
        ]
    )
    service = service_factory(
        variants=3,
        ocr_engine=lambda _image: next(responses),
    )

    result = service.recognize(VALID_JPEG)

    assert result.weight.value == 34_800
    assert result.ocr.raw_text == "34.8 kg"


def test_final_confidence_is_the_lower_of_number_and_unit(service_factory):
    engine_result = ocr_result(
        token("2.35", 0.96, left=0, top=0, width=110, height=60),
        token("kg", 0.91, left=115, top=30, width=30, height=20),
    )

    result = service_factory(ocr_engine=lambda _image: engine_result).recognize(
        VALID_JPEG
    )

    assert result.status == VisionStatus.SUCCESS
    assert result.confidence == 0.91
    assert result.weight.value == 2350


@pytest.mark.parametrize(
    ("confidence", "status"),
    [(0.89, VisionStatus.LOW_CONFIDENCE), (0.90, VisionStatus.SUCCESS)],
)
def test_confidence_threshold_is_inclusive_at_point_nine(
    service_factory, confidence, status
):
    result = service_factory(ocr=confidence).recognize(VALID_JPEG)

    assert result.status == status
    assert result.confidence == confidence


def test_candidate_is_returned_even_below_applicable_confidence(service_factory):
    result = service_factory(ocr=0.69).recognize(VALID_JPEG)

    assert result.status == VisionStatus.LOW_CONFIDENCE
    assert result.confidence == 0.69
    assert result.weight.value == 2350


def test_detection_carries_number_and_unit_confidence(service_factory):
    """backend 가 confidence 를 이 두 값의 최솟값과 정확히 비교한다."""
    engine_result = ocr_result(
        token("2.35", 0.96, left=0, top=0, width=110, height=60),
        token("kg", 0.91, left=115, top=30, width=30, height=20),
    )

    result = service_factory(ocr_engine=lambda _image: engine_result).recognize(
        VALID_JPEG
    )

    assert result.detection.scale_confidence == 0.96
    assert result.detection.display_confidence == 0.91
    assert result.confidence == min(
        result.ocr.confidence,
        result.detection.scale_confidence,
        result.detection.display_confidence,
    )


def test_unexpected_ocr_exception_is_not_converted_to_domain_failure(service_factory):
    def explode(_image):
        raise RuntimeError("engine crashed")

    with pytest.raises(RuntimeError):
        service_factory(ocr_engine=explode).recognize(VALID_JPEG)
