# 컨트랙트 env 키 불일치 + 지갑 작업 중복 — Frontend / Backend / Blockchain 요청 문서

> **읽는 시점**: 이 작업을 시작하는 사람이 처음 읽는 문서라고 가정하고,
> 사전 지식 없이 읽을 수 있게 썼습니다.
>
> 작성: Infra(6번), 2026-09-04
> 관련: MR !85(BE 지갑), MR !86(FE 지갑), MR !87(공지),
> `TEAM_NOTICE_2026-09-04-wallet-auth-api.md`, `TEAM_NOTICE_2026-09-04-user-wallet-schema.md`

---

## 0-A. 2026-09-07 갱신 — 상황이 바뀌었습니다 (Infra 6번)

9/4 이후 원격을 다시 대조했습니다. **세 항목의 상태가 바뀌었고, 우선순위가 뒤집혔습니다.**

| 항목 | 9/4 | 9/7 | 근거 |
| --- | --- | --- | --- |
| 1번 BC `RootOrigin.RECOVERED` | 🔴 차단 | 🟢 정리 항목 | BE·FE 가 각자 이미 제외 처리 |
| 1번 BC ABI 전달 경로 | 🟡 전체 대기 | ✅ 해소 | FE 가 소스에 ABI 커밋 |
| 2번 FE 컨트랙트 env 키 | 🟡 (가려져 있음) | 🔴 **유일한 차단 요인** | ABI 가 채워져 문제가 드러남 |

### RECOVERED — 더 이상 막지 않습니다 (§4-1 대체)

`RECOVERED` 로 mint 할 경로가 **양쪽에서 이미 사라졌습니다.**

| 파트 | 근거 | 상태 |
| --- | --- | --- |
| 4번 BE | `backend/develop` `f3ab04e` — `MaterialRootOrigin` enum 에서 삭제, `schema.sql` 이 `ENUM('VIRGIN','WASTE')` | ✅ 반영됨 |
| 2번 FE | `frontend/feat/blockchain-contract-layer` `b8f70ba:src/config/abi/onchainEnums.ts` — `ROOT_ORIGIN_ON_CHAIN` 이 VIRGIN/WASTE 2종 | ✅ 반영됨 (미병합) |
| 1번 BC | `blockchain/develop:contracts/MaterialToken.sol:22` — **RECOVERED 그대로** | ❌ 미처리 |

**사실상 B안으로 굳었습니다.** 백엔드 enum 에 값 자체가 없으니 DB INSERT 실패 시나리오는
발생하지 않습니다. §4-1 의 "4번에게 차단 요청" 도 불필요합니다 — 이미 되어 있습니다.

1번께 남는 건 **"쓸 수 없는 값이 ABI 에 남는다"** 는 위생 문제뿐입니다.
재배포 계획이 있으면 그때 빼 주시고, 없으면 `MaterialToken.sol` 의 `RECOVERED` 옆에
"MVP 미사용" 주석만 남겨 주셔도 됩니다. **급하지 않습니다.**

### ABI — 받았습니다 (§4-2 부분 해소)

2번 FE 가 `frontend/feat/blockchain-contract-layer` (`b8f70ba`, **아직 미병합**) 에서
ABI 를 소스로 직접 커밋했습니다.

```
src/config/abi/materialToken.abi.ts   function 25개
src/config/abi/marketplace.abi.ts
src/config/abi/onchainEnums.ts        RootOrigin / LockPurpose / BurnReason
src/config/abi/abi.test.ts            ABI 시그니처 검증 테스트
```

전달 방식은 **"FE 브랜치에 복사 커밋"** 으로 결정된 것으로 보겠습니다. 다만 두 가지가 남습니다:

- **배포 주소 2개** (`MaterialToken`, `Marketplace`) — 아직 못 받았습니다. 주시면 EC2 `.env` 에 넣겠습니다
- **재배포 시 ABI·주소 갱신 통보 방침** — 복사본이 생겼으므로 이제 **동기화가 깨질 수 있습니다.**
  컨트랙트를 재배포하면 반드시 알려 주세요

### env 키 — 이제 이것만 남았습니다 (§2-1·§2-2 갱신, 🔴 승격)

ABI 가 채워지면서 `requireAbi()` 가 먼저 막아 주던 가림막이 사라졌습니다.
**지금은 세 칸의 키 이름이 서로 다릅니다.**

