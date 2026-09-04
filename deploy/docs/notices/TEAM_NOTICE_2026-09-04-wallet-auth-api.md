# 📢 API 공지 — 지갑 서명 로그인 엔드포인트 확정 (2026-09-04)

## 결론부터

**지갑 서명으로 로그인하는 엔드포인트 2개를 구현했고, 경로는 아래로 확정합니다.**

```
POST /api/v1/auth/wallet/nonce     1회용 nonce + 서명할 메시지 발급
POST /api/v1/auth/wallet/verify    서명 검증 후 Access Token 발급
```

인증 없이 호출합니다 (로그인 전이므로). `SecurityConfig` 가 `/api/v1/auth/**` 를 이미
열어 두고 있어 **보안 설정 변경은 없습니다.**

Backend MR **!85**, Frontend MR **!86** 에서 함께 진행했습니다.

## 왜 공지하는가 — API 명세에 이 경로가 없었습니다

API 명세에는 **지갑 등록용** 엔드포인트만 있습니다.

| 명세에 있던 것 | 용도 | 인증 |
| --- | --- | --- |
| `POST /wallets/nonce` | 지갑 **등록**용 nonce | 필요 (SUPPLIER/BRAND/PROCESSOR) |
| `POST /wallets` | 서명 검증 후 지갑 **등록** | 필요 |
| `GET /wallets/me` | 내 지갑 목록 | 필요 |
| `DELETE /wallets/{walletId}` | 지갑 비활성화 | 필요 |

반면 **로그인**용 엔드포인트는 명세에 없었습니다. 기술 스택 문서에는
*"Backend 인증은 Wallet Signature 기반 인증을 사용한다 — Nonce 발급 → MetaMask 서명 →
Signature 검증 → JWT 발급"* 이라고 적혀 있는데, 그 흐름에 대응하는 경로가 API 목록에
빠져 있었습니다.

**등록과 로그인은 다른 기능입니다.** 등록은 "이미 로그인한 사용자가 자기 지갑을 계정에 붙이는 것"
이고, 로그인은 "지갑 서명으로 세션을 얻는 것" 입니다. 전자는 인증이 필요하고 후자는 필요 없습니다.
그래서 경로를 겹치지 않게 `/api/v1/auth/wallet/*` 로 두었습니다.

> **API 명세에 위 두 경로를 추가해 주세요.** 명세와 구현이 일치해야 한다는 요구사항(NFR-015)
> 때문에 어느 쪽이든 한 번은 맞춰야 하고, 구현이 이미 이 경로로 나갔습니다.
> 등록용 `/wallets/*` 는 아직 구현하지 않았습니다 (아래 "미결" 참고).

---

## 1. nonce 발급

```
POST /api/v1/auth/wallet/nonce
Content-Type: application/json
```

```json
{
  "walletAddress": "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
  "chainId": 1337
}
```

| 필드 | 타입 | 필수 | 비고 |
| --- | --- | --- | --- |
| `walletAddress` | string | ✅ | `^0x[0-9a-fA-F]{40}$`. 대소문자는 서버가 정규화 |
| `chainId` | number | ❌ | **10진수 정수**. 생략하면 등록된 체인을 그대로 씁니다 |

응답 (`ApiResponse.data`)

```json
{
  "walletAddress": "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
  "nonce": "0123456789abcdef0123456789abcdef",
  "message": "SAEROI 로그인 요청\n\n지갑 주소: 0xd8dA...\nNonce: 0123...\n\n이 서명은 로그인 용도로만 사용되며 자산이 이동하지 않습니다.",
  "expiresAt": "2026-09-04T12:05:00"
}
```

- `walletAddress` 는 **서버가 계산한 EIP-55 체크섬** 형태입니다. 요청이 보낸 대소문자가 아닙니다
- `nonce` 는 32자 소문자 hex, **1회용**, 유효시간 **5분**
- 같은 지갑에 새로 발급하면 **이전 미사용 nonce 는 삭제**됩니다 (지갑당 하나만 살아 있음)

### `message` 를 서명하세요

서명할 문자열의 **정본은 서버가 내려주는 `message`** 입니다. 프론트가 같은 규칙으로
만든 문자열과 대조한 뒤 이 값에 서명합니다 — 양쪽 구현이 어긋나면 **서명이 만들어지기
전에** 드러납니다.

문자열 규칙 (한 글자도 달라선 안 됩니다)

