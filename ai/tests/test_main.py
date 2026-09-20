"""파이프라인의 Test 스테이지가 실제로 도는지 확인하는 최소 테스트.

OCR 엔드포인트는 여기서 부르지 않는다 — 모델(약 15MB)을 올려야 해서 CI 가 느려지고,
실패해도 원인이 모델인지 우리 코드인지 가려지지 않는다. 규칙 검증은 test_weight.py 가 한다.
"""

import pytest
import cv2
import numpy as np
from fastapi.testclient import TestClient

from app.main import VISION_MAX_UPLOAD_BYTES, app
from app.vision.schemas import VisionStatus, WeightRecognitionResponse, WeightValue

client = TestClient(app)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_root():
    assert client.get("/").status_code == 200


def test_빈_파일은_400():
    response = client.post("/v1/ocr", files={"file": ("empty.jpg", b"", "image/jpeg")})
    assert response.status_code == 400


def test_지원하지_않는_형식은_415():
    response = client.post("/v1/ocr", files={"file": ("a.txt", b"hello", "text/plain")})
    assert response.status_code == 415


def test_vision_weight_accepts_image_part_and_returns_service_response(monkeypatch):
    class StubService:
        def recognize(self, data: bytes) -> WeightRecognitionResponse:
            assert data == b"jpeg-data"
            return WeightRecognitionResponse(
                status=VisionStatus.SUCCESS,
                weight=WeightValue(value=2350),
                confidence=0.93,
            )

    monkeypatch.setattr("app.main.get_weight_recognition_service", lambda: StubService())

    response = client.post(
        "/v1/vision/weight",
        files={"image": ("scale.jpg", b"jpeg-data", "image/jpeg")},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "SUCCESS"
    assert response.json()["weight"] == {"value": 2350, "unit": "g"}


def test_vision_weight_domain_failure_remains_http_200(monkeypatch):
    class StubService:
        def recognize(self, _data: bytes) -> WeightRecognitionResponse:
            return WeightRecognitionResponse(
                status=VisionStatus.SCALE_NOT_FOUND,
                message="저울을 찾지 못했습니다.",
            )

    monkeypatch.setattr("app.main.get_weight_recognition_service", lambda: StubService())

    response = client.post(
        "/v1/vision/weight",
        files={"image": ("scale.png", b"png-data", "image/png")},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "SCALE_NOT_FOUND"


def test_vision_weight_rejects_old_webp_allowance_before_service_call(monkeypatch):
    monkeypatch.setattr(
        "app.main.get_weight_recognition_service",
        lambda: pytest.fail("service must not be called"),
    )

    response = client.post(
        "/v1/vision/weight",
        files={"image": ("scale.webp", b"webp-data", "image/webp")},
    )

    assert response.status_code == 415


def test_vision_weight_rejects_more_than_ten_megabytes_before_service_call(
    monkeypatch,
):
    monkeypatch.setattr(
        "app.main.get_weight_recognition_service",
        lambda: pytest.fail("service must not be called"),
    )

    response = client.post(
        "/v1/vision/weight",
        files={
            "image": (
                "scale.jpg",
                b"x" * (VISION_MAX_UPLOAD_BYTES + 1),
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 413


def test_vision_weight_accepts_exactly_ten_megabytes(monkeypatch):
    class StubService:
        def recognize(self, data: bytes) -> WeightRecognitionResponse:
            assert len(data) == VISION_MAX_UPLOAD_BYTES
            return WeightRecognitionResponse(status=VisionStatus.OCR_FAILED)

    monkeypatch.setattr("app.main.get_weight_recognition_service", lambda: StubService())

    response = client.post(
        "/v1/vision/weight",
        files={
            "image": (
                "scale.jpg",
                b"x" * VISION_MAX_UPLOAD_BYTES,
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 200


def test_vision_weight_requires_image_part_name():
    response = client.post(
        "/v1/vision/weight",
        files={"file": ("scale.jpg", b"jpeg-data", "image/jpeg")},
    )

    assert response.status_code == 422


def test_vision_weight_invalid_image_is_domain_response_without_loading_ocr(
    monkeypatch,
):
    monkeypatch.setattr(
        "app.ocr.get_engine",
        lambda: pytest.fail("OCR model must not load for an invalid image"),
    )

    response = client.post(
        "/v1/vision/weight",
        files={"image": ("broken.jpg", b"not-an-image", "image/jpeg")},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "INVALID_IMAGE"


@pytest.mark.parametrize("extension", [".webp", ".bmp", ".tiff"])
def test_vision_weight_rejects_mime_spoofed_decodable_formats(extension):
    image = np.full((600, 800, 3), 128, dtype=np.uint8)
    cv2.rectangle(image, (50, 50), (750, 550), (0, 0, 0), 8)
    encoded, data = cv2.imencode(extension, image)
    assert encoded

    response = client.post(
        "/v1/vision/weight",
        files={"image": ("scale.jpg", data.tobytes(), "image/jpeg")},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "INVALID_IMAGE"


def test_vision_weight_unexpected_exception_is_handled_as_generic_http_500(
    monkeypatch, caplog
):
    class FailingService:
        def recognize(self, _data: bytes) -> WeightRecognitionResponse:
            raise RuntimeError("sensitive raw OCR text")

    monkeypatch.setattr(
        "app.main.get_weight_recognition_service", lambda: FailingService()
    )

    response = client.post(
        "/v1/vision/weight",
        files={"image": ("scale.jpg", b"private-image-bytes", "image/jpeg")},
    )

    assert response.status_code == 500
    assert response.json() == {"detail": "Internal server error"}
    assert "vision-weight recognition failed error_type=RuntimeError" in caplog.text
    assert "private-image-bytes" not in caplog.text
    assert "sensitive raw OCR text" not in caplog.text
    assert "sensitive raw OCR text" not in response.text
