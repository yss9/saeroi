# Processing 견적 선택과 결제 설계

## 목표

지정된 Processor가 Processing 요청에 견적을 제출하고, Brand가 견적을 선택해 Job을 만든 뒤 가공비 결제를 완료할 수 있게 한다. 견적 선택 전에 생성된 Material Lock은 같은 행을 Job에 연결해 유지하며, 결제가 15분 안에 끝나지 않으면 Job과 Lock을 자동으로 만료 처리한다.

## 적용 API

### 요청 취소

`PATCH /api/v1/processing-requests/{requestId}/cancel`

- 요청을 만든 활성 Brand만 호출할 수 있다.
- `QUOTING` 상태에서만 취소하고 Request Lock을 `RELEASED`로 전환한다.
- 기존 프론트 호환을 위해 현재 `POST /cancel` 매핑도 같은 서비스 메서드로 유지한다.

### 견적 제출

`POST /api/v1/processing-requests/{requestId}/quotes`

```json
{
  "price": 120000,
  "leadDays": 5,
  "memo": "입고 확인 후 작업을 시작합니다."
}
```

- 요청에 지정된 활성 Processor만 제출할 수 있다.
- `price`는 0 이상의 원 단위 정수, `leadDays`는 양의 정수, `memo`는 선택값이다.
- 요청이 `QUOTING`이고 `expiresAt` 이전일 때만 제출할 수 있다.
- `(processing_request_id, processor_id)` 유일 제약에 따라 한 업체는 요청당 견적 하나만 제출한다. 중복 제출은 `409 Conflict`이며 수정으로 취급하지 않는다.
- 성공 시 `201 Created`와 견적 단건을 반환한다.

### 견적 목록

`GET /api/v1/processing-requests/{requestId}/quotes`

- 요청을 만든 활성 Brand만 조회할 수 있다.
- `quoteId`, `requestId`, `processorId`, `processorName`, `price`, `leadDays`, `memo`, `status`, `createdAt`을 배열로 반환한다.
- 현재 요청은 Processor 한 곳을 지정하므로 결과는 최대 한 건이지만, API 계약은 배열을 유지한다.

### 요청 상세

`GET /api/v1/processing-requests/{requestId}`

- 요청을 만든 Brand와 지정된 Processor만 조회할 수 있다.
- 요청 코드, 양쪽 당사자, 소재 코드·이름·유형, 요청 수량(g), 가공 목적 순서, 상태, 생성·만료 시각을 반환한다.
- 다른 인증 사용자는 `403 Forbidden`, 없는 요청은 `404 Not Found`를 받는다.

### 견적 선택과 Job 생성

`POST /api/v1/quotes/{quoteId}/select`

```json
{
  "paymentMethod": "KAKAO_PAY"
}
```

- 요청을 만든 활성 Brand만 선택할 수 있다.
- `paymentMethod`는 `MOCK` 또는 `KAKAO_PAY`다.
- 요청과 견적이 선택 가능하고 요청 만료 전일 때만 처리한다.
- 양쪽 사용자의 현재 활성 지갑 주소를 Job에 스냅샷으로 저장한다. 어느 한쪽이라도 활성 지갑이 없으면 Job을 만들지 않는다.
- 요청을 `SELECTED`, 견적을 `SELECTED`로 바꾸고 `processing_job`과 `processing_input`을 생성한다.
- Job은 `fee=quote.price`, `paymentAmount=0`, `status=CREATED`로 시작한다.
- Job 생성 시각으로부터 정확히 15분 뒤를 `payment_expires_at`으로 저장한다.
- Request를 참조하던 활성 Processing Lock은 새로 만들거나 해제하지 않고 `MaterialLock.attachToProcessingJob`으로 같은 행을 Job에 연결한다. `processing_input.material_lock_id`도 그 행을 가리킨다.
- 요청·견적·Job·Input·Lock 변경은 한 DB 트랜잭션에서 처리하며 요청, 견적, Lock을 잠금 조회해 중복 선택을 막는다.
- 성공 시 `201 Created`와 Job 상세를 반환한다.
- 온체인 Lock과 트랜잭션 기록은 별도 블록체인 담당 구현이 이 선택 경계에 연결한다. 이번 변경은 온체인 성공을 가장하지 않고 DB 상태와 연결 대상을 제공한다.

## 가공비 결제

### 결제 준비

