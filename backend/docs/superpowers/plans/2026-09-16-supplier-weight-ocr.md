# Supplier Weight OCR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Supplier 소재 등록 화면에서 디지털 저울 사진의 무게를 제안하고, 확정 등록 시 사진을 `WEIGHT_PHOTO` 증빙으로 저장해 실제 파일 해시로 온체인 발행한다.

**Architecture:** React는 인증된 Spring Boot 프록시로 사진을 보내고, Spring Boot는 FastAPI의 이미지 검증·휴리스틱 탐지·RapidOCR 파이프라인을 호출한다. OCR 값은 사용자가 적용해야 기존 `quantityKg` 필드에 들어가며, 소재 생성 후 같은 파일을 Evidence API에 저장한 뒤 그 `fileHash`를 mint에 전달한다.

**Tech Stack:** Python 3.12, FastAPI, RapidOCR ONNX, OpenCV, Pillow, pytest, Java 21, Spring Boot 3.5, RestClient, JUnit/MockMvc, React 19, TypeScript 6, React Hook Form, Axios, Vitest/Testing Library

**Spec:** `docs/superpowers/specs/2026-09-16-supplier-weight-ocr-design.md`

## Global Constraints

- 지원 이미지는 JPG, JPEG, PNG이며 파일당 최대 10MB다.
- 최소 해상도는 가로·세로 중 긴 변 640px 이상, 짧은 변 480px 이상이다.
- 지원 무게는 1g 이상 100kg 이하다.
- DB와 온체인에 전달하는 무게는 gram 정수다.
- OCR 응답만으로 수량을 자동 변경하지 않는다.
- `confidence >= 0.90`은 `SUCCESS`, `0.70 <= confidence < 0.90`은 확인 가능한 `LOW_CONFIDENCE`, `confidence < 0.70`은 적용 불가능한 `LOW_CONFIDENCE`다.
- OCR 사진을 사용한 등록은 사진 저장 성공 뒤 실제 `fileHash`로 mint한다.
- OCR 사진이 없는 기존 수동 등록 흐름은 유지한다.
- 기존 `/v1/ocr`과 `/v1/evidence/weight` 계약을 변경하지 않는다.

---

### Task 1: AI 무게 파서와 응답 계약

**Files:**
- Create: `../S15P21E104_ai/app/vision/__init__.py`
- Create: `../S15P21E104_ai/app/vision/schemas.py`
- Create: `../S15P21E104_ai/app/vision/weight_parser.py`
- Create: `../S15P21E104_ai/tests/test_vision_weight_parser.py`

**Interfaces:**
- Produces: `parse_weight(text: str) -> ParsedWeight | None`
- Produces: `VisionStatus`, `WeightRecognitionResponse`, `ParsedWeight`
- `ParsedWeight` fields: `gram: int`, `raw_text: str`, `normalized_text: str`

- [ ] **Step 1: Write failing parser tests**

```python
import pytest
from app.vision.weight_parser import parse_weight

@pytest.mark.parametrize(("text", "gram"), [
    ("2.35 kg", 2350), ("2350g", 2350), ("0.85 KG", 850),
    ("2.3S kg", 2350), ("SOO g", 500),
])
def test_parse_supported_weight(text, gram):
    assert parse_weight(text).gram == gram

@pytest.mark.parametrize("text", ["2.35", "12 lb", "0 g", "100.001 kg", "2..35 kg"])
def test_rejects_unsupported_or_out_of_range_weight(text):
    assert parse_weight(text) is None
```

- [ ] **Step 2: Verify RED**

Run: `python -m pytest tests/test_vision_weight_parser.py -q`

Expected: collection fails because `app.vision.weight_parser` does not exist.

- [ ] **Step 3: Implement the minimal parser and schemas**

Use `Decimal`, not `float`, and accept only the full weight token matched by `(?P<value>[0-9]+(?:\.[0-9]+)?)\s*(?P<unit>kg|g)` after position-limited character correction. Normalize with `ROUND_HALF_UP`, then reject values outside `1..100_000` gram.

```python
class VisionStatus(str, Enum):
    INVALID_IMAGE = "INVALID_IMAGE"
    SCALE_NOT_FOUND = "SCALE_NOT_FOUND"
    DISPLAY_NOT_FOUND = "DISPLAY_NOT_FOUND"
    OCR_FAILED = "OCR_FAILED"
    INVALID_WEIGHT_FORMAT = "INVALID_WEIGHT_FORMAT"
    LOW_CONFIDENCE = "LOW_CONFIDENCE"
    SUCCESS = "SUCCESS"
```