| 위치 | 소유 | 키 |
| --- | --- | --- |
| `.env.example` / EC2 `.env` | 6번(저) | `VITE_MATERIAL_TOKEN_ADDRESS` `VITE_PAYMENT_TOKEN_ADDRESS` `VITE_ESCROW_ADDRESS` |
| `b8f70ba:docker/Dockerfile` ARG | 2번 | `VITE_MATERIAL_TOKEN_ADDRESS` `VITE_PAYMENT_TOKEN_ADDRESS` `VITE_ESCROW_ADDRESS` |
| `b8f70ba:src/config/contracts.ts` | 2번 | `VITE_MATERIAL_CONTRACT_ADDRESS` `VITE_MARKETPLACE_CONTRACT_ADDRESS` |

**2번께 — 같은 브랜치 안에서 한쪽만 고치셨습니다.**
`contracts.ts` 에서 `escrow` 를 지우고 `marketplace` 를 남기신 건 §2-2 의 "확정 3건" 과 맞습니다.
그런데 **`docker/Dockerfile` 의 `ARG` 는 그대로**입니다. 결과:

- `VITE_MARKETPLACE_CONTRACT_ADDRESS` — **`ARG` 선언이 아예 없습니다.**
  Jenkins 가 `--build-arg` 로 넘겨도 docker 가 `"build-args were not consumed"` 경고만 내고 **값을 버립니다**
- `VITE_MATERIAL_CONTRACT_ADDRESS` — `ARG` 는 `..._TOKEN_ADDRESS` 라 이름이 어긋나 **주입되지 않습니다**
- `VITE_PAYMENT_TOKEN_ADDRESS` `VITE_ESCROW_ADDRESS` — 소스에서 안 쓰는데 `ARG` 에 남아 있습니다

즉 **주소를 받아도 두 컨트랙트 모두 `null` 이 되어 `isContractConfigured()` 가 false** 입니다.

§2-2 의 A안/B안 결정을 주세요. `contracts.ts` 현재 상태는 **A안**(`*_CONTRACT_ADDRESS`) 쪽입니다.
A안으로 확정하시면 `docker/Dockerfile` 의 `ARG` 를 아래 2개로 바꿔 주시면 됩니다:

```dockerfile
ARG VITE_MATERIAL_CONTRACT_ADDRESS
ARG VITE_MARKETPLACE_CONTRACT_ADDRESS
```

**결정을 주시면 같은 배포 주기에 맞춰 제 쪽(`.env.example`·EC2 `.env`·`INFRA_SETUP.md` §3)을
반영하겠습니다.** 제가 먼저 올릴지 나중에 올릴지도 알려 주세요.

> 참고 — 제 Jenkins 가드는 `.env` → `Dockerfile ARG` 누락만 검사하고
> `Dockerfile` → **소스 코드**는 검사하지 않습니다. 이번에도 그 틈에서 어긋났습니다.
> 키 이름이 확정되면 소스까지 검사하도록 가드를 넓히겠습니다.

---

## 0. 요약 — 담당자별 첫 액션

> 아래는 **9/4 작성 시점** 기준입니다. 상태가 바뀐 3건은 §0-A 를 먼저 보세요.

| 대상 | 해야 할 것 | 막혀 있나 |
| --- | --- | --- |
| **2번 Frontend** | `src/config/contracts.ts` 의 env 키 이름이 실제 주입되는 이름과 다릅니다. 고쳐야 컨트랙트 주소가 들어갑니다 | 🔴 **9/7 승격 — ABI 가 채워져 드러났습니다.** §0-A 참고 |
| **2번 Frontend** | `docker/Dockerfile` 의 `ARG` 를 위 결정에 맞춰 조정 | 🟡 위와 같은 건 |
| **2번 Frontend** | **ethers Provider/Signer 공통 모듈은 이미 `frontend/develop` 에 있습니다.** 새로 만들지 마세요 | 🟢 중복 방지 |
| **4번 Backend** | **로그인 nonce 발급 API 는 이미 구현됐습니다** (MR !85). 새로 만들지 마세요 | 🟢 중복 방지 |
| **1번 Blockchain** | `MaterialToken.RootOrigin` 에 `RECOVERED` 가 남아 있어 DB ENUM 과 어긋납니다 | 🟢 **9/7 하향 — BE·FE 가 이미 제외 처리.** 위생 문제만 남음 |
| **1번 Blockchain** | ABI 산출물 전달 경로 (`abi/*.json` → FE) | ✅ **9/7 해소 — FE 가 소스에 커밋.** 배포 주소 2개만 대기 |

