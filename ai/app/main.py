"""AI 서버.

인프라 계약 — 바꾸지 말 것
  - 포트 8000: backend 가 AI_SERVER_BASE_URL=http://ai:8000 으로 호출한다.
  - GET /health: 배포 후 컨테이너가 살아 있는지 확인하는 지점.

엔드포인트를 두 층으로 나눈 이유
  /v1/ocr            읽기만 한다. 무엇이 잘못 읽혔는지 눈으로 확인할 때 쓴다.
  /v1/evidence/weight 읽은 뒤 신고값과 대조한다. 실제로 백엔드가 부르는 쪽.
아래쪽만 두면 판정이 이상할 때 OCR 문제인지 우리 규칙 문제인지 가릴 수 없다.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from app import ocr, weight
from app.schemas import OcrResponse, Verdict, WeightCheckResponse
from app.vision.schemas import WeightRecognitionResponse
from app.vision.service import WeightRecognitionService

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="circular-fashion ai", version="0.1.0")

# 계근표 사진이 이보다 크면 대개 원본 무보정이다. 읽기 전에 거른다.
MAX_UPLOAD_BYTES = 15 * 1024 * 1024
ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"}

# 저울 사진 인식 계약은 기존 계근표 OCR보다 좁다. 기존 상수와 합치지 않는다.
VISION_MAX_UPLOAD_BYTES = 10 * 1024 * 1024
VISION_ALLOWED_TYPES = {"image/jpeg", "image/png"}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/")
def root() -> dict[str, str]:
    return {"service": "ai", "status": "ok"}


async def _read_upload(file: UploadFile) -> bytes:
    if file.content_type and file.content_type not in ALLOWED_TYPES:
        raise HTTPException(415, f"지원하지 않는 형식입니다: {file.content_type}")
    data = await file.read()
    if not data:
        raise HTTPException(400, "빈 파일입니다.")
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, f"파일이 너무 큽니다. 최대 {MAX_UPLOAD_BYTES // 1024 // 1024}MB")
    return data


async def _read_vision_upload(image: UploadFile) -> bytes:
    if image.content_type not in VISION_ALLOWED_TYPES:
        raise HTTPException(415, f"지원하지 않는 형식입니다: {image.content_type}")
    data = await image.read()
    if not data:
        raise HTTPException(400, "빈 파일입니다.")
    if len(data) > VISION_MAX_UPLOAD_BYTES:
        raise HTTPException(
            413,
            f"파일이 너무 큽니다. 최대 {VISION_MAX_UPLOAD_BYTES // 1024 // 1024}MB",
        )
    return data


def get_weight_recognition_service() -> WeightRecognitionService:
    """Build the light orchestrator; the OCR model itself remains lazy and cached."""
    return WeightRecognitionService()


@app.post("/v1/ocr", response_model=OcrResponse)
async def read_document(file: UploadFile = File(...)) -> OcrResponse:
    """이미지에서 글자와 위치만 읽는다. 판정하지 않는다."""
    data = await _read_upload(file)
    try:
        lines, elapsed_ms = ocr.read_lines(data)
    except Exception as error:  # 손상된 이미지, 모델 로딩 실패 등
        logger.exception("OCR 실패")
        raise HTTPException(422, f"이미지를 읽지 못했습니다: {error}") from error
    return OcrResponse(lines=lines, elapsed_ms=elapsed_ms)


@app.post("/v1/evidence/weight", response_model=WeightCheckResponse)
async def check_weight(
    file: UploadFile = File(..., description="계근표·저울 사진"),
    declared_gram: int | None = Form(default=None, description="공급자가 신고한 중량(gram)"),
    tolerance: float = Form(default=weight.DEFAULT_TOLERANCE, description="허용 오차 비율"),
) -> WeightCheckResponse:
    """증빙에서 중량을 읽어 신고값과 대조한다.

    **등록을 막지 않는다.** FLAG 는 사람이 확인해야 한다는 표시일 뿐이다 (weight.judge 참고).
    """
    data = await _read_upload(file)
    try:
        lines, _ = ocr.read_lines(data)
    except Exception as error:
        logger.exception("OCR 실패")
        raise HTTPException(422, f"이미지를 읽지 못했습니다: {error}") from error

    extracted = weight.extract_weight(lines)
    verdict, delta = weight.judge(extracted, declared_gram, tolerance)

    logger.info(
        "weight-check verdict=%s extracted=%s declared=%s delta=%s anchor=%s",
        verdict.value, extracted.gram, declared_gram, delta, extracted.anchor,
    )
    return WeightCheckResponse(
        verdict=verdict,
        extracted=extracted,
        declared_gram=declared_gram,
        delta_ratio=delta,
        tolerance=tolerance,
        # 판정 근거를 함께 돌려준다. 화면이 어느 줄을 보고 판단했는지 보여줄 수 있고,
        # FLAG 를 검토하는 사람이 사진을 다시 열지 않아도 된다.
        lines=lines,
    )


@app.post("/v1/vision/weight", response_model=WeightRecognitionResponse)
async def recognize_weight(image: UploadFile = File(...)) -> WeightRecognitionResponse:
    """Suggest a gram weight from a digital scale photo."""
    data = await _read_vision_upload(image)
    try:
        return get_weight_recognition_service().recognize(data)
    except Exception as error:
        # Exception values may contain OCR text. Record only the type to avoid leaking it.
        logger.error(
            "vision-weight recognition failed error_type=%s",
            type(error).__name__,
        )
        raise HTTPException(status_code=500, detail="Internal server error") from None