- [ ] **Step 4: Verify GREEN**

Run: `python -m pytest tests/test_vision_weight_parser.py -q`

Expected: all parser tests pass.

- [ ] **Step 5: Commit in AI repository**

```bash
git add app/vision tests/test_vision_weight_parser.py
git commit -m "feat: 저울 OCR 무게 파서 추가"
```

### Task 2: AI 이미지 검증, 탐지, 전처리

**Files:**
- Create: `../S15P21E104_ai/app/vision/image_validation.py`
- Create: `../S15P21E104_ai/app/vision/detection.py`
- Create: `../S15P21E104_ai/app/vision/preprocessing.py`
- Create: `../S15P21E104_ai/tests/test_vision_image_pipeline.py`
- Modify: `../S15P21E104_ai/requirements.txt`

**Interfaces:**
- Produces: `validate_image(data: bytes) -> np.ndarray`
- Produces: `HeuristicScaleDetector.detect(image) -> Detection | None`
- Produces: `HeuristicDisplayDetector.detect(scale_image) -> Detection | None`
- Produces: `crop_detection(image, detection) -> np.ndarray`
- Produces: `preprocess_variants(display_image) -> list[tuple[str, np.ndarray]]`
- `Detection` fields: `bbox: tuple[int, int, int, int]`, `confidence: float`

- [ ] **Step 1: Add failing image-pipeline tests with generated fixtures**

Create NumPy images in tests rather than checking in large binaries. Cover valid 800x600 image, short-side 479 rejection, all-black rejection, flat/blurred rejection, lower-half scale rectangle, inner 4:1 display rectangle, missing scale, missing display, and exactly five named preprocessing variants.

```python
def test_display_detector_finds_high_contrast_wide_rectangle():
    image = synthetic_scale_image(width=800, height=600)
    scale = HeuristicScaleDetector().detect(image)
    display = HeuristicDisplayDetector().detect(crop_detection(image, scale))
    assert display is not None
    assert display.confidence >= 0.7
```

- [ ] **Step 2: Verify RED**

Run: `python -m pytest tests/test_vision_image_pipeline.py -q`

Expected: collection fails because the modules do not exist.

- [ ] **Step 3: Implement validation and OpenCV pipeline**

Add `opencv-python-headless==4.12.0.88`. Decode with `cv2.imdecode`; validate dimensions, mean brightness in `25..235`, and Laplacian variance `>= 35`. Detect scale candidates from Canny/closed contours with area ratio, rectangularity, lower-frame position, and aspect score. Detect display candidates inside the scale crop with aspect ratio `2..8`, contrast, rectangularity, and central/lower position. Generate `original`, `gray`, `clahe`, `adaptive`, `adaptive_inverted` variants, applying a four-point transform when a quadrilateral exists.

- [ ] **Step 4: Verify GREEN**

Run: `python -m pytest tests/test_vision_image_pipeline.py -q`

Expected: all image pipeline tests pass without loading RapidOCR.

- [ ] **Step 5: Commit in AI repository**

```bash
git add requirements.txt app/vision tests/test_vision_image_pipeline.py
git commit -m "feat: 저울 표시창 탐지와 이미지 전처리 추가"
```

### Task 3: AI 인식 서비스와 FastAPI endpoint

**Files:**
- Create: `../S15P21E104_ai/app/vision/service.py`
- Create: `../S15P21E104_ai/tests/test_vision_service.py`
- Modify: `../S15P21E104_ai/app/main.py`
- Modify: `../S15P21E104_ai/tests/test_main.py`

**Interfaces:**
- Consumes: Task 1 parser/schemas and Task 2 detector/preprocessor contracts
- Produces: `WeightRecognitionService.recognize(data: bytes) -> WeightRecognitionResponse`
- Produces: `POST /v1/vision/weight`, multipart part `image`

- [ ] **Step 1: Write failing orchestration tests**

Inject detector and OCR callables. Assert every failure maps to its exact status, the best OCR candidate is selected, final confidence is the minimum of the three stages, 0.89 maps to `LOW_CONFIDENCE`, and 0.90 maps to `SUCCESS`.