```
SAEROI 로그인 요청
                                    ← 빈 줄
지갑 주소: {EIP-55 체크섬 주소}
Nonce: {32자 hex}
                                    ← 빈 줄
이 서명은 로그인 용도로만 사용되며 자산이 이동하지 않습니다.
```

`\n` 으로 이어 붙인 6줄입니다. **140자 / UTF-8 210바이트** — EIP-191 프리픽스가
바이트 길이를 쓰기 때문에 이 숫자가 중요합니다. 직접 프리픽스를 만들지 말고
라이브러리(`ethers.signMessage`, web3j `signedPrefixedMessageToKey`)에 맡기세요.

---

## 2. 서명 검증

```
POST /api/v1/auth/wallet/verify
```

```json
{
  "walletAddress": "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
  "nonce": "0123456789abcdef0123456789abcdef",
  "signature": "0x<130자 hex>"
}
```

`nonce` 를 함께 보냅니다 — 서버가 **어느 발급분에 대한 서명인지** 특정해야 정확히
그 한 건만 소모할 수 있습니다.

응답 (`ApiResponse.data`) — **이메일 로그인과 같은 모양**입니다.

```json
{
  "accessToken": "eyJ...",
  "user": {
    "id": 7,
    "email": "supplier@example.com",
    "name": "공급자",
    "phone": "010-0000-0000",
    "role": "SUPPLIER",
    "status": "ACTIVE",
    "createdAt": "2026-09-04T03:00:00"
  }
}
```

- `password` 는 응답에 **없습니다**
- 토큰의 `sub` 는 user id, `roles` 는 `["SUPPLIER"]` 형태로 **`ROLE_` 접두어가 없습니다**
- 검증 성공 시 `user_wallet.verified = TRUE`, `verified_at` 이 갱신됩니다

---

## 3. 에러 코드 — Frontend 분기용 계약

`ApiResponse.code` 로 내려갑니다. **`message` 는 바뀔 수 있고 `code` 가 계약입니다.**

| code | HTTP | 언제 |
| --- | --- | --- |
| `INVALID_WALLET_ADDRESS` | 400 | 주소 형식 오류 |
| `INVALID_CHAIN_ID` | 400 | 체인 ID 가 양수가 아님 |
| `WALLET_NOT_LINKED` | 404 | **등록되지 않은 지갑 주소** |
| `WALLET_CHAIN_MISMATCH` | 400 | 요청 체인이 등록된 체인과 다름 |
| `WALLET_ROLE_NOT_ALLOWED` | 403 | **소비자·관리자 계정** — 지갑 로그인 대상이 아님 |
| `USER_SUSPENDED` | 403 | 정지된 계정 |
| `NONCE_NOT_FOUND` | 400 | 해당 지갑에 그 nonce 가 없음 |
| `NONCE_EXPIRED` | 400 | 유효시간 경과 |
| `NONCE_ALREADY_USED` | 400 | 이미 소모됨 |
| `INVALID_SIGNATURE` | 400 | 서명 형식 오류·복구 불가 |
| `SIGNATURE_MISMATCH` | 401 | 복원된 주소가 요청 주소와 다름 |
| `VALIDATION_ERROR` | 400 | Bean Validation 실패. `data` 에 필드별 오류 |

**`WALLET_NOT_LINKED` 와 `WALLET_ROLE_NOT_ALLOWED` 는 nonce 단계에서 걸립니다** —
사용자가 MetaMask 팝업에 서명까지 하고 나서 거부당하지 않도록 의도한 것입니다.
정지 계정도 마찬가지입니다.

서명·복원된 주소·nonce 원문은 **응답과 로그 어디에도 남기지 않습니다.**

---

## 4. 각 파트에 알려드릴 것

### 2번 (Frontend)

`frontend/feat/wallet-signature-login` (MR !86) 에 이미 붙여 두었습니다.

- `features/auth/api/walletAuthApi.ts` — 위 두 경로
- `features/wallet/hooks/useWalletLogin.ts` — nonce → 메시지 대조 → 서명 → verify
- `features/wallet/components/WalletLoginButton.tsx` — 에러 code 별 안내 문구
- `LoginPage` 의 "지갑으로 로그인" 섹션에 배선

⚠️ **경로 버전이 섞여 있습니다.** `authApi.ts` 의 `/auth/login`·`/auth/me` 는 아직 컨트롤러가
없는 **가정값**(무버전)이고, 지갑 로그인만 `/v1` 입니다. "통일" 하려고 한쪽만 고치지 마세요 —
양쪽 파일에 이유를 주석으로 남겨 뒀습니다. **이메일 로그인 컨트롤러가 나올 때 함께 `/v1` 로
옮기는 것**이 맞습니다.

