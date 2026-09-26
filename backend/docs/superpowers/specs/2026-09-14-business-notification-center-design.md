# 기업 사용자 알림 센터 설계

## 목적

BRAND, PROCESSOR, SUPPLIER 사용자가 자신이 직접 실행하지 않은 업무 변경을 공통 Header에서 확인한다. 알림은 서버에 저장되어 새로고침과 다른 브라우저에서도 읽음 상태가 유지되며, 페이지 진입 시와 30초마다 갱신된다.

## 범위

- 가공 요청, 견적, 결제, 배송, 작업, 결과 승인·반려
- 소재 거래 생성, 결제, 취소, 배송, 수령
- 제품 주문 관련 상대방 상태 변경
- Header 알림 아이콘, 미확인 개수, 최근 알림 목록, 개별·전체 읽음 처리

현재 Backend에는 제품 주문 조회 API만 있고 제품 주문 생성·결제·배송 명령 API는 없다. 따라서 이번 구현에서는 제품 주문 알림 종류와 연결 규칙을 정의하고 알림 저장 구조를 지원하되, 실제 생성 연결은 해당 명령 API가 추가될 때 같은 `NotificationService`를 호출하도록 한다. 존재하지 않는 제품 주문 동작을 임의로 만들지는 않는다.

이메일, 모바일 푸시, 알림 삭제, 사용자별 알림 설정, WebSocket과 SSE는 포함하지 않는다.

## 선택한 구조

업무 상태 변경과 알림 생성을 같은 DB 트랜잭션에서 처리한다. 상태 변경이 롤백되면 알림도 롤백되며, 업무 처리가 성공했는데 알림만 누락되는 상태를 만들지 않는다.

업무 서비스는 알림 저장 형식을 직접 알지 않고 `NotificationService.notify(...)`를 호출한다. 서비스는 수신자·종류·관련 업무·표시 문구·이동 경로·중복 방지 키를 검증하고 저장한다.

## 데이터 모델

`user_notification` 테이블을 추가한다.

| 컬럼 | 설명 |
|---|---|
| `id` | 알림 식별자 |
| `recipient_id` | 알림 수신 사용자 |
| `type` | 업무 알림 종류 |
| `title` | 목록 제목 |
| `message` | 짧은 설명 |
| `target_path` | 프론트 내부 이동 경로 |
| `reference_type` | PROCESSING_REQUEST, PROCESSING_JOB, MATERIAL_TRADE, PRODUCT_ORDER |
| `reference_id` | 관련 업무 ID |
| `idempotency_key` | 동일 업무 이벤트의 중복 저장 방지 값 |
| `read_at` | 읽은 시각, 미확인은 NULL |
| `created_at` | 생성 시각 |

`idempotency_key`에는 고유 제약을 둔다. 조회를 위해 `(recipient_id, created_at, id)`와 `(recipient_id, read_at)` 인덱스를 둔다. 알림은 append 중심으로 저장하고 읽음 시각만 변경한다.

## 알림 종류와 수신자

본인이 실행한 동작은 본인에게 알리지 않는다.

### 가공

| 업무 이벤트 | 수신자 | 이동 경로 |
|---|---|---|
| Brand가 가공 요청 생성 | 지정 Processor | `/processor/jobs` |
| Processor가 견적 제출 | 요청 Brand | `/brand/processing/requests/{requestId}` |
| Brand가 견적 선택 | 선택된 Processor | `/processing/jobs/{jobId}` |
| Brand가 가공비 결제 완료 | Processor | `/processing/jobs/{jobId}` |
| Brand가 소재 발송 | Processor | `/processing/jobs/{jobId}` |
| Processor가 소재 수령 | Brand | `/processing/jobs/{jobId}` |
| Processor가 가공 시작 | Brand | `/processing/jobs/{jobId}` |
| Processor가 결과 제출 | Brand | `/processing/jobs/{jobId}` |
| Brand가 결과 반려 | Processor | `/processing/jobs/{jobId}` |
| Brand가 결과 승인 | Processor | `/processing/jobs/{jobId}` |
| 요청 취소·거절·만료 | 남은 상대방 | 요청 상세 경로 |

프론트 라우터에 역할별 상세 경로가 다르게 정의되어 있으면 실제 존재하는 경로를 사용한다. 알림 생성 시점에 서버가 내부 웹 경로를 저장하므로 Header는 종류별 경로를 다시 계산하지 않는다.

### 소재 거래

| 업무 이벤트 | 수신자 | 이동 경로 |
|---|---|---|
| Brand가 소재 구매 요청 생성 | 판매 Supplier | `/trades/{tradeId}` |
| Brand가 결제 완료 | 판매 Supplier | `/trades/{tradeId}` |
| 거래 취소 | 상대방 | 역할별 거래 상세 경로 |
| Supplier가 소재 발송 | 구매 Brand | `/trades/{tradeId}` |
| Brand가 수령 확인 | 판매 Supplier | `/trades/{tradeId}` |

### 제품 주문

제품 주문 명령 API가 추가되면 아래 규칙을 적용한다.