```python
def test_final_confidence_is_pipeline_minimum(service_factory):
    result = service_factory(scale=.96, display=.91, text="2.35 kg", ocr=.94).recognize(VALID_JPEG)
    assert result.status == VisionStatus.SUCCESS
    assert result.confidence == .91
    assert result.weight.value == 2350
```

- [ ] **Step 2: Verify RED**

Run: `python -m pytest tests/test_vision_service.py tests/test_main.py -q`

Expected: imports or endpoint assertions fail because the service and route do not exist.

- [ ] **Step 3: Implement service and route**

Reuse `ocr.get_engine()` and convert each preprocessed array to the engine input. Flatten OCR lines into candidate strings while retaining each line score. Domain failures return HTTP 200 response models; malformed upload retains FastAPI 4xx behavior; unexpected exceptions remain HTTP 500 with server-side logging and no image bytes/raw OCR text.

```python
@app.post("/v1/vision/weight", response_model=WeightRecognitionResponse)
async def recognize_weight(image: UploadFile = File(...)) -> WeightRecognitionResponse:
    data = await _read_vision_upload(image)
    return get_weight_recognition_service().recognize(data)
```

- [ ] **Step 4: Verify GREEN and regression**

Run: `python -m pytest -q`

Expected: new vision tests and existing OCR/weight tests all pass.

- [ ] **Step 5: Commit in AI repository**

```bash
git add app tests
git commit -m "feat: 저울 무게 인식 API 추가"
```

### Task 4: Backend `WEIGHT_PHOTO` evidence type

**Files:**
- Modify: `src/main/java/com/ssafy/upcycle/evidence/entity/EvidenceType.java`
- Modify: `src/main/resources/schema.sql`
- Create: `src/main/resources/db/migrations/2026-09-16-weight-photo-evidence.sql`
- Modify: `src/test/java/com/ssafy/upcycle/evidence/entity/EvidenceSchemaMappingTests.java`
- Modify: `src/test/java/com/ssafy/upcycle/evidence/controller/EvidenceUploadControllerTests.java`

**Interfaces:**
- Produces: `EvidenceType.WEIGHT_PHOTO`
- Migration adds `WEIGHT_PHOTO` to the MySQL ENUM without removing or reordering existing values.

- [ ] **Step 1: Add failing enum/schema/upload tests**

Assert `EvidenceType.valueOf("WEIGHT_PHOTO")`, schema validation with the updated enum, and multipart upload using `evidenceType=WEIGHT_PHOTO` returns success.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -Dtest=EvidenceSchemaMappingTests,EvidenceUploadControllerTests test`

Expected: compilation or request assertion fails because `WEIGHT_PHOTO` is absent.

- [ ] **Step 3: Add enum and migration**

Use:

```sql
ALTER TABLE evidence_file
    MODIFY COLUMN evidence_type ENUM(
        'BEFORE_PHOTO','AFTER_PHOTO','WEIGHT_SLIP','WEIGHT_PHOTO',
        'INVOICE','PROCESS_PHOTO','ETC'
    ) NOT NULL;
```

Mirror the same value list in `schema.sql` and add `WEIGHT_PHOTO` after `WEIGHT_SLIP` in Java.

- [ ] **Step 4: Verify GREEN**

Run: `./mvnw -Dtest=EvidenceSchemaMappingTests,EvidenceUploadControllerTests test`

Expected: selected tests pass.

- [ ] **Step 5: Commit in backend repository**

```bash
git add src/main/java/com/ssafy/upcycle/evidence src/main/resources/schema.sql src/main/resources/db/migrations src/test/java/com/ssafy/upcycle/evidence
git commit -m "feat: 저울 측정 사진 증빙 타입 추가"
```

### Task 5: Backend Vision client and authenticated proxy

**Files:**
- Create: `src/main/java/com/ssafy/upcycle/vision/VisionErrorCode.java`
- Create: `src/main/java/com/ssafy/upcycle/vision/config/VisionProperties.java`
- Create: `src/main/java/com/ssafy/upcycle/vision/config/VisionConfig.java`
- Create: `src/main/java/com/ssafy/upcycle/vision/dto/WeightRecognitionResponse.java`
- Create: `src/main/java/com/ssafy/upcycle/vision/service/WeightVisionClient.java`
- Create: `src/main/java/com/ssafy/upcycle/vision/controller/WeightVisionController.java`
- Create: `src/test/java/com/ssafy/upcycle/vision/service/WeightVisionClientTests.java`
- Create: `src/test/java/com/ssafy/upcycle/vision/controller/WeightVisionControllerTests.java`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`

