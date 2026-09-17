# Supplier Weight OCR Design

## Purpose

Supplier가 최초 소재를 등록할 때 저울 사진에서 무게를 제안하고, Supplier가 확인한 값을 기존 `quantity` 입력에 반영한다. OCR 결과는 확정값이 아니며 사용자가 적용하거나 수정해야 한다. OCR에 사용한 사진은 소재 생성 후 `WEIGHT_PHOTO` 증빙으로 저장하고, 그 파일의 실제 SHA-256 해시를 소재의 온체인 발행 근거로 사용한다.

## Scope

이 기능은 `S15P21E104_ai`, `S15P21E104_back`, `S15P21E104_front` 세 저장소를 연결한다.

- 디지털 저울의 LCD/LED 숫자와 `g`, `kg` 단위를 인식한다.
- 1g 이상 100kg 이하만 정상 무게 후보로 인정한다.
- JPG, JPEG, PNG 이미지를 지원하며 파일당 최대 크기는 10MB다.
- 학습 가중치가 없는 MVP에서는 OpenCV 기반 휴리스틱으로 저울과 표시창 후보를 찾는다.
- 탐지기는 인터페이스 뒤에 두어 추후 YOLO 구현으로 교체할 수 있게 한다.
- 기존 계근표 OCR(`/v1/ocr`, `/v1/evidence/weight`) 계약은 유지한다.
- 소재 등록 요청과 DB의 gram 정수 정책은 변경하지 않는다.

아날로그 저울, 바늘식 저울, 한 화면의 복수 무게, 100kg 초과 측정, 모델 학습과 데이터 라벨링은 범위 밖이다.

## Supplier User Flow

1. Supplier가 소재 등록 화면의 `수량 (kg)` 근처에서 `저울 사진으로 측정`을 선택한다.
2. 브라우저가 확장자와 10MB 상한을 먼저 검사하고 사진 미리보기를 표시한다.
3. 프론트엔드는 인증된 Spring Boot API에 사진을 전송한다.
4. Spring Boot는 내부 AI 서비스에 사진을 중계한다.
5. AI 서비스는 이미지 검증, 저울 후보 탐지, 표시창 후보 탐지, 전처리, OCR, 파싱, gram 정규화를 수행한다.
6. 성공 또는 저신뢰도 결과를 화면에 보여준다. 사용자가 `적용`을 눌러야 `quantityKg`이 변경된다.
7. 사용자는 적용된 수량을 자유롭게 수정할 수 있다. 최종 등록 수량은 기존 폼 검증을 그대로 통과한다.
8. 소재 등록을 누르면 기존 순서에 사진 증빙 저장 단계가 추가된다.
   - 지갑 연결 확인
   - 소재 DB 생성
   - 같은 사진을 `WEIGHT_PHOTO` 증빙으로 업로드
   - 업로드 응답의 `fileHash`를 사용해 온체인 발행
   - 온체인 결과를 서버에 연결
   - 보유 소재 화면으로 이동
9. 사진 증빙 업로드가 실패하면 온체인 발행을 시작하지 않는다. 이미 생성된 소재와 선택한 사진을 화면 상태에 유지하고, 다음 제출에서 소재를 중복 생성하지 않은 채 증빙 업로드부터 재시도한다.
10. 사용자가 사진을 선택하지 않았다면 기존 수동 수량 입력과 등록 흐름이 그대로 동작하며 기존 placeholder evidence hash를 사용한다.

## Architecture

```text
MaterialForm
  -> POST /api/materials/recognize-weight (multipart image)
      -> POST AI /v1/vision/weight (multipart image)
          -> image validation
          -> heuristic ScaleDetector
          -> heuristic DisplayDetector
          -> preprocessing variants
          -> RapidOCR
          -> WeightParser
          -> confidence/status
  <- recognition result

MaterialForm submit
  -> POST /api/materials
  -> POST /api/evidence (WEIGHT_PHOTO, same File)
  -> MaterialToken.mintMaterial(..., evidenceFile.fileHash)
  -> POST /api/materials/{id}/chain
```

