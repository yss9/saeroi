"""AI 서버 최소 골격.

이 파일은 파이프라인 검증용 스캐폴드다. 실제 추론 로직이 들어오면 통째로 교체해도 된다.
다만 아래 두 가지는 인프라 계약이므로 유지해야 한다.

  - 포트 8000: backend 가 AI_SERVER_BASE_URL=http://ai:8000 으로 호출한다.
  - GET /health: 배포 후 컨테이너가 살아 있는지 확인하는 지점.
    (ai 는 internal 네트워크 전용이라 외부에서 접근할 수 없고,
     scripts/health-check.sh 는 컨테이너 상태만 보지만, backend 나 운영자가
     컨테이너 안에서 직접 확인할 때 이 엔드포인트를 쓴다)
"""

from fastapi import FastAPI

app = FastAPI(title="circular-fashion ai", version="0.0.1")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/")
def root() -> dict[str, str]:
    return {"service": "ai", "status": "scaffold"}
