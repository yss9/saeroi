import pytest

from app.vision.schemas import (
    DetectionResult,
    OcrResult,
    VisionStatus,
    WeightRecognitionResponse,
    WeightValue,
)
from app.vision.weight_parser import parse_weight


@pytest.mark.parametrize(("text", "gram"), [
    ("2.35 kg", 2350), ("2350g", 2350), ("0.85 KG", 850),
    ("2.3S kg", 2350), ("SOO g", 500),
])
def test_parse_supported_weight(text, gram):
    assert parse_weight(text).gram == gram


@pytest.mark.parametrize("text", [
    "2.35", "12 lb", "0 g", "100.001 kg", "2..35 kg", "2kg0", "2g.5", "2kg|",
    "-2 kg", "+2 kg", "1,500 g", "2,35 kg", "100,001 g",
])
def test_rejects_unsupported_or_out_of_range_weight(text):
    assert parse_weight(text) is None


@pytest.mark.parametrize(("text", "gram", "normalized"), [
    ("weight: l.2S kg", 1250, "1.25 kg"),
    ("weight: I0 g", 10, "10 g"),
    ("weight: |0 g", 10, "10 g"),
    ("weight: s0 g", 50, "50 g"),
    ("weight: B0g", 80, "80g"),
])
def test_corrects_only_the_numeric_token_before_a_unit(text, gram, normalized):
    parsed = parse_weight(text)
    assert parsed.gram == gram
    assert parsed.raw_text in text
    assert parsed.normalized_text == normalized


def test_finds_a_weight_token_within_surrounding_ocr_text():
    parsed = parse_weight("SCALE 2.35 kg")
    assert parsed.raw_text == "2.35 kg"
    assert parsed.normalized_text == "2.35 kg"


def test_response_serializes_contract_fields_as_camel_case():
    response = WeightRecognitionResponse(
        status=VisionStatus.SUCCESS,
        weight=WeightValue(value=2350),
        ocr=OcrResult(raw_text="2.35 kg", confidence=0.91),
        detection=DetectionResult(scale_confidence=0.88, display_confidence=0.92),
    )

    assert response.model_dump(by_alias=True) == {
        "status": "SUCCESS",
        "weight": {"value": 2350, "unit": "g"},
        "ocr": {"rawText": "2.35 kg", "confidence": 0.91},
        "detection": {"scaleConfidence": 0.88, "displayConfidence": 0.92},
        "confidence": 0.0,
        "message": None,
    }