브라우저는 AI 서비스를 직접 호출하지 않는다. 인증, 업로드 제한, 내부 서비스 주소와 장애 변환을 Spring Boot 경계에서 통제한다. AI 분석 호출은 사진을 저장하지 않는 일시적 처리다. 영속화는 사용자가 소재 등록을 확정한 뒤 기존 Evidence 경로가 담당한다.

## AI Service Design

### Components

- `image_validation`: 실제 이미지 디코딩 여부, 포맷, 파일 크기, 최소 해상도, 밝기, Laplacian variance를 검사한다.
- `ScaleDetector`: `detect(image) -> Detection | None` 계약을 갖는다. MVP 구현은 에지와 큰 사각 윤곽 및 화면 내 위치를 조합한 휴리스틱이다.
- `DisplayDetector`: 저울 후보 내부에서 가로로 긴 고대비 사각형을 찾아 표시창 후보를 반환한다.
- `image_preprocessor`: 원본 crop, grayscale, CLAHE, adaptive binary, inverted binary 후보를 생성한다. 기울어진 사각형은 four-point perspective transform으로 정면화한다.
- `ocr`: 기존 RapidOCR 엔진을 재사용하되 각 후보를 독립 실행한다.
- `WeightParser`: OCR 텍스트를 제한적으로 보정하고 `g`, `kg`를 gram 정수로 변환한다.
- `WeightRecognitionService`: 각 단계를 조립하고 상태 및 최종 confidence를 계산한다.

### OCR Correction Rules

숫자 또는 단위가 기대되는 위치에서만 다음 문자를 보정한다.

- `O`, `o` -> `0`
- `I`, `l`, `|` -> `1`
- `S`, `s` -> `5`
- `B` -> `8`

소수점을 임의로 삽입하지 않는다. 파서는 `([0-9]+(?:\.[0-9]+)?)\s*(kg|g)` 형태만 수용한다. 후보가 여러 개면 OCR confidence, 단위 포함 여부, 표시창 중심과의 거리 순으로 점수를 계산해 하나를 선택한다.

### Confidence and Status

최종 confidence는 `min(scaleConfidence, displayConfidence, ocrConfidence)`다.

- `confidence >= 0.90`: `SUCCESS`
- `0.70 <= confidence < 0.90`: `LOW_CONFIDENCE`
- `confidence < 0.70`: `LOW_CONFIDENCE`, 무게 자동 적용 버튼은 비활성화하고 재촬영 또는 수동 입력을 안내한다.

단계 실패는 다음 상태로 구분한다.

- `INVALID_IMAGE`: 형식, 크기, 해상도, 밝기 또는 흐림 검사 실패
- `SCALE_NOT_FOUND`: 저울 후보 없음
- `DISPLAY_NOT_FOUND`: 표시창 후보 없음
- `OCR_FAILED`: OCR 텍스트 없음
- `INVALID_WEIGHT_FORMAT`: 텍스트가 무게 규칙과 맞지 않거나 범위 밖
- `LOW_CONFIDENCE`: 후보는 있으나 자동 적용에 추가 확인이 필요
- `SUCCESS`: 정상 후보

### AI API

`POST /v1/vision/weight`, `multipart/form-data`, 파일 파트 이름은 `image`다.

성공 및 저신뢰도 응답:

```json
{
  "status": "SUCCESS",
  "weight": { "value": 2350, "unit": "g" },
  "ocr": { "rawText": "2.35 kg", "confidence": 0.97 },
  "detection": {
    "scaleConfidence": 0.95,
    "displayConfidence": 0.96
  },
  "confidence": 0.95,
  "message": null
}
```

단계 실패 응답은 HTTP 200과 해당 `status`, 사용자용 `message`를 반환한다. 잘못된 multipart 요청이나 서버 내부 오류만 4xx/5xx를 사용한다. 이 구분을 통해 촬영 실패와 서비스 장애를 프론트가 다르게 안내한다.

