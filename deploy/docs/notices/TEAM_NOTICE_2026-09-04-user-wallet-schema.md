# 📢 스키마 변경 — user_wallet: 활성 1개 + 교체 이력 보존 (2026-09-04)

## 결론부터

**`user_wallet` 에서 `UNIQUE (user_id)` 를 없애고, 계정당 활성 지갑 1개 + 교체 이력을 남깁니다.**

```
- CONSTRAINT uk_user_wallet_user UNIQUE (user_id)          제거
+ is_active BOOLEAN NOT NULL DEFAULT TRUE                  추가
+ deactivated_at DATETIME NULL                             추가
+ active_user_id BIGINT GENERATED ALWAYS AS (...)          추가 (활성 단일성 강제용)
+ CONSTRAINT uk_user_wallet_active_user UNIQUE (active_user_id)
+ INDEX idx_user_wallet_user (user_id)
```

**다른 테이블은 건드리지 않았습니다.** `material_balance` · Lock · Trade · Processing ·
Production 은 그대로입니다 — 다른 파트 코드에 영향이 없습니다.

Backend MR **!85** 에 포함. 운영 DB 에는 아래 `ALTER TABLE` 이 필요합니다.

---

## 왜 바꾸는가 — API 명세와 스키마가 어긋나 있었습니다

| | 전제 |
| --- | --- |
| API 명세 | `GET /wallets/me` "목록 (활성/비활성)", `DELETE /wallets/{walletId}` "비활성화" → **N개** |
| 실제 `schema.sql` | `uk_user_wallet_user UNIQUE (user_id)` → **1개** |

명세대로 목록·비활성화를 만들려면 계정당 여러 행이 필요한데 스키마가 막고 있었습니다.
어느 쪽이든 한 번은 맞춰야 해서 정리했습니다.

### N개 "동시 사용" 은 택하지 않았습니다

`material_balance` 의 `UNIQUE (material_id, owner_id)` 가 소유를 **사용자 기준**으로 잡습니다.
지갑을 여러 개 동시에 쓰면 온체인 잔액은 **주소별로** 갈리는데 DB 는 **사용자당 한 행만**
허용해 불일치가 생깁니다. 동시 사용을 늘리려면 Balance · Lock · Trade 전반을 지갑 기준으로
재정의해야 하고, 그건 2번·3번·4번·5번 전원의 합의 사안입니다.

### 이력은 반드시 남겨야 합니다

여섯 컬럼이 **서명 시점 지갑 주소를 스냅샷으로** 들고 있고 전부 `NOT NULL` 입니다.

```
material_trade.seller_wallet      material_trade.buyer_wallet
processing_job.brand_wallet       processing_job.processor_wallet
production_batch.producer_wallet  blockchain_transaction.from_wallet
```

교체하면서 `user_wallet` 행을 지우거나 덮어쓰면 **그 주소가 누구 것이었는지 역추적할 수
없습니다.** `users` 에서 지갑을 분리한 이유가 이력 보존인데, `user_wallet` 안에서 같은
덮어쓰기가 반복되면 분리 목적이 달성되지 않습니다.

---

## 활성 단일성을 생성 컬럼으로 강제합니다

MySQL 8 은 **부분 UNIQUE(`... WHERE is_active = TRUE`)를 지원하지 않습니다.**
비활성 행을 `NULL` 로 만드는 생성 컬럼에 UNIQUE 를 걸면, UNIQUE 에서 `NULL` 은 서로
충돌하지 않으므로 활성만 1개로 제한됩니다.

```sql
active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_active THEN user_id ELSE NULL END)
```

`IF(is_active, user_id, NULL)` 이 더 짧지만 **H2 가 파싱하지 못합니다** — 스키마 테스트가
`schema.sql` 을 H2 에 올려 검증하므로 표준 `CASE WHEN` 을 씁니다.

> 📌 **다른 테이블에도 "부분 UNIQUE" 가 필요하면 같은 방법을 쓰세요.**
> `consumer_address` 의 기본 배송지 단일성, `trade_dispute` 의 열린 분쟁 1건 등이
> 같은 상황입니다. 현재는 둘 다 제약이 없어 코드로만 보장됩니다.

### 이 컬럼은 엔티티가 매핑하지 않습니다

DB 가 계산하는 값이라 JPA 가 쓰려 들면 INSERT 가 깨집니다.
`UserSchemaMappingTests` 는 `IS_GENERATEDCOLUMN = YES` 인 컬럼을 비교에서 제외하도록
고쳤습니다 — **다른 도메인에서 생성 컬럼을 쓰더라도 그 테스트는 그대로 통과합니다.**

---

## 운영 DB 마이그레이션

```sql
ALTER TABLE user_wallet
    DROP INDEX uk_user_wallet_user,
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER chain_id,
    ADD COLUMN deactivated_at DATETIME NULL AFTER created_at,
    ADD COLUMN active_user_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN is_active THEN user_id ELSE NULL END),
    ADD CONSTRAINT uk_user_wallet_active_user UNIQUE (active_user_id),
    ADD INDEX idx_user_wallet_user (user_id);
```

기존 행은 `is_active = TRUE` 가 되고, 계정당 한 행뿐이었으므로 새 UNIQUE 를 그대로 만족합니다.
**데이터 손실 없이 적용됩니다.**