| 업무 이벤트 | 수신자 | 이동 경로 |
|---|---|---|
| Consumer가 주문 생성 | 판매 Brand | `/brand/orders/{orderId}` |
| Consumer가 결제 완료 | 판매 Brand | `/brand/orders/{orderId}` |
| 주문 취소 | 판매 Brand | `/brand/orders/{orderId}` |
| Brand가 배송 처리 | 구매 Consumer | `/consumer/orders/{orderId}` |

이번 Header 알림 UI는 기업 역할에서만 노출하므로 Consumer 알림 저장은 제품 주문 기능이 확장될 때 함께 활성화한다.

## API

모든 API는 인증된 사용자만 호출할 수 있고 자신의 알림만 다룬다.

### `GET /notifications?page=0&size=20`

최신순 알림 페이지와 현재 미확인 개수를 반환한다. `size`는 기본 20, 최대 50이다.

```json
{
  "items": [
    {
      "notificationId": 12,
      "type": "PROCESSING_REQUEST_RECEIVED",
      "title": "새 가공 요청이 도착했어요",
      "message": "300g 가공 요청을 확인해 주세요.",
      "targetPath": "/processor/jobs",
      "referenceType": "PROCESSING_REQUEST",
      "referenceId": 41,
      "read": false,
      "createdAt": "2026-09-14T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "unreadCount": 1
}
```

### `PATCH /notifications/{notificationId}/read`

수신자가 자신의 알림을 읽음 처리한다. 이미 읽은 알림에는 같은 결과를 반환하는 멱등 동작으로 처리한다. 다른 사용자의 알림은 존재 여부를 노출하지 않고 404를 반환한다.

### `PATCH /notifications/read-all`

현재 사용자의 미확인 알림을 모두 읽음 처리하고 변경된 개수를 반환한다.

## 프론트엔드 동작

`CompanyDashboardLayout` Header의 계정·지갑 버튼 옆에 알림 아이콘을 둔다.

- 로그인한 BRAND, PROCESSOR, SUPPLIER에게만 조회와 아이콘을 활성화한다.
- 미확인 알림이 있으면 배지를 표시하며 100개 이상은 `99+`로 표시한다.
- 아이콘 클릭 시 최근 알림 20개를 최신순으로 보여준다.
- 알림 클릭 시 먼저 읽음 처리를 요청하고 `targetPath`로 이동한다. 읽음 요청이 실패해도 사용자의 이동은 막지 않는다.
- `모두 읽음`은 패널의 미확인 표시와 Header 배지를 즉시 갱신한다.
- 최초 진입, 30초 간격, 브라우저 탭 재활성화 시 조회한다.
- 패널 로딩·오류·빈 목록 상태를 각각 표시한다. 주기 조회 오류는 페이지 전체를 막지 않는다.
- 키보드 포커스, `aria-label`, `aria-expanded`, Escape 닫기, 바깥 클릭 닫기를 지원한다.

TanStack Query가 서버 상태를 관리한다. query key는 `['notifications', page, size]`이고 읽음 mutation 성공 시 캐시를 갱신하거나 invalidate한다.

## 오류와 일관성

- 알림 입력이 잘못되면 업무 트랜잭션도 실패하도록 하여 알림 없는 성공 상태를 만들지 않는다.
- 동일 이벤트 재시도는 `idempotency_key`로 한 건만 유지한다.
- 수신자가 없거나 본인인 경우 알림을 생성하지 않는다.
- 제목·내용·경로는 서버에서 허용 길이와 내부 경로 형식을 검증한다.
- 알림 조회는 관련 업무의 상세 정보를 다시 조인하지 않는다. 생성 당시 표시용 문구와 경로를 저장해 삭제·상태 변경 후에도 목록을 안정적으로 반환한다.

## 스키마 적용

`schema.sql`에 새 테이블을 추가하고, 기존 Docker MySQL 볼륨에 데이터를 유지하며 적용할 수 있는 수동 migration SQL도 함께 제공한다. 새 Docker 볼륨은 `schema.sql`로 생성하고 기존 볼륨은 migration을 한 번 적용한다.

## 테스트 기준

백엔드 테스트는 다음을 검증한다.

- 목록은 수신자의 알림만 최신순으로 반환한다.
- 미확인 개수, 개별 읽음, 전체 읽음이 정확하다.
- 다른 사용자의 알림을 읽을 수 없다.
- 중복 방지 키로 알림이 중복 생성되지 않는다.
- 가공과 소재 거래의 각 상태 변경이 올바른 상대방에게 알림을 만든다.
- 업무 트랜잭션 실패 시 알림도 저장되지 않는다.

프론트엔드 테스트는 다음을 검증한다.

- 기업 Header에 아이콘과 미확인 배지가 표시된다.
- 패널 열기, 빈 상태, 오류 상태, 모두 읽음이 동작한다.
- 알림 클릭이 읽음 처리와 관련 화면 이동을 수행한다.
- 30초 polling과 탭 재활성화 갱신 옵션이 설정된다.
- 로그아웃 상태와 Consumer 화면에서는 알림 요청을 보내지 않는다.
