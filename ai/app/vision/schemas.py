from __future__ import annotations

from enum import Enum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class VisionStatus(str, Enum):
    INVALID_IMAGE = "INVALID_IMAGE"
    SCALE_NOT_FOUND = "SCALE_NOT_FOUND"
    DISPLAY_NOT_FOUND = "DISPLAY_NOT_FOUND"
    OCR_FAILED = "OCR_FAILED"
    INVALID_WEIGHT_FORMAT = "INVALID_WEIGHT_FORMAT"
    LOW_CONFIDENCE = "LOW_CONFIDENCE"
    SUCCESS = "SUCCESS"


class WeightValue(BaseModel):
    value: int
    unit: Literal["g"] = "g"


class OcrResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    raw_text: str = Field(alias="rawText")
    confidence: float


class DetectionResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    scale_confidence: float = Field(alias="scaleConfidence")
    display_confidence: float = Field(alias="displayConfidence")


class Detection(BaseModel):
    bbox: tuple[int, int, int, int]
    confidence: float


class WeightRecognitionResponse(BaseModel):
    status: VisionStatus
    weight: WeightValue | None = None
    ocr: OcrResult | None = None
    detection: DetectionResult | None = None
    confidence: float = 0.0
    message: str | None = None


class ParsedWeight(BaseModel):
    gram: int
    raw_text: str
    normalized_text: str