적용 순서는 **DB 먼저, 그다음 배포**입니다. `ddl-auto: validate` 라서 컬럼이 없는 상태로
새 이미지가 올라오면 기동에 실패합니다.

> 운영 반영은 제가 하겠습니다. MR !85 병합 시점을 알려주시면 DB 먼저 적용하고 CD 를 돌립니다.

---

## 새로 생긴 API (인증 필요)

```
POST   /api/v1/wallets/nonce      등록용 nonce 발급
POST   /api/v1/wallets            서명 검증 후 등록 (기존 활성 지갑은 자동 비활성)
GET    /api/v1/wallets/me         내 지갑 목록 (활성·비활성 전부, 최신순)
DELETE /api/v1/wallets/{walletId} 비활성화 (행은 남습니다)
```

**경로가 `/api/v1/auth/**` 밖인 것이 의도적입니다.** 그 프리픽스는 SecurityConfig 가
인증 없이 열어 둔 곳이라, 등록을 거기 두면 **아무나 남의 계정에 지갑을 붙일 수 있습니다.**
로그인용 `/api/v1/auth/wallet/*` 과 경로를 나눈 이유가 이것입니다
(로그인 계약은 `TEAM_NOTICE_2026-09-04-wallet-auth-api.md`).

사용자는 **JWT 의 `sub`** 에서 얻습니다. 요청 바디로 받지 않습니다 — 받으면 남의 계정을
지정할 수 있습니다.

### 추가된 에러 코드

| code | HTTP | 언제 |
| --- | --- | --- |
| `WALLET_ALREADY_LINKED` | 409 | 이미 **다른 계정**에 등록된 주소 |
| `WALLET_NOT_FOUND` | 404 | 내 지갑이 아니거나 없음 |
| `WALLET_ALREADY_INACTIVE` | 400 | 이미 비활성화된 지갑 |

`WALLET_NOT_FOUND` 는 남의 지갑에도 같은 응답을 줍니다 — "권한 없음" 과 "없음" 을
구분해 주면 id 를 훑어 존재 여부를 알아낼 수 있습니다.

### 같은 주소를 다시 등록하면

`wallet_address` 는 **전역 UNIQUE** 라 비활성 행도 자리를 점유합니다. 그래서 재등록은
새 행을 만들지 않고 **기존 행을 되살립니다**(`is_active = TRUE`, `deactivated_at = NULL`).
이력 관점에서도 같은 지갑이 두 행으로 늘어나지 않는 편이 맞습니다.

---

## 각 파트에 알려드릴 것

### 2번 (Frontend)

`GET /api/v1/wallets/me` 응답이 **배열**입니다. `active` · `verified` · `createdAt` ·
`deactivatedAt` 로 활성/이력을 구분해 보여주면 됩니다.

**비활성 지갑으로는 로그인되지 않습니다.** `POST /api/v1/auth/wallet/nonce` 가
`WALLET_NOT_LINKED` 를 줍니다 — 비활성 지갑은 없는 것과 같게 취급합니다.
사용자가 "내 지갑 목록에는 보이는데 로그인이 안 된다" 고 할 수 있으니 목록 화면에서
활성 여부를 분명히 보여주세요.

### 2번 (Material) · 1번 (Blockchain)

⚠️ **지갑을 교체해도 이전 지갑의 온체인 잔액은 자동으로 옮겨지지 않습니다.**

`material_balance.owner_id` 는 사용자 기준이라 DB 상으로는 문제가 없지만, 온체인 잔액은
**예전 주소에 그대로 남습니다.** 이전이 필요하면 사용자가 `safeTransferFrom` 을 직접
호출해야 하고, 그 경로는 아직 없습니다.

교체 시점에 잔액이 남아 있으면 어떻게 할지 정해야 합니다. 후보:

1. 잔액이 남아 있으면 **교체를 막는다** (가장 안전, UX 는 불편)
2. 교체는 허용하고 **이전 안내만** 한다
3. 이전 트랜잭션을 유도하는 화면을 만든다

현재 구현은 2번(막지 않음)이며 **의도적으로 미결로 두었습니다.** 결정해 주시면 반영하겠습니다.

### 4번 (Backend)

`user_wallet` 을 조회할 때 **활성 여부를 반드시 조건에 넣으세요.**
`findByWalletAddress` 는 비활성 행도 돌려줍니다 — 그건 "주소가 이미 점유됐는지" 확인용이고,
소유자를 찾는 목적이면 `findByWalletAddressAndActiveTrue` 를 써야 합니다.

리포지토리 메서드 이름에 그 구분이 드러나 있으니 자동완성만 보고 고르지 마세요.

---

## 확인 방법

```bash
./mvnw -B -ntp test        # 182건 통과
```

활성 단일성은 `UserWalletActiveConstraintTests` 가 **`schema.sql` 을 raw H2 에 올려**
검증합니다. 테스트 프로파일이 `create-drop` 이라 엔티티가 만드는 테이블에는 생성 컬럼이
없어서, 일반 테스트로는 이 제약을 확인할 수 없습니다.

> 이 구조 때문에 실제로 버그를 하나 잡았습니다. 엔티티에 `unique = true` 가 남아 있었고,
> 그대로 두면 **Hibernate 가 만드는 테이블(테스트)에만 제약이 생겨** 이력 보존이
> 테스트에서만 실패하고 운영에서는 되는 상태가 됩니다.