### 4번 (Backend)

같은 `com.ssafy.upcycle.user` 패키지에 `User`·`UserWallet`·`WalletNonce` 엔티티와
리포지토리를 만들어 뒀습니다. **다른 도메인에서 `users` 를 참조해야 하면 재사용하세요.**
`Material.registrantId` 처럼 raw `Long` FK 로 쓰는 것이 이 프로젝트 관례입니다.

`ApiResponse` 를 직접 반환하고 실패는 `BusinessException(도메인 ErrorCode)` 로 던지는
패턴도 그대로 따랐습니다 (`WalletAuthController` 참고).

### 1번 (Blockchain)

이 기능은 **온체인 Tx 가 없습니다.** 서명을 서버에서 검증만 하는 오프체인 흐름입니다
(`web3j:crypto` 의 `ecrecover` 사용). 컨트랙트 호출·이벤트 구독은 포함되지 않습니다.

다만 `MaterialToken.RootOrigin` 에 **`RECOVERED`** 가 남아 있는데 DB `material.root_origin`
ENUM 은 `VIRGIN`/`WASTE` 뿐입니다. `RECOVERED` 로 mint 하면 백엔드 INSERT 가
제약 위반으로 실패합니다 — 컨트랙트에서 뺄지, 백엔드에서 막을지 알려주세요.

---

## 5. 수동 테스트에는 시드가 필요합니다

**회원가입 API가 없어서 `users` + `user_wallet` 행을 만들 경로가 없습니다.**
행이 없으면 nonce 발급이 **항상 `WALLET_NOT_LINKED`** 로 끝납니다 — 정상 동작인데
엔드포인트가 깨진 것처럼 보입니다.

```sql
INSERT INTO users (email, password, name, phone, role, status)
VALUES ('supplier@example.com', '{noop}dev-only', '개발자', '010-0000-0000', 'SUPPLIER', 'ACTIVE');

INSERT INTO user_wallet (user_id, wallet_address, chain_id, verified)
VALUES (LAST_INSERT_ID(), LOWER('0x본인MetaMask주소'), 1337, FALSE);
```

- `wallet_address` 는 **소문자로** 넣으세요. 앱이 소문자로 정규화해 조회합니다
- `chain_id` 는 MetaMask 가 실제로 붙어 있는 체인과 같아야 합니다. 아니면 매번
  `WALLET_CHAIN_MISMATCH` 입니다
- `role` 은 `SUPPLIER`/`BRAND`/`PROCESSOR` 중 하나여야 합니다.
  `CONSUMER`·`ADMIN` 은 `WALLET_ROLE_NOT_ALLOWED` 로 거부됩니다

---

## 6. 미결 — 지갑을 계정당 몇 개까지 둘 것인가

**API 명세와 DB 스키마가 서로 다릅니다.**

| | 전제 |
| --- | --- |
| API 명세 | `GET /wallets/me` "목록 (활성/비활성)", `DELETE /wallets/{walletId}` "비활성화" → **N개** |
| DB 스키마 v3 문서 | `user_id` UNIQUE 제거 + `is_active`/`deactivated_at` 추가 → **N개** |
| **실제 `schema.sql`** | `uk_user_wallet_user UNIQUE (user_id)`, `is_active` 없음 → **1개** |

**결정됐습니다 — 활성 1개 + 교체 이력 보존.** 스키마와 등록·비활성화 API 를 함께 반영했습니다.
자세한 내용과 운영 DB `ALTER TABLE` 은 `TEAM_NOTICE_2026-09-04-user-wallet-schema.md` 를 보세요.

N개 동시 사용은 택하지 않았습니다. `material_balance` 의 `UNIQUE (material_id, owner_id)` 가
소유를 사용자 기준으로 잡아서, 지갑이 여러 개면 온체인 잔액은 주소별로 갈리는데 DB 는
사용자당 한 행만 허용해 불일치가 생깁니다.

---

## 확인 방법

```bash
curl -s -X POST https://j15e104.p.ssafy.io/api/v1/auth/wallet/nonce \
  -H 'Content-Type: application/json' \
  -d '{"walletAddress":"0x...","chainId":1337}'
```

MR !85 가 `backend/develop` → `backend/master` 로 병합되어 CD 가 돌면 위 주소에서
호출할 수 있습니다. 그 전에는 로컬(`localhost:8080`)에서만 됩니다.

Backend 테스트 163건, Frontend 64건이 이 계약을 고정하고 있습니다.