## Spring Boot Design

### Endpoint

`POST /api/materials/recognize-weight`, `multipart/form-data`, 파일 파트 이름은 `image`다. 기존 소재 API와 같이 인증이 필요하며 Supplier 전용 페이지에서 사용한다. 컨트롤러는 AI 응답 DTO를 `ApiResponse`로 감싸 반환한다.

### Client Boundary

- `app.ai.base-url=${AI_SERVER_BASE_URL:http://localhost:8000}` 설정을 추가한다.
- 전용 `WeightVisionClient`가 Spring `RestClient`로 multipart 요청을 보낸다.
- 연결과 응답 타임아웃을 설정해 AI 장애가 요청 스레드를 무기한 점유하지 않게 한다.
- AI의 정상 도메인 상태는 그대로 반환한다.
- 연결 실패, 타임아웃, 깨진 응답은 `VISION_SERVICE_UNAVAILABLE` 비즈니스 오류로 변환한다.
- 브라우저 업로드와 AI 요청 모두 10MB 상한을 사용한다.

OCR은 소재 등록의 보조 기능이므로 분석 실패가 `POST /api/materials`를 막지 않는다. 사용자는 언제든 사진을 제거하고 수동 수량으로 등록할 수 있다.

## Evidence and Data Model

`evidence_type` 허용값과 양쪽 enum/type에 `WEIGHT_PHOTO`를 추가한다.

- DB schema 및 기존 DB용 migration
- Backend `EvidenceType`
- Frontend `EvidenceType`, 라벨 `저울 측정 사진`
- Material evidence 선택 목록

저울 사진은 소재가 생성되기 전에는 영속화하지 않는다. 등록 취소 사진이 저장소에 남는 일을 방지하기 위해서다. 소재 생성 후 기존 `POST /api/evidence`에 `referenceType=MATERIAL`, `referenceId=<created id>`, `evidenceType=WEIGHT_PHOTO`로 업로드한다.

OCR 결과 자체는 DB 정본이 아니다. 정본은 Supplier가 최종 제출한 `material.total_quantity`이며, 사진과 파일 해시는 그 입력의 근거다. OCR 원문과 confidence는 이번 MVP에서 별도 테이블에 저장하지 않고 UI 확인 및 서버 로그에만 사용한다.

## Frontend Design

수량 필드 옆에 독립된 `WeightPhotoRecognition` 컴포넌트를 둔다. 컴포넌트는 파일 선택, 미리보기, 분석 요청, 상태 메시지, 결과 적용과 사진 제거를 책임진다. `MaterialForm`은 적용된 gram 값을 kg로 변환해 `setValue('quantityKg', ..., { shouldValidate: true, shouldDirty: true })`로 기존 필드에 넣고, 등록 단계에서 선택 파일을 Evidence 업로드에 넘긴다.

화면 상태는 다음을 명시적으로 구분한다.

- 파일 미선택
- 분석 중
- 성공: 원문, 환산 무게, confidence, `적용` 버튼
- 저신뢰도: 같은 정보와 확인 경고
- 재촬영 필요: 실패 메시지와 파일 다시 선택
- 등록 중 증빙 업로드
- 증빙 업로드 실패 및 재시도

`SUCCESS`는 적용 버튼을 활성화한다. `LOW_CONFIDENCE` 중 0.70 이상은 경고와 함께 적용 가능하고, 0.70 미만은 적용할 수 없다. 어떠한 경우에도 OCR 응답만으로 수량이 자동 변경되지 않는다.

## Failure and Retry Semantics