~~지금 당장 개발이 멈추는 것은 1번의 `RECOVERED` 하나뿐입니다.~~
**9/7 정정 — 지금 막는 것은 2번의 env 키 이름 결정과 1번의 배포 주소 2개입니다.**

---

## 1. 확정된 사실

| 항목 | 값 | 상태 |
| --- | --- | --- |
| 배포된 컨트랙트 | `MaterialToken`(ERC-1155) + `Marketplace` **둘** | ✅ `blockchain/develop` 확인 |
| `PaymentToken`(ERC-20) | **사용하지 않음** | ✅ blockchain README 명시 |
| Escrow 컨트랙트 | **존재하지 않음** | ✅ 결제는 카카오페이 PG 직결 |
| `.env` 의 컨트랙트 키 | `VITE_MATERIAL_TOKEN_ADDRESS` `VITE_PAYMENT_TOKEN_ADDRESS` `VITE_ESCROW_ADDRESS` | ✅ 현재 상태 |
| `contracts.ts` 가 읽는 키 | `VITE_MATERIAL_CONTRACT_ADDRESS` `VITE_MARKETPLACE_CONTRACT_ADDRESS` `VITE_ESCROW_CONTRACT_ADDRESS` | ❌ 위와 **셋 다 다름** |
| `abis` | 전부 빈 배열 `[]` | ⏳ 1번 산출물 대기 |
| Marketplace 주소 주입 경로 | **없음** — `.env`·Dockerfile ARG 어디에도 키가 없음 | ❌ |

---

## 2. 2번 Frontend 담당자에게

### 2-1. 🟡 컨트랙트 주소가 번들에 들어가지 않습니다

주입 경로를 따라가면 **마지막 한 칸에서 끊깁니다.**

```
EC2 .env            VITE_MATERIAL_TOKEN_ADDRESS   VITE_PAYMENT_TOKEN_ADDRESS   VITE_ESCROW_ADDRESS
   ↓ Jenkinsfile 이 ^VITE_ 줄을 전부 --build-arg 로 넘김                          ✅
docker/Dockerfile   ARG 위 3개 선언                                              ✅ 일치
   ↓ vite build 가 import.meta.env 로 노출                                        ✅
src/config/contracts.ts
   VITE_MATERIAL_CONTRACT_ADDRESS / VITE_MARKETPLACE_CONTRACT_ADDRESS / VITE_ESCROW_CONTRACT_ADDRESS
                                                                                  ❌ 이름이 다름
```

파이프라인 내부(`.env` ↔ Dockerfile ARG ↔ Jenkins 검사)는 **정합합니다.**
제가 만든 Jenkins 가드는 `.env` → `Dockerfile ARG` 누락만 검사하고,
`Dockerfile` → **소스 코드**는 검사하지 않습니다. 그 틈에서 어긋났습니다.

**결과**: `contractAddress()` 가 `undefined` 를 받아 세 주소 모두 `null` →
`isContractConfigured()` 가 항상 `false` →
`getReadContract()`/`getWriteContract()` 가 `"... 컨트랙트 주소가 설정되지 않았습니다."` 로 실패합니다.

**지금은 가려져 있습니다.** `abis` 도 전부 빈 배열이라 어차피 `requireAbi()` 에서 먼저 막힙니다.
1번이 ABI 를 넘기는 순간 이 문제만 남는데, 그때 원인이 **env 키 이름**이라는 걸 알아내기 어렵습니다.

확인 방법 (배포된 번들에 값이 들어갔는지):

```bash
# EC2 에서
docker exec circular-fashion-frontend \
  sh -c 'grep -o "0x[0-9a-fA-F]\{40\}" /usr/share/nginx/html/assets/*.js | head'
```

### 2-2. 이름을 어느 쪽으로 통일할지 정해 주세요

**제가 정하지 않겠습니다.** `.env.example` 과 EC2 `.env` 는 제 소유라 결정만 주시면 바로 반영합니다.
`docker/Dockerfile` 과 `contracts.ts` 는 2번 소유이므로 그쪽에서 고쳐 주세요.

