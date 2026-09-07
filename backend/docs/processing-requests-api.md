# Processing 요청 API

## 요청 목록

`GET /processing-requests` (`/api/v1/processing-requests`도 지원) — AUTH

인증된 활성 사용자의 역할에 따라 관계있는 요청을 최신순으로 반환한다. BRAND는 자신이 생성한 요청, PROCESSOR는 자신에게 지정된 요청을 조회한다. 그 밖의 인증 역할은 관계있는 Processing 요청이 없으므로 빈 목록을 받는다.

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `status` | 아니오 | QUOTING, SELECTED, CANCELED, REJECTED, EXPIRED |
| `page` | 아니오 | 0부터 시작, 기본 0 |
| `size` | 아니오 | 1~50, 기본 20 |

응답 `ApiResponse.data` 예시:

```json
{
  "items": [{
    "requestId": 10,
    "requestCode": "PR0123456789abcdef0123456789ab",
    "brandId": 1,
    "brandName": "새로이 스튜디오",
    "processorId": 2,
    "processorName": "리뉴 텍스타일",
    "materialId": 3,
    "materialCode": "MAT-0001",
    "materialName": "폐데님",
    "materialType": "DENIM",
    "quantity": 700,
    "processTypes": ["CLEANING", "CUTTING"],
    "status": "QUOTING",
    "createdAt": "2026-09-07T02:00:00",
    "expiresAt": "2026-09-14T02:00:00"
  }],
  "total": 1,
  "page": 0,
  "size": 20
}
```

페이지는 요청 단위로 계산하므로 복수 가공 목적이 있어도 한 요청이 여러 건으로 계산되지 않는다. 잘못된 상태·페이지·크기는 `PROCESSING_INVALID_QUERY`(400)를 반환한다.

## 요청 생성

`POST /processing-requests` (`/api/v1/processing-requests`도 지원)

Bearer JWT가 필요하다. JWT `sub`의 DB 사용자가 활성 BRAND여야 한다. 요청 본문으로 소유자를 지정할 수 없다.

```json
{
  "materialId": 1,
  "processorId": 2,
  "quantity": 700,
  "processTypes": ["CLEANING", "CUTTING"]
}
```

- 요청당 소재 한 종류, 업체 한 곳을 지정한다.
- `quantity`: JSON 정수, 1부터 9223372036854775807까지, 단위 g. 소수(`1.0` 포함), 문자열, 0, 음수는 거부한다.
- `processTypes`: 중복 없는 1~8개. 전달 순서를 `sequence_no`에 저장한다.
- 목적: CLEANING, DISASSEMBLY, SORTING, CUTTING, SHREDDING, COLOR_PROCESSING, SURFACE_FINISHING, REMANUFACTURING.
- 업체는 활성 PROCESSOR여야 하며 해당 Material Type에서 모든 목적을 지원하고, 각 목적의 최소·최대 수량 범위에 요청 수량이 들어가야 한다.
- 보유량은 `material_balance`의 블록체인 동기화 스냅샷을 사용한다. 다른 도메인을 포함한 **모든 ACTIVE Lock**을 차감한다. 실시간 RPC 조회나 블록체인 동기화 워커를 새로 구현한 것은 아니다.

성공: HTTP **201**, 기존 `ApiResponse`의 `data`는 다음 형태다.

```json
{
  "requestId": 10,
  "requestCode": "PR0123456789abcdef0123456789ab",
  "status": "QUOTING",
  "materialId": 1,
  "processorId": 2,
  "quantity": 700,
  "processTypes": ["CLEANING", "CUTTING"],
  "createdAt": "2026-09-07T02:00:00",
  "expiresAt": "2026-09-14T02:00:00"
}
```

시각은 기존 Clock 빈의 UTC 기준이다. 만료 시각은 생성 시각에 정확히 7일을 더한다. 요청/input/목적/Lock은 한 트랜잭션에서 생성한다. 실제 보유량과 토큰은 차감하지 않는다. HTTP 재시도마다 별도 요청을 만들며, 가용량 안에서만 생성된다(별도 멱등성 키 계약 없음).

## 취소·거절·만료

| API | 권한 | 결과 |
| --- | --- | --- |
| `POST /processing-requests/{requestId}/cancel` | 요청 소유 BRAND | CANCELED, Lock RELEASED |
| `POST /processing-requests/{requestId}/reject` | 지정 PROCESSOR | REJECTED, Lock RELEASED |

