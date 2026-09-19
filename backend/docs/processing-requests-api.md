# Processing 요청 API

## 요청 목록

`GET /processing-requests` (`/api/v1/processing-requests`도 지원) — AUTH

인증된 활성 사용자의 역할에 따라 관계있는 요청을 최신순으로 반환한다. BRAND는 자신이 생성한 요청, PROCESSOR는 자신에게 지정된 요청을 조회한다. 그 밖의 인증 역할은 관계있는 Processing 요청이 없으므로 빈 목록을 받는다.

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `status` | 아니오 | REQUESTED, ACCEPTED, CANCELED, REJECTED, EXPIRED |
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
    "status": "REQUESTED",
    "createdAt": "2026-09-07T02:00:00",
    "expiresAt": "2026-09-07T02:15:00"
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
- 업체는 활성 PROCESSOR여야 하며 해당 Material Type에서 **요청한 목적 전부**를 지원해야 한다. 하나라도 빠지면 `PROCESSING_CAPABILITY_MISMATCH`(400)다 — 반쯤 맡기면 브랜드는 나머지를 어디에 맡겨야 하는지 모른 채 돈을 낸다.
- **수량 상한은 없다.** 많이 맡기면 오래 걸릴 뿐 거절되지 않는다 (소요일 = ⌈수량 ÷ 하루 처리량⌉).
- 양쪽 모두 활성 지갑이 있어야 한다. 요청이 곧 작업을 만들고, 작업은 그 시점의 지갑 주소를 고정한다 — 없으면 `PROCESSING_WALLET_NOT_FOUND`(409).
- **금액과 결제 수단은 받지 않는다.** 서버가 가공사의 kg 당 단가로 계산해 확정하고, 결제 수단은 붙어 있는 게이트웨이를 그대로 적는다.
- 보유량은 `material_balance`의 블록체인 동기화 스냅샷을 사용한다. 다른 도메인을 포함한 **모든 ACTIVE Lock**을 차감한다. 실시간 RPC 조회나 블록체인 동기화 워커를 새로 구현한 것은 아니다.

성공: HTTP **201**, 기존 `ApiResponse`의 `data`는 다음 형태다.

```json
{
  "requestId": 10,
  "requestCode": "PR0123456789abcdef0123456789ab",
  "status": "REQUESTED",
  "materialId": 1,
  "processorId": 2,
  "quantity": 700,
  "processTypes": ["CLEANING", "CUTTING"],
  "jobId": 44,
  "fee": 1400,
  "paymentMethod": "KAKAO_PAY",
  "paymentExpiresAt": "2026-09-07T02:15:00",
  "createdAt": "2026-09-07T02:00:00",
  "expiresAt": "2026-09-07T02:15:00"
}
```

**요청이 곧 작업을 만든다.** 견적 단계가 사라지면서 `POST /quotes/{id}/select`가 없어졌고, 작업을 만드는 유일한 입구가 이 엔드포인트다. 응답의 `jobId`·`fee`·`paymentExpiresAt`으로 화면이 곧장 결제(`POST /processing-jobs/{jobId}/payment`)로 넘어간다.

`fee`는 서버가 확정한 금액이다 — 공정마다 `⌈kg당 단가 × 수량(g) ÷ 1000⌉`을 구해 더한다. 업체 검색의 `estimatedPrice`가 같은 식을 쓰므로 목록에서 본 금액과 청구 금액이 같다.

시각은 기존 Clock 빈의 UTC 기준이다. **만료 시각은 결제 기한(생성 + 15분)과 같다** — 요청과 작업이 각자 기한을 만들면 결제창은 살아 있는데 요청이 먼저 죽는다. 결제가 승인되면 이 값이 가공사의 수락 기한(승인 + 3일)으로 밀린다. 요청/input/목적/작업/Lock은 한 트랜잭션에서 생성한다. 실제 보유량과 토큰은 차감하지 않는다. HTTP 재시도마다 별도 요청을 만들며, 가용량 안에서만 생성된다(별도 멱등성 키 계약 없음).

## 취소·거절·만료

| API | 권한 | 결과 |
| --- | --- | --- |
| `POST /processing-requests/{requestId}/cancel` | 요청 소유 BRAND | CANCELED, Lock RELEASED, 결제분 환불 |
| `POST /processing-requests/{requestId}/reject` | 지정 PROCESSOR | REJECTED, Lock RELEASED, 결제분 환불 |
| `PATCH /processing-jobs/{jobId}/accept` | 지정 PROCESSOR | 작업 ACCEPTED, 요청 ACCEPTED |
| `PATCH /processing-jobs/{jobId}/reject` | 지정 PROCESSOR | 작업 DECLINED, 요청 REJECTED, 환불 |

수락·거절은 작업 쪽 경로이고, `/processing-requests/{id}/reject`와 같은 곳에 도착한다 — 요청과 작업이 한 몸으로 만들어졌으므로 한 몸으로 끝난다.

⚠️ `PATCH /processing-jobs/{jobId}/reject`(가공사가 **일**을 거절)와 `POST /processing-jobs/{jobId}/reject`(브랜드가 **결과**를 반려)는 경로가 같고 메서드가 다르다. 사유도 각각 `declineReason`·`rejectionReason`으로 나뉜다.

거절에는 사유가 필수다(`{"reason": "..."}`, 500자 이하). 브랜드가 다음에 무엇을 해야 하는지가 사유에 따라 달라지기 때문이다.

**요청 시 결제이므로 거절은 환불을 동반한다.** 결제가 끝난 뒤 종료되면 가공비를 돌려주고 `refundedAt`을 적는다. 모의 게이트웨이(`paymentMethod=MOCK`) 결제는 카카오페이에 존재하지 않으므로 취소 호출을 건너뛴다.