| 안 | 내용 | 장점 | 단점 |
| --- | --- | --- | --- |
| **A** | `.env`·Dockerfile 을 `contracts.ts` 에 맞춘다 (`*_CONTRACT_ADDRESS`) | FE 코드 무변경 | `MATERIAL_CONTRACT` 는 컨트랙트 실제 이름(`MaterialToken`)과 어긋남 |
| **B** (권장) | `contracts.ts` 를 `.env` 에 맞춘다 (`VITE_MATERIAL_TOKEN_ADDRESS`) | 키 이름 = 컨트랙트 이름. ABI 파일명·문서와도 일치 | FE 코드 3줄 수정 |

어느 안이든 **아래 세 가지는 확정입니다.**

| 키 | 처리 |
| --- | --- |
| `VITE_MARKETPLACE_*` | **추가** — 실제 배포 컨트랙트인데 주입 경로가 아예 없습니다 |
| `VITE_PAYMENT_TOKEN_ADDRESS` | **제거** — PaymentToken 은 쓰지 않습니다 (blockchain README) |
| `VITE_ESCROW_*` | **제거** — Escrow 컨트랙트가 존재하지 않습니다. `contracts.ts` 의 `escrow` 항목도 함께 |

### 2-3. 고쳐야 할 파일 (2번 소유)

| 파일 | 무엇을 |
| --- | --- |
| `src/config/contracts.ts` | 안 B 면 키 이름 3개. 어느 안이든 `escrow` 항목 제거 + `marketplace` 유지 |
| `docker/Dockerfile` | `ARG` 목록을 결정에 맞춰 조정 (`PAYMENT_TOKEN`·`ESCROW` 제거, `MARKETPLACE` 추가) |

> ⚠️ **한쪽만 바꾸면 값이 조용히 사라집니다.** Dockerfile 에 `ARG` 선언이 없는 키는
> docker 가 `"build-args were not consumed"` 경고만 내고 값을 버립니다.
> `.env`(제 쪽)와 `Dockerfile`(2번 쪽)을 **같은 배포 주기에** 맞춰야 합니다.
> 제 변경을 먼저 올릴지 나중에 올릴지 알려 주세요.

### 2-4. 🟢 ethers Provider/Signer 공통 모듈 — 이미 있습니다

**새로 만들지 마세요.** `frontend/develop` 에 이미 병합돼 있습니다.

| 파일 | 내용 |
| --- | --- |
| `features/wallet/services/walletService.ts` | `getInjectedProvider` `isWalletAvailable` `getBrowserProvider` `getSigner` `getWalletAddress` `getChainId` `signMessage` `toWalletError` |
| `features/wallet/services/walletClient.ts` | EIP-1193 raw 계층 — 연결 상태, `accountsChanged`/`chainChanged` |
| `services/blockchain/contractFactory.ts` | `getReadContract` / `getWriteContract` — Contract 생성의 유일한 지점 |
| `hooks/useBlockchainTransaction.ts` | Tx UX 상태기계 (`IDLE→SIGNING→PENDING→CONFIRMED/FAILED`) |

계층이 이미 나뉘어 있습니다 — `walletService` 는 **능력**(Provider/Signer/서명),
`walletClient` 는 **상태**(연결·이벤트 구독). 새 모듈을 만들면 이 경계가 무너집니다.

#### MR !86 에 들어 있는 것 (아직 병합 전이라 지금은 안 보입니다)

| 내용 | 왜 |
| --- | --- |
| `getChainIdNumber()` | 10진수 chainId. BE `wallet_nonce.chain_id` 가 `BIGINT` 라 hex 문자열을 보낼 수 없습니다 |
| **`errorCode()` 버그 수정** | 아래 참고 |

**ethers v6 는 provider 에러를 감쌀 때 최상위 `code` 를 문자열 `'ACTION_REJECTED'` 로 덮고
원본 숫자 코드를 `info.error.code` 아래로 밀어 넣습니다.** 기존 `toWalletError()` 는 최상위
숫자 `code` 만 봤기 때문에 **사용자가 서명을 취소(4001)하면 "알 수 없는 오류가 발생했습니다"**
가 떴습니다. 정상 흐름인데 장애처럼 보이던 것입니다.

연결 경로(`walletClient.ts`)는 raw 에러를 받아서 이 문제가 없습니다 — 그래서 눈에 잘 안 띕니다.
**직접 서명·트랜잭션 코드를 새로 쓰면 같은 함정에 다시 빠집니다.** MR !86 을 먼저 보세요.

---

## 3. 4번 Backend 담당자에게

### 3-1. 🟢 로그인 nonce 발급 API — 이미 구현됐습니다

**새로 만들지 마세요.** MR **!85** 에 있습니다.

