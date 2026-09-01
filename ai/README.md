

## 현재 상태 (2026-09-01)

파이프라인 검증용 **스캐폴드**가 들어 있습니다. 실제 추론 로직이 준비되면 통째로 교체하세요.

```
app/main.py        FastAPI 앱 (GET /health, GET /)
tests/test_main.py pytest — 파이프라인의 테스트 스테이지가 실제로 도는지 확인용
requirements.txt   fastapi, uvicorn, httpx
docker/Dockerfile  2-stage 빌드 (이미 있던 파일)
```

교체할 때 유지해야 할 인프라 계약은 둘뿐입니다.

- **포트 8000** — backend 가 `AI_SERVER_BASE_URL=http://ai:8000` 으로 호출합니다.
- **이미지 이름** `circular-fashion/ai:latest` — Jenkinsfile 이 이미 그렇게 태깅합니다.

`GET /health` 는 필수는 아닙니다. ai 는 internal 네트워크 전용이라 배포 판정은 컨테이너 상태로만
이뤄집니다. 다만 backend 나 운영자가 컨테이너 안에서 확인할 때 유용하니 남겨두길 권합니다.