**Interfaces:**
- Produces: `POST /api/materials/recognize-weight`, multipart part `image`
- Produces: `WeightVisionClient.recognize(MultipartFile) -> WeightRecognitionResponse`
- Produces config: `app.ai.base-url`, `connect-timeout`, `read-timeout`
- Produces error code: HTTP 503 `VISION_SERVICE_UNAVAILABLE`

- [ ] **Step 1: Write failing client and MockMvc tests**

Use `MockRestServiceServer` with a `RestClient.Builder` to prove the downstream path, multipart part name/content, JSON mapping, and 503 translation. Use `@WebMvcTest` to prove anonymous requests are 401 and authenticated Supplier requests receive the `ApiResponse` envelope.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -Dtest=WeightVisionClientTests,WeightVisionControllerTests test`

Expected: compilation fails because the vision package does not exist.

- [ ] **Step 3: Implement configuration, DTO, client, controller**

Build the multipart request with `MultipartBodyBuilder` and a `ByteArrayResource` whose `getFilename()` returns the sanitized original filename. Catch `RestClientException` and empty response bodies, log status/latency only, and throw `BusinessException(VisionErrorCode.VISION_SERVICE_UNAVAILABLE)`.

```yaml
app:
  ai:
    base-url: ${AI_SERVER_BASE_URL:http://localhost:8000}
    connect-timeout: ${AI_CONNECT_TIMEOUT:PT2S}
    read-timeout: ${AI_READ_TIMEOUT:PT30S}
```

- [ ] **Step 4: Verify GREEN and backend regression**

Run: `./mvnw -Dtest=WeightVisionClientTests,WeightVisionControllerTests test`

Then run: `./mvnw test`

Expected: selected tests and full backend suite pass.

- [ ] **Step 5: Commit in backend repository**

```bash
git add .env.example src/main/java/com/ssafy/upcycle/vision src/main/resources/application.yml src/test/java/com/ssafy/upcycle/vision
git commit -m "feat: 저울 OCR AI 프록시 추가"
```

### Task 6: Frontend recognition API and component

**Files:**
- Create: `../S15P21E104_front/src/features/material/types/weightRecognition.types.ts`
- Create: `../S15P21E104_front/src/features/material/api/recognizeWeight.ts`
- Create: `../S15P21E104_front/src/features/material/components/WeightPhotoRecognition.tsx`
- Create: `../S15P21E104_front/src/features/material/components/WeightPhotoRecognition.test.tsx`
- Modify: `../S15P21E104_front/src/features/material/components/material.css`

**Interfaces:**
- Produces: `recognizeWeight(image: File) -> Promise<WeightRecognitionResult>`
- Produces component props: `onApply(weightGram: number): void`, `onFileChange(file: File | null): void`

- [ ] **Step 1: Write failing component tests**

Mock only `recognizeWeight`. Test JPG/PNG acceptance, unsupported type and >10MB client rejection, loading state, success display without automatic application, explicit apply callback, 0.70 low-confidence application with warning, <0.70 disabled application, failure retake message, preview URL cleanup, and remove-file callback.

- [ ] **Step 2: Verify RED**

Run: `npm test -- src/features/material/components/WeightPhotoRecognition.test.tsx`

Expected: module import fails because the component does not exist.

- [ ] **Step 3: Implement types, API and component**

Build `FormData` with `image`; clear Axios's default JSON content type; set timeout to 35 seconds. Use `URL.createObjectURL` for preview and revoke it on replacement/unmount. Render status messages in `role=status` and actionable failures in `role=alert`.

- [ ] **Step 4: Verify GREEN**

Run: `npm test -- src/features/material/components/WeightPhotoRecognition.test.tsx`

Expected: all component tests pass.

- [ ] **Step 5: Commit in frontend repository**

```bash
git add src/features/material
git commit -m "feat: 소재 등록 저울 사진 인식 UI 추가"
```

### Task 7: Frontend registration, evidence and mint integration

**Files:**
- Modify: `../S15P21E104_front/src/features/evidence/types/evidence.types.ts`
- Modify: `../S15P21E104_front/src/features/material/components/MaterialForm.tsx`
- Modify: `../S15P21E104_front/src/features/material/components/MaterialForm.test.tsx`

**Interfaces:**
- Consumes: `WeightPhotoRecognition`, `uploadEvidence`, `EvidenceFile.fileHash`
- Changes pending registration state to include `weightPhoto: File | null` and `weightEvidence: EvidenceFile | null`
- Preserves `CreateMaterialRequest.quantity` as gram integer

- [ ] **Step 1: Extend MaterialForm tests before implementation**

Mock `recognizeWeight` through the child component's real UI, `uploadEvidence`, and mint. Assert:

```typescript
expect(uploadEvidence).toHaveBeenCalledWith(expect.objectContaining({
  referenceType: 'MATERIAL',
  referenceId: 41,
  evidenceType: 'WEIGHT_PHOTO',
  files: [photo],
}))
expect(mint).toHaveBeenCalledWith(expect.objectContaining({ evidenceHash: '0xactualhash' }))
```

Also assert apply converts 2350g to the visible 2.35kg field; upload failure prevents mint; retry does not call `createMaterial` twice; mint retry does not upload evidence twice; and no-photo registration retains the existing placeholder path.

- [ ] **Step 2: Verify RED**

Run: `npm test -- src/features/material/components/MaterialForm.test.tsx`

Expected: new assertions fail because the weight-photo flow is not integrated.

- [ ] **Step 3: Integrate without changing existing registration rules**

Add `WEIGHT_PHOTO` and label `저울 측정 사진`. Place `WeightPhotoRecognition` adjacent to `수량 (kg)`. On apply call `setValue('quantityKg', gramToKg(weightGram), { shouldValidate: true, shouldDirty: true })`. After `createMaterial`, upload selected photo once and use `files[0].fileHash`; if no file, use `evidencePlaceholder(created.materialCode)`. Store both created material and uploaded evidence in retry state before invoking mint.

- [ ] **Step 4: Verify GREEN and frontend regression**

Run: `npm test -- src/features/material/components/MaterialForm.test.tsx src/features/material/components/WeightPhotoRecognition.test.tsx`

Then run: `npm test`

Expected: selected and full frontend suites pass.

- [ ] **Step 5: Commit in frontend repository**

```bash
git add src/features/evidence/types/evidence.types.ts src/features/material/components
git commit -m "feat: 저울 사진을 소재 증빙과 온체인 해시에 연결"
```

### Task 8: Cross-repository verification and documentation

**Files:**
- Modify: `../S15P21E104_ai/README.md`
- Modify: `README.md`
- Modify: `../S15P21E104_front/README.md` only if it contains local setup guidance for feature-specific configuration

**Interfaces:**
- Documents: AI endpoint, `AI_SERVER_BASE_URL`, supported images/statuses, local run and test commands

- [ ] **Step 1: Run AI verification**

Run in AI repository: `python -m pytest -q`

Run: `docker build -f docker/Dockerfile -t circular-fashion/ai:weight-ocr-test .`

Expected: tests pass and image builds successfully.

- [ ] **Step 2: Run backend verification**

Run in backend repository: `./mvnw test`

Run: `./mvnw -DskipTests package`

Expected: tests pass and package exits 0.

- [ ] **Step 3: Run frontend verification**

Run in frontend repository: `npm test`

Run: `npm run lint`

Run: `npm run build`

Expected: tests, lint, typecheck, and Vite build exit 0.

- [ ] **Step 4: Update operator/developer docs**

Document the exact curl request:

```bash
curl -F "image=@scale.jpg" http://localhost:8000/v1/vision/weight
```

Document backend `AI_SERVER_BASE_URL=http://ai:8000`, 10MB limit, supported status values, and the fact that heuristic detection is the MVP implementation behind a replaceable detector interface.

- [ ] **Step 5: Review requirement coverage and diffs**

Run in each repository: `git status --short` and `git diff --check HEAD~1..HEAD` for the final commit, plus `git diff --check` for uncommitted documentation edits. Confirm unrelated pre-existing changes remain untouched.

- [ ] **Step 6: Commit documentation in the owning repositories**

```bash
git add README.md
git commit -m "docs: 저울 OCR 실행 방법 추가"
```

Only create a documentation commit in a repository whose README actually changed.