```
POST /api/v1/auth/wallet/nonce     로그인용 nonce + 서명 정본 메시지  (인증 없음)
POST /api/v1/auth/wallet/verify    서명 검증 → JWT                    (인증 없음)
POST /api/v1/wallets/nonce         등록용 nonce                       (인증 필요)
POST /api/v1/wallets               서명 검증 → 지갑 등록              (인증 필요)
GET  /api/v1/wallets/me            지갑 목록 (활성·비활성)
DELETE /api/v1/wallets/{walletId}  비활성화
```

nonce 발급만이 아니라 **검증까지** 끝나 있습니다:

- 1회용 보장 — `used_at IS NULL` 조건부 UPDATE 의 영향 행 수로 판정 (다중 인스턴스에서도 성립)
- TTL 5분 (`app.auth.wallet.nonce-ttl`), 지갑당 살아 있는 nonce 1개
- EIP-191 `personal_sign` 복구 → 요청 주소와 대조
- 역할 제한 — `SUPPLIER`/`BRAND`/`PROCESSOR` 만 (`UserRole.canOwnWallet()`)
- 정지 계정·미등록 지갑은 **서명 팝업이 뜨기 전에** 거부

패키지: `com.ssafy.upcycle.user` — `entity` `repository` `service` `controller` `dto`.
테스트 182건. 계약은 `TEAM_NOTICE_2026-09-04-wallet-auth-api.md` 참고.

### 3-2. 재사용하실 것

| 클래스 | 용도 |
| --- | --- |
| `User` `UserWallet` `WalletNonce` + 리포지토리 | 다른 도메인에서 `users` 를 참조할 때 |
| `WalletNonceGenerator` | 32자 hex nonce. 길이 계약이 `CHAR(32)` 와 묶여 있어 새로 만들면 어긋납니다 |
| `WalletSignatureVerifier` | EIP-191 복구. **프리픽스를 직접 만들지 마세요** — 아래 참고 |
| `WalletLoginMessage` | 서명 문구. FE 와의 고정 계약 |

> ⚠️ **EIP-191 프리픽스를 손으로 만들면 한글 메시지에서 전량 실패합니다.**
> 프리픽스는 **UTF-8 바이트 길이**를 쓰는데, 로그인 문구는 140자 / **210 바이트**입니다.
> `String.length()` 로 계산하면 70바이트가 어긋납니다.
> `Sign.signedPrefixedMessageToKey(msg.getBytes(UTF_8), data)` 에 맡기세요.

### 3-3. 조회할 때 주의

`user_wallet` 은 이제 **계정당 활성 1개 + 교체 이력**입니다
(`TEAM_NOTICE_2026-09-04-user-wallet-schema.md`).

| 메서드 | 용도 |
| --- | --- |
| `findByWalletAddressAndActiveTrue` | **소유자를 찾을 때.** 비활성은 없는 것과 같게 취급 |
| `findByWalletAddress` | 주소가 이미 점유됐는지 확인할 때만 (비활성 행도 돌려줍니다) |

자동완성만 보고 고르면 비활성 지갑으로 로그인이 되는 구멍이 생깁니다.

---

## 4. 1번 Blockchain 담당자에게

### 4-1. 🔴 `RootOrigin.RECOVERED` 가 DB ENUM 과 어긋납니다

```solidity
// contracts/MaterialToken.sol
enum RootOrigin { VIRGIN, WASTE, RECOVERED }
```

```sql
-- schema.sql
root_origin ENUM('VIRGIN', 'WASTE') NOT NULL
```

**백엔드가 `RECOVERED` 로 mint 하면 온체인은 성공하고 DB INSERT 는 제약 위반으로 실패합니다.**
온체인이 Source of Truth 인데 Projection 이 못 따라가는 상태가 되어, 되돌리기가 어렵습니다.

DB v3 문서는 회수 소재 재순환(2차·3차 순환)을 **MVP 범위 제외**로 확정했습니다 —
회수 소재의 부모는 Material 이 아니라 ProductItem 이라 `material_lineage` 구조 확장이 선행됩니다.

| 안 | 내용 | 비고 |
| --- | --- | --- |
| **A** (권장) | 컨트랙트에서 `RECOVERED` 제거 | enum 값이 줄면 **기존 배포 컨트랙트와 호환되지 않습니다.** 재배포 전이라면 지금이 적기 |
| **B** | 컨트랙트에 남기고 백엔드가 차단 | 재배포 불필요. 다만 "쓸 수 없는 값" 이 ABI 에 남아 다음 사람이 오해합니다 |