두 경로 모두 `/api/v1` 접두어도 지원한다. HTTP 200의 `data`는 결과 상태 문자열이다. 동일 작업 재시도는 성공하며, 다른 종료 상태로 바꾸거나 ACCEPTED 요청을 취소/거절하면 409다. 이미 마감이 지난 요청은 EXPIRED로 처리한다.

만료 워커가 둘이다. 요청 만료 워커는 마감이 지난 REQUESTED 요청을, 수락 만료 워커는 가공사가 답하지 않은 채 수락 기한이 지난 PAID 작업을 각각 기본 60초마다 최대 100개씩 처리한다. 후자는 환불까지 한다 — 브랜드에게 가장 나쁜 결말은 아무 일도 일어나지 않는 것이다. 요청과 Lock을 각각 EXPIRED로 바꾸며 보유량은 변경하지 않는다. 여러 서버가 실행해도 요청 행 잠금 후 상태를 다시 확인한다. 실패한 요청은 다음 실행에서 재시도한다. 배치 지연 중 ACTIVE Lock은 보수적으로 계속 차감되므로 가용량 복구는 배치 처리 이후다.

- `app.processing.expiry.enabled=false`: 자동 만료 워커 비활성화(테스트용).
- `app.processing.expiry.interval-ms`: 배치 실행 간격, 기본 60000.

## 동시성 및 Job·Lock 연동

요청 생성은 `(material_id, owner_id)` 잔고 행에 쓰기 잠금을 잡은 뒤 ACTIVE Lock을 현재 읽기로 조회한다. 향후 Marketplace/Production 예약 생성 코드도 같은 잔고 행을 먼저 잠가야 여러 도메인 사이에서 초과 예약을 막을 수 있다.

요청 생성이 작업 생성과 소재 예약까지 한 트랜잭션에서 처리한다. 업체 검색은 [업체 검색 API 문서](processor-search-api.md)를 참고한다.

Lock은 요청으로 걸렸다가 같은 트랜잭션 안에서 `MaterialLock.attachToProcessingJob(requestId, jobId)`로 작업에 옮겨 붙는다. 동일 Lock ID·수량·ACTIVE 상태를 유지하고 참조만 Job으로 바꾸며, 기한은 결제 기한이 된다. 결제가 승인되면 기한이 풀린다(`clearExpiryForPaidProcessingJob`).

그래서 종료 처리에서 Lock을 EXPIRED로 적는 것은 **Lock 자체에 기한이 남아 있을 때뿐**이다. 결제가 끝난 작업의 Lock은 기한이 없으므로 RELEASED로 돌아간다 — 시간을 넘긴 것이 아니라 상위 작업이 끝났기 때문이다.

## 오류

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR / INVALID_REQUEST | 필수값, JSON 타입, 양의 정수 검증 실패(공통 오류 계약) |
| 400 | PROCESSING_INVALID_REQUEST | 중복 목적 등 도메인 입력 오류 |
| 400 | PROCESSING_INVALID_QUERY | 목록 상태·페이지·크기 오류 |
| 400 | PROCESSING_CAPABILITY_MISMATCH | 요청한 목적 중 그 소재에 등록되지 않은 것이 있음 |
| 401 | 기존 인증 오류 | 토큰 없음/유효하지 않음 |
| 403 | PROCESSING_FORBIDDEN | 역할, 계정 상태 또는 당사자 불일치 |
| 404 | PROCESSING_MATERIAL_NOT_FOUND | 소재 없음/사용 불가 |
| 404 | PROCESSING_PROCESSOR_NOT_FOUND | 활성 PROCESSOR 없음 |
| 404 | PROCESSING_REQUEST_NOT_FOUND | 요청 없음 |
| 409 | PROCESSING_INSUFFICIENT_BALANCE | 소유 잔고 없음/가용량 부족 |
| 409 | PROCESSING_INVALID_STATE | 허용하지 않는 상태 전환 (미결제 작업 수락, 발송 이후 거절 등) |
| 409 | PROCESSING_WALLET_NOT_FOUND | 브랜드나 가공사의 활성 지갑 없음 |

## 기존 DB 적용

신규 DB는 `src/main/resources/schema.sql`을 사용한다. 기존 DB는 `src/main/resources/db/migrations/2026-09-19-processing-without-quotes.sql`을 **수동으로** 적용해야 한다 — `processing_quote`·`processor_lead_time` 테이블을 지우고 `processor_capability`의 수량 구간을 하루 처리량·단가로 바꾸므로, **진행 중인 가공 작업이 없는 시점**에 적용한다.

기존 `processor_capability` 행의 단가는 옮겨 올 값이 없어 0으로 남는다. 하루 처리량은 옛 `max_quantity`를 물려받는다. 가공사가 다시 입력하기 전까지 그 조합은 0원으로 검색에 노출되므로, 적용 직후 가공사들에게 단가 재등록을 안내해야 한다.

그 이전 마이그레이션은 `src/main/resources/db/migrations/2026-09-07-processing-request.sql`이다. 이 프로젝트에는 자동 마이그레이션 실행기가 없다. 이번 작업에서는 실제 DB에 적용하지 않았다.

기존 요청의 업체를 임의로 결정하지 않으므로 마이그레이션 직후 `processor_id`는 nullable이다. 확정된 업무 기록으로 보정한 후 파일에 기재된 NOT NULL 전환을 적용한다. 기존 중복 input/목적도 제약 추가 전에 확인해야 한다. 과거 QUOTING 요청에 업체·Lock·새 마감일을 자동 생성하지 않는다.

정책 우선순위: 업체 한 곳 지정·요청 생성 즉시 Job·Lock 생성·결제 기한 15분·수락 기한 3일이 기존 `POLICY.md` 의 견적 선택 시 Lock 설명을 대체한다.
