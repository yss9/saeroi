"""파이프라인의 pytest 스테이지가 실제로 도는지 확인하는 최소 테스트."""

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_returns_up():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_root_is_reachable():
    assert client.get("/").status_code == 200