재배포 계획이 있는지 알려 주세요. 없으면 B 로 가고 백엔드 쪽 차단은 4번에게 요청하겠습니다.

### 4-2. 🟡 ABI 전달 경로가 아직 없습니다

`src/config/contracts.ts` 의 `abis` 가 전부 빈 배열이고 주석에
`TODO(blockchain 파트): blockchain/master 의 abi/ 산출물이 나오면 ...` 로 남아 있습니다.

**컨트랙트 호출 기능 전체가 여기서 대기 중입니다.** 정해야 할 것:

- `abi/MaterialToken.json` · `abi/Marketplace.json` 을 **어떻게 넘길지** — FE 브랜치에 복사 커밋 / npm 패키지 / 빌드 시 fetch
- **배포 주소** — `MaterialToken`, `Marketplace` 각각. SSAFY Besu 실배포 주소
- 컨트랙트가 재배포될 때 주소·ABI 를 **어떻게 갱신 통보**할지

주소는 저에게 주시면 EC2 `.env` 에 넣겠습니다. ABI 전달 방식은 2번과 합의해 주세요.

### 4-3. 참고 — 서명 지갑 role

`MaterialToken` 은 `SUPPLIER_ROLE`/`PROCESSOR_ROLE`/`BRAND_ROLE`/`MATERIAL_LOCK_MANAGER_ROLE` 을
쓰고, `Marketplace` 는 `markPaid` 에 `PAYMENT_MANAGER_ROLE` 이 필요합니다.

**`PAYMENT_MANAGER_ROLE` 은 백엔드 서비스 지갑이 가져야 합니다** —
PG 결제 승인을 확인한 뒤 백엔드가 호출하는 함수이고, 사용자가 직접 부르지 않습니다.
EC2 의 `BLOCKCHAIN_SERVICE_PRIVATE_KEY` 에 대응하는 주소에 role 을 부여해 주세요.
그 주소는 필요하시면 제가 알려 드립니다 (키는 공유하지 않습니다).

---

## 5. 아직 막혀 있지 않은 것

- **지갑 로그인 전 구간** — BE MR !85, FE MR !86 으로 동작합니다. 컨트랙트와 무관합니다
- **소재 등록의 DB 부분** — 온체인 mint 전까지는 진행 가능합니다 (`materials` → `confirm` 2단계 패턴)
- **Marketplace·Processing·Production 의 DB·화면** — 온체인 확정만 대기 상태로 두면 됩니다
- **프론트 화면 전반** — 컨트랙트 호출을 `isContractConfigured()` 로 감싸 두면 비활성 상태로 개발 가능합니다

**env 키 불일치는 지금 아무 기능도 깨뜨리지 않습니다.** ABI 가 오기 전에 정리하면 됩니다.

---

## 6. 처리되면 제가 이어서 할 것

| 선행 | 그다음 제 작업 |
| --- | --- |
| 2번이 env 키 이름 결정 | `.env.example` + EC2 `.env` 반영, `docs/INFRA_SETUP.md` §3 갱신, 변경 공지 |
| 1번이 컨트랙트 주소 전달 | EC2 `.env` 주입 → 프론트 재빌드 트리거 |
| 1번이 `RECOVERED` 처리 방침 결정 | B안이면 4번에게 백엔드 차단 요청 문서 작성 |
| 2번이 Dockerfile ARG 조정 | 같은 배포 주기에 맞춰 `.env` 반영 순서 조율 |

**처리 완료는 MR 번호나 커밋 해시로 알려 주세요.** 이 문서 상단에 ✅ 로 표시하고
후속 작업을 시작하겠습니다.

---

## 부록 — 왜 제가 대신 고치지 않았나

남의 브랜치 파일을 제가 고치면 그 담당자는 자기 코드가 언제 왜 바뀌었는지 모르고,
MR 리뷰 범위도 흐려집니다. `docker/Dockerfile` 과 `Jenkinsfile` 은 각 파트 브랜치 소유라는
규약도 있습니다 (`docs/INFRA_SETUP.md` §4).

**제 소유인 것은 제가 이미 했거나 결정만 기다리고 있습니다** — `.env.example`, EC2 `.env`,
`nginx`, `docker-compose.yml`, `scripts/`, `jenkins/casc.yaml`.
