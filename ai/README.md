

# 새로이 AI 서버

FastAPI 기반 OCR 서버입니다. 계근표 OCR과 저울 사진 무게 인식을 제공하며 기본 포트는
`8000`입니다. 배포 환경에서 backend는 `AI_SERVER_BASE_URL=http://ai:8000`으로 이 서버를
호출합니다.

## 저울 사진 무게 인식

`POST /v1/vision/weight`는 multipart 필드 `image`로 저울 사진 한 장을 받습니다. 지원 형식은
JPEG(`image/jpeg`)와 PNG(`image/png`)뿐이며 파일 크기는 **10MB 이하**여야 합니다.

```bash
curl -F "image=@scale.jpg" http://localhost:8000/v1/vision/weight
```

응답의 `status`는 다음 중 하나입니다.

- `SUCCESS`: 무게를 높은 신뢰도로 인식했습니다.
- `LOW_CONFIDENCE`: 무게 후보는 있지만 사용자가 사진과 숫자를 확인해야 합니다.
- `INVALID_IMAGE`: 이미지 바이트를 해석할 수 없습니다.
- `SCALE_NOT_FOUND`: 사진에서 저울을 찾지 못했습니다.
- `DISPLAY_NOT_FOUND`: 저울 표시창을 찾지 못했습니다.
- `OCR_FAILED`: 표시창에서 문자를 읽지 못했습니다.
- `INVALID_WEIGHT_FORMAT`: OCR 문자열에서 지원하는 `g` 또는 `kg` 값을 찾지 못했습니다.

인식된 값은 `weight.value`에 **정수 gram**으로 반환되고 단위는 항상 `weight.unit: "g"`입니다.
예를 들어 `2.35 kg`은 `{"value": 2350, "unit": "g"}`가 됩니다. 도메인 인식 실패도 HTTP
200으로 위 상태를 반환하며, 빈 파일·크기 초과·지원하지 않는 형식 같은 요청 오류는 각각 HTTP
4xx로 거절합니다.

현재 저울 본체와 표시창 검출은 `Detector` 인터페이스 뒤의 OpenCV 휴리스틱 구현입니다. 이는
교체 가능한 MVP이며 실제 공급 현장의 저울 종류, 촬영 거리, 조명, 배경 사진으로 임계값과
정확도를 반드시 현장 보정해야 합니다. 학습 모델 등으로 바꿀 때도 endpoint와 응답 계약은
유지합니다.

## 로컬 실행과 테스트

```bash
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

테스트는 production 의존성 파일에 `pytest`를 추가하지 않고 UV가 검증 환경에만 주입하게
실행합니다. 저장소 루트의 PowerShell에서 다음 명령을 그대로 사용합니다.

```powershell
$env:UV_CACHE_DIR=(Join-Path (Get-Location) '.uv-cache')
$env:UV_PYTHON_INSTALL_DIR=(Join-Path (Get-Location) '.uv-python')
uv run --no-project --with-requirements requirements.txt --with pytest python -m pytest -q
```

Docker 이미지는 저장소 루트에서 다음과 같이 확인합니다.

```bash
docker build -f docker/Dockerfile -t circular-fashion/ai:weight-ocr-test .
```

`GET /health`는 `{"status": "UP"}`를 반환합니다. Jenkinsfile은 배포 이미지를
`circular-fashion/ai:latest`로 태깅합니다.
