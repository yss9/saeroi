# 새로이 Backend

## 저울 사진 OCR 연동

브라우저는 AI 컨테이너를 직접 호출하지 않습니다. 인증된 사용자가 backend의
`POST /api/materials/recognize-weight`에 multipart 필드 `image`를 보내면 backend가 내부 AI
endpoint `POST /v1/vision/weight`로 전달합니다. 배포 환경에는 다음 값을 설정합니다.

```dotenv
AI_SERVER_BASE_URL=http://ai:8000
```

AI endpoint 자체를 로컬에서 확인하는 정확한 요청은 다음과 같습니다.

```bash
curl -F "image=@scale.jpg" http://localhost:8000/v1/vision/weight
```

입력은 JPEG 또는 PNG 한 장, **10MB 이하**입니다. AI 응답의 `status`는 `SUCCESS`,
`LOW_CONFIDENCE`, `INVALID_IMAGE`, `SCALE_NOT_FOUND`, `DISPLAY_NOT_FOUND`, `OCR_FAILED`,
`INVALID_WEIGHT_FORMAT` 중 하나이며, 인식된 무게는 `weight.value`에 **정수 gram**으로
전달됩니다.

화면은 OCR 값을 수량에 자동 반영하지 않습니다. 사용자가 결과를 보고 **인식 무게 적용**을
눌러야 gram 값이 kg 입력란에 반영됩니다. `LOW_CONFIDENCE`는 경고와 함께 표시되며 전체
신뢰도가 `0.70` 이상일 때만 사용자가 적용할 수 있고, 그보다 낮으면 재촬영해야 합니다.
나머지 실패 상태는 적용 버튼 없이 재촬영 안내를 표시합니다.

### 소재 등록, 증빙, 온체인 해시 순서

저울 사진을 선택한 소재 등록은 다음 순서를 지킵니다.

1. 사용자가 OCR 결과를 확인하고 적용한 뒤 소재를 생성합니다.
2. 생성된 `materialId`에 원본 사진을 `MATERIAL` / `WEIGHT_PHOTO` 증빙으로 업로드합니다.
3. 업로드 응답의 실제 `EvidenceFile.fileHash`를 온체인 mint의 `evidenceHash`로 전달합니다.
4. 증빙 업로드가 실패하거나 빈 결과를 반환하면 mint를 시작하지 않습니다. 재시도는 이미 생성된
   소재와 같은 사진을 재사용하며, 업로드 후 mint만 실패한 경우에는 같은 증빙과 실제 해시를
   재사용합니다.
5. 사진을 선택하지 않은 기존 경로만 소재 코드 기반의 결정적 placeholder 해시를 사용합니다.

따라서 `WEIGHT_PHOTO`가 있는 등록에서 placeholder가 실제 사진 해시를 대신하지 않습니다.

AI의 저울/표시창 검출기는 교체 가능한 `Detector` 인터페이스 뒤의 OpenCV 휴리스틱 MVP입니다.
운영 전 실제 공급 현장의 저울, 조명, 거리, 배경을 사용한 현장 보정이 필요합니다.

## 검증 명령

```bash
./mvnw test
./mvnw -DskipTests package
```
