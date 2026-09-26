"""요청·응답 형태. 백엔드와의 계약이므로 필드 이름을 함부로 바꾸지 않는다."""

from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field


class OcrLine(BaseModel):
    """OCR 이 읽은 한 줄. 좌표가 함께 와야 '어느 항목의 값인지' 를 판단할 수 있다."""

    text: str
    score: float = Field(description="이 줄을 얼마나 확신하는지. 0~1")
    x_min: float
    y_min: float
    x_max: float
    y_max: float

    @property
    def y_center(self) -> float:
        return (self.y_min + self.y_max) / 2

    @property
    def height(self) -> float:
        return self.y_max - self.y_min


class Verdict(str, Enum):
    """판정. 등록을 막는 게이트가 아니라 사람 검증이 필요한 건을 골라내는 분류다."""

    PASS = "PASS"          # 신고값과 증빙이 허용 오차 안에서 일치
    FLAG = "FLAG"          # 값은 읽었는데 신고값과 벌어진다
    UNREADABLE = "UNREADABLE"  # 중량을 특정하지 못했다


class WeightExtraction(BaseModel):
    gram: int | None = Field(default=None, description="증빙에서 읽어낸 중량(gram 정수)")
    source_text: str | None = Field(default=None, description="그 값을 뽑아낸 원문")
    anchor: str | None = Field(default=None, description="근거로 삼은 키워드. 없으면 추정")
    confidence: float = Field(default=0.0, description="추출 신뢰도. OCR 점수와 근거 강도를 함께 반영")


class OcrResponse(BaseModel):
    lines: list[OcrLine]
    elapsed_ms: int


class WeightCheckResponse(BaseModel):
    verdict: Verdict
    extracted: WeightExtraction
    declared_gram: int | None = None
    delta_ratio: float | None = Field(
        default=None, description="|읽은값 - 신고값| / 신고값. 신고값이 없으면 None"
    )
    tolerance: float = Field(description="이번 판정에 적용한 허용 오차 비율")
    lines: list[OcrLine] = Field(default_factory=list, description="판단 근거. 화면이 하이라이트할 수 있다")
