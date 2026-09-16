"""파이프라인의 Test 스테이지가 실제로 도는지 확인하는 최소 테스트.

OCR 엔드포인트는 여기서 부르지 않는다 — 모델(약 15MB)을 올려야 해서 CI 가 느려지고,
실패해도 원인이 모델인지 우리 코드인지 가려지지 않는다. 규칙 검증은 test_weight.py 가 한다.
"""

from fastapi.testclient import TestClient

from app.main import app

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