두 경로 모두 `/api/v1` 접두어도 지원한다. HTTP 200의 `data`는 결과 상태 문자열이다. 동일 작업 재시도는 성공하며, 다른 종료 상태로 바꾸거나 SELECTED 요청을 취소/거절하면 409다. 이미 마감이 지난 요청은 EXPIRED로 처리한다.

만료 워커는 기본 60초마다 마감이 지난 QUOTING 요청을 최대 100개씩 처리한다. 요청과 Lock을 각각 EXPIRED로 바꾸며 보유량은 변경하지 않는다. 여러 서버가 실행해도 요청 행 잠금 후 상태를 다시 확인한다. 실패한 요청은 다음 실행에서 재시도한다. 배치 지연 중 ACTIVE Lock은 보수적으로 계속 차감되므로 가용량 복구는 배치 처리 이후다.

- `app.processing.expiry.enabled=false`: 자동 만료 워커 비활성화(테스트용).
- `app.processing.expiry.interval-ms`: 배치 실행 간격, 기본 60000.

## 동시성 및 후속 견적·Job 연동

요청 생성은 `(material_id, owner_id)` 잔고 행에 쓰기 잠금을 잡은 뒤 ACTIVE Lock을 현재 읽기로 조회한다. 향후 Marketplace/Production 예약 생성 코드도 같은 잔고 행을 먼저 잠가야 여러 도메인 사이에서 초과 예약을 막을 수 있다.

요청 생성 및 해당 요청의 취소·거절·만료를 제공한다. 업체 검색·예상 기간과 Brand 요청 화면은 후속 구현되었으며 [업체 검색 API 문서](processor-search-api.md)를 참고한다. 견적 제출·목록·선택, Job 생성과 온체인 전송 API는 포함하지 않는다. **Brand 견적 확정이 온체인 기록 시점**이며 요청 생성에서는 호출하지 않는다.

후속 견적 선택 서비스는 요청 행을 잠그고 소유자·QUOTING·만료 여부와 실제 Job 연결을 검증한 뒤, 같은 트랜잭션에서 요청을 SELECTED로 전환하고 `MaterialLock.attachToProcessingJob(requestId, jobId)`를 호출해야 한다. 이 메서드는 동일 Lock ID·수량·ACTIVE 상태를 유지하고 참조만 Job으로 바꾸며, 요청 단계의 만료 시각을 제거한다. 임의 Job ID가 실제 존재하는지의 검증은 견적 선택 서비스 책임이다. 온체인 실패·재시도 처리는 해당 API의 계약과 함께 구현해야 한다.

## 오류

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR / INVALID_REQUEST | 필수값, JSON 타입, 양의 정수 검증 실패(공통 오류 계약) |
| 400 | PROCESSING_INVALID_REQUEST | 중복 목적 등 도메인 입력 오류 |
| 400 | PROCESSING_INVALID_QUERY | 목록 상태·페이지·크기 오류 |
| 400 | PROCESSING_CAPABILITY_MISMATCH | 소재·모든 목적·수량 조건 불일치 |
| 401 | 기존 인증 오류 | 토큰 없음/유효하지 않음 |
| 403 | PROCESSING_FORBIDDEN | 역할, 계정 상태 또는 당사자 불일치 |
| 404 | PROCESSING_MATERIAL_NOT_FOUND | 소재 없음/사용 불가 |
| 404 | PROCESSING_PROCESSOR_NOT_FOUND | 활성 PROCESSOR 없음 |
| 404 | PROCESSING_REQUEST_NOT_FOUND | 요청 없음 |
| 409 | PROCESSING_INSUFFICIENT_BALANCE | 소유 잔고 없음/가용량 부족 |
| 409 | PROCESSING_INVALID_STATE | 허용하지 않는 상태 전환 |

## 기존 DB 적용

신규 DB는 `src/main/resources/schema.sql`을 사용한다. 기존 DB는 `src/main/resources/db/migrations/2026-09-07-processing-request.sql`을 수동 적용해야 한다. 이 프로젝트에는 자동 마이그레이션 실행기가 없다. 이번 작업에서는 실제 DB에 적용하지 않았다.

기존 요청의 업체를 임의로 결정하지 않으므로 마이그레이션 직후 `processor_id`는 nullable이다. 확정된 업무 기록으로 보정한 후 파일에 기재된 NOT NULL 전환을 적용한다. 기존 중복 input/목적도 제약 추가 전에 확인해야 한다. 과거 QUOTING 요청에 업체·Lock·새 마감일을 자동 생성하지 않는다.

정책 우선순위: 이번 대화에서 확정한 업체 한 곳 지정·요청 생성 즉시 Lock·7일 만료가 기존 `POLICY.md`의 견적 선택 시 Lock 설명을 대체한다.