- OCR 실패: 선택 사진을 유지하며 수동 입력을 허용한다.
- AI 서비스 장애: `잠시 후 다시 시도하거나 수량을 직접 입력해주세요`를 표시한다.
- 소재 생성 실패: 증빙 업로드와 온체인 발행을 시작하지 않는다.
- 증빙 업로드 실패: 생성된 소재와 요청, 사진을 유지하고 온체인 발행을 시작하지 않는다.
- 지갑 서명 거절 또는 온체인 실패: 기존 `pendingRegistration` 방식으로 생성된 소재를 재사용한다. 이미 업로드된 증빙 응답도 함께 유지해 재업로드하지 않는다.
- 페이지 새로고침 뒤에는 메모리 상태가 사라진다. 생성됐지만 온체인 연결이 끝나지 않은 소재는 기존 보유 소재/상세 화면의 재발행 경로로 복구하며, 증빙은 이미 저장된 경우 상세 화면에 남는다.

## Security and Privacy

- OCR 프록시는 JWT 인증을 요구한다.
- 업로드된 바이트를 로그에 남기지 않는다.
- 로그에는 상태, 처리 시간, confidence만 남기고 OCR 원문의 개인정보 가능성을 고려해 원문 전체를 기록하지 않는다.
- AI 분석 요청은 저장하지 않는다.
- 영속 사진 조회는 기존 Evidence 접근 정책을 그대로 적용한다.
- 확장자만 믿지 않고 실제 이미지 디코딩과 콘텐츠 타입을 함께 검증한다.

## Testing Strategy

### AI

- parser: `2.35kg`, `2350 g`, 허용 문자 보정, 범위 경계, 잘못된 단위와 소수점
- validation: 빈 파일, 잘못된 포맷, 작은 해상도, 어두움, 과노출, 흐림
- detector/preprocessor: 합성 이미지 fixture로 표시창 선택과 perspective transform
- service: 각 단계 실패 상태, 다중 전처리 중 최고 후보, 최솟값 confidence
- API: multipart 계약과 모든 응답 상태

OCR 엔진은 느린 외부 경계로 대체하되 파서, 전처리, 서비스 조립은 실제 구현을 테스트한다. 최소 한 개의 작은 이미지 fixture로 전체 이미지 처리 통합 테스트를 둔다.

### Backend

- 인증 없는 요청 거절
- multipart 파일명과 바이트를 AI client에 전달
- AI 도메인 상태 보존
- 타임아웃/연결 실패를 서비스 불가 오류로 변환
- `WEIGHT_PHOTO` 증빙 업로드 허용 및 기존 Evidence 정책 유지

### Frontend

- 지원하지 않는 파일과 10MB 초과 파일을 요청 전에 거절
- 성공 결과가 사용자의 `적용` 전에는 수량을 바꾸지 않음
- 적용 시 gram을 kg 입력으로 정확히 변환
- 저신뢰도 경고와 임계값별 적용 가능 여부
- OCR 실패 후 수동 등록 가능
- 소재 생성 후 같은 파일을 `WEIGHT_PHOTO`로 업로드
- 증빙 업로드 성공 뒤 그 `fileHash`를 mint에 전달
- 증빙 업로드 실패 시 mint 미호출 및 재시도에서 소재 중복 생성 방지
- 온체인 재시도에서 증빙 중복 업로드 방지

## Acceptance Criteria

- Supplier는 소재 등록 화면에서 디지털 저울 사진을 선택해 무게 후보를 받을 수 있다.
- OCR 결과는 명시적 적용 전에는 등록 수량을 변경하지 않는다.
- 최종 저장 수량은 기존과 동일하게 gram 정수다.
- OCR 사진을 사용한 등록은 해당 사진이 `WEIGHT_PHOTO` 증빙으로 저장된다.
- 온체인 발행에는 placeholder가 아니라 저장된 사진의 실제 `fileHash`가 전달된다.
- 사진 또는 AI 문제는 수동 등록 경로를 막지 않는다.
- AI 서버 장애, 촬영 실패, 저신뢰도 결과가 서로 구분되어 안내된다.
- 기존 계근표 OCR, 소재 등록, 증빙 조회/업로드 흐름의 회귀 테스트가 통과한다.