`POST /api/v1/processing-jobs/{jobId}/payment`

- Job의 Brand 소유자만 호출할 수 있으며 요청 본문은 없다.
- Job이 `CREATED`이고 `paymentExpiresAt` 이전일 때만 가능하다.
- Job의 `fee`, `paymentMethod`, `jobCode`, Brand ID를 사용해 기존 `PaymentGateway`에 결제 준비를 요청한다.
- 재호출은 새 `tid`로 기존 미승인 결제 준비 정보를 교체한다.

```json
{
  "jobId": 15,
  "tid": "payment-reference",
  "redirectUrl": "https://payment.example/redirect",
  "amount": 120000,
  "live": true,
  "paymentExpiresAt": "2026-09-07T16:45:00"
}
```

### 결제 승인

`POST /api/v1/processing-jobs/{jobId}/payment/confirm`

```json
{
  "pgToken": "PG가 반환한 인증 토큰"
}
```

- Job의 Brand 소유자만 호출할 수 있다.
- Job이 `CREATED`이고 결제 준비의 `tid`가 있으며 `paymentExpiresAt` 이전일 때만 가능하다.
- 클라이언트에게 `tid`나 승인 금액을 받지 않는다.
- PG 승인 결과 금액이 Job의 `fee`와 일치할 때만 `PAID`로 전환하고 `paymentReference`, `paymentAmount`를 기록한다.
- 중복 승인과 만료 후 승인은 `409 Conflict`다.
- PG 승인 실패 시 트랜잭션을 롤백하며 Job과 Lock은 `CREATED`/`ACTIVE`로 남아 만료 시각까지 재시도할 수 있다.

## Job 조회와 자동 결제 만료

`GET /api/v1/processing-jobs/{jobId}`는 결제 API가 아니라 작업 상세 API다. Job의 Brand와 Processor만 조회할 수 있으며 결제 상태 복원을 위해 `paymentMethod`, `paymentAmount`, `paymentExpiresAt`도 포함한다.

별도의 공개 결제 취소 API는 두지 않는다. 결제를 포기한 사용자의 Job은 최대 15분 동안 Lock을 유지한다.

- `processing_job.payment_expires_at DATETIME NOT NULL`을 추가한다.
- `processing_job.status`에 `EXPIRED`를 추가한다.
- `(status, payment_expires_at)` 인덱스를 추가한다.
- 스케줄러는 제한된 배치로 `CREATED`이면서 결제 기한이 지난 Job을 조회한다.
- 각 Job을 잠금 조회한 뒤 여전히 만료 대상이면 `EXPIRED`로 전환하고 Job에 연결된 활성 Material Lock을 `EXPIRED`로 전환한다.
- `PAID` 이상의 Job은 자동 결제 만료 대상이 아니다.
- 결제 승인과 만료가 경합하면 같은 Job 행 잠금과 상태 조건으로 하나만 성공한다.
- 온체인 Lock이 이미 생성된 경우의 온체인 해제는 블록체인 담당 구현이 이 만료 상태 전이에 연결한다.

## 오류와 상태 규칙

- 권한 없음: `403 Forbidden`
- 요청·견적·Job 없음: `404 Not Found`
- 유효성 오류: `400 Bad Request`
- 중복 견적, 이미 선택된 견적, 잘못된 상태, 만료 후 제출·선택·결제: `409 Conflict`
- 활성 지갑 없음: `409 Conflict`
- PG 승인 금액 불일치: `409 Conflict`, Job 상태 유지

## 구현 구조

- `ProcessingQuote`, `ProcessingJob`, `ProcessingInput` JPA Entity와 Repository가 테이블과 상태 전이를 담당한다.
- 견적 명령, 요청·견적 조회, 견적 선택/Job 생성, 결제, Job 조회, 결제 만료 Worker를 책임별 Service로 나눈다.
- 기존 Trade 결제의 `PaymentGateway`와 결제 명령/응답 타입을 재사용하되 MaterialTrade용 `PaymentService`에는 Processing 분기를 넣지 않는다.
- HTTP 통합 테스트는 실제 H2 Repository와 Mock PaymentGateway로 권한, 유효성, 중복, 만료, 금액 대조, Lock 연결·해제를 검증한다.
- 동시 선택과 결제 승인/만료 경합은 잠금 조회를 사용하는 서비스 테스트로 검증한다.
