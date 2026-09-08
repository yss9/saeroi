# Role 별 기능 권한 Matrix 확정

- 티켓: S15P21E104-286 (`[COMMON]`). 구현은 S15P21E104-287 (`[BE]`), 클레임은 S15P21E104-288 (`[BE]`)
- 결정자: 공병우 (인프라 · User/Wallet · Product/Material)
- 날짜: 2026-09-08
- 역할: `SUPPLIER` `BRAND` `PROCESSOR` `CONSUMER` `ADMIN` (`users.role` ENUM 과 1:1, `UserRole`)

---

## 0. 먼저 — 티켓의 전제를 정정한다

티켓은 "Role 별 권한을 확정하고 Spring Security 로 구현한다" 로 적혀 있다. 그 문장은
**아직 아무 인가도 없다는 전제**를 담고 있는데, 사실이 아니다.

역할 인가는 **이미 광범위하게 구현되어 있다.** 다만 Spring Security 가 아니라
**서비스 계층에서 DB 를 재조회하는 방식**이다.

| 서비스 | 검사 |
| --- | --- |
| `MaterialRegistrationService` | `canRegisterMaterial()` = SUPPLIER·BRAND |
| `ProductModelRegistrationService` | `canRegisterProduct()` = BRAND |
| `ListingCommandService` | SUPPLIER + `isActive()` |
| `TradeCommandService` | BRAND + `isActive()` |
| `ProcessingRequestService` | BRAND(요청·취소) / PROCESSOR(반려) + `isActive()` |
| `ProcessingQuoteService` | PROCESSOR(제출) / BRAND(목록) + `isActive()` |
| `ProcessingJobService` · `ProcessingPaymentService` | BRAND + `isActive()` |
| `ProcessorSearchService` | BRAND + `isActive()` |
| `WalletAuthService` · `WalletRegistrationService` · `SignupService` | `canOwnWallet()` = SUPPLIER·BRAND·PROCESSOR |

그리고 `UserRole` 에는 이미 `canOwnWallet()` `canRegisterMaterial()` `canRegisterProduct()`
세 판정이 있고, **왜 세 목록이 서로 다른지**가 javadoc 에 근거까지 적혀 있다.

`@PreAuthorize` 는 저장소 전체에서 `TradeController` 의 발송·수령 두 곳뿐이다.
**그 둘이 관례가 아니라 예외다.** `ProductModelController` javadoc 이 그 이유를 적어 두었다:

> 역할 검사를 `@PreAuthorize` 로 하지 않는다. 그것은 토큰의 역할 클레임을 보는데,
> 토큰은 발급 시점의 스냅샷이라 정지되거나 역할이 바뀐 계정이 만료 전까지 계속 통과한다.

따라서 이 문서의 일은 "없는 인가를 새로 설계하는 것" 이 아니라
**흩어져 있는 판정을 한 표로 모으고, 비어 있거나 어긋난 칸을 드러내는 것**이다.

---

## 1. 결정 — 인가를 어디서 거는가

세 지점 중 **서비스 계층을 기본**으로 한다.

| 지점 | 역할 | 정본인가 |
| --- | --- | --- |
| `SecurityConfig` URL 규칙 | **인증 여부만** (`permitAll` / `authenticated`). 역할 규칙을 넣지 않는다 | — |
| `@PreAuthorize` (메서드) | 도메인 역할 에러코드가 없는 엔드포인트의 **외곽 조기 차단**. 예외적으로만 | ❌ |
| **서비스 계층 DB 재조회** | **역할 · 정지 여부 · 소유권 판정** | ✅ **정본** |

### 왜 URL 규칙이 아닌가 — 별칭 매핑 때문에 조용히 뚫린다

`processing` 컨트롤러들이 **경로를 두 개씩** 매핑하고 있다.

```java
@RequestMapping({"/processing-jobs","/api/v1/processing-jobs"})
@RequestMapping({"/quotes","/api/v1/quotes"})
@RequestMapping({"/processing-requests", "/api/v1/processing-requests"})
@GetMapping({"/processors/search","/api/v1/processors/search"})
```

여기에 `/api/v1/quotes/**` 만 역할 규칙을 걸면 **`/quotes/**` 로 그대로 들어올 수 있다.**
컨트롤러는 두 경로에 다 응답하는데 필터 체인은 한쪽만 안다. 규칙과 매핑이
서로 다른 파일에 있어 **컴파일도 테스트도 통과하고, 뚫린 뒤에야 드러난다.**

경로 관례가 통일돼 있지도 않다 — `/api/materials`·`/api/evidence` 는 무버전이고
`/api/v1/trades`·`/api/v1/listings`·`/api/v1/wallets`·`/api/v1/product-models` 는 `/v1` 이다.
통일은 프론트와 같은 주기에 하기로 이미 정해져 있으므로, **경로가 움직일 예정인 표면 위에
인가 규칙을 얹지 않는다.** 애노테이션은 핸들러에 붙으므로 별칭 수·경로 변경과 무관하다.

### 왜 `@PreAuthorize` 도 기본이 아닌가 — 두 가지

1. **토큰 스냅샷.** `roles` 는 로그인 시점의 값이다. 정지된 계정, 역할이 바뀐 계정이
   토큰 만료(`app.jwt.access-token-ttl`)까지 계속 통과한다. DB 를 다시 읽는 검사만
   "지금" 을 본다.
2. **에러 코드가 기계 계약이다.** `TradeErrorCode` javadoc 에 명시돼 있다 —
   프론트가 `code` 문자열로 분기한다. 이미 `LISTING_ROLE_NOT_ALLOWED`,
   `TRADE_ROLE_NOT_ALLOWED`, `MATERIAL_ROLE_NOT_ALLOWED`, `WALLET_ROLE_NOT_ALLOWED`,
   `PROCESSING_FORBIDDEN` 같은 구체 코드가 나가고 있다.
   그 엔드포인트 앞에 `@PreAuthorize` 를 세우면 응답이 **일반 `FORBIDDEN` 으로 바뀐다.**
   보안이 나아지지 않고 화면만 깨진다.

### 그래서 `@PreAuthorize` 를 남기는 자리

**도메인 역할 에러코드가 없는 엔드포인트**, 현재는 `PATCH /trades/{id}/ship` 과
`POST /trades/{id}/receive/confirm` 두 곳이다. 여기서 애노테이션은
**"엉뚱한 역할을 DB 조회 전에 끊는 외곽 문"** 이고 정본이 아니다.
정본은 그 뒤의 서비스 검사다 (`-287` 에서 채운다 — §5).

새 엔드포인트를 만들 때의 기준:

> 역할 위반을 **구체 에러코드로 알려야 하면** 서비스에서만 검사한다.
> 일반 403 으로 충분하면 `@PreAuthorize` 를 얹어도 되지만,
> **그것만으로 끝내지 말고 서비스에서 DB 를 다시 읽는다.**

`SecurityConfig` 에는 어떤 경우에도 역할 규칙을 넣지 않는다.

---

## 2. 표기

| 표기 | 의미 |
| --- | --- |
| `●` | 허용 |
| `●소` | 허용하되 **그 리소스의 소유자·당사자**여야 한다 (역할만으로는 부족) |
| `○` | 거부 |
| `?` | **미결** — §6 에서 사람이 정한다 |
| `PUBLIC` | 인증 없이 접근 |
| `AUTH` | 로그인만. 역할을 보지 않는다 |

"강제" 열은 그 규칙이 실제로 코드 어디에 있는지다. 표와 코드가 갈라지지 않게 하려고 적는다.

---

## 3. Matrix

### 3-1. 공통 · 인증

| Method | 경로 | SUP | BRA | PRO | CON | ADM | 강제 | 근거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/health` | PUBLIC | | | | | `SecurityConfig` permitAll | 배포 파이프라인(`scripts/health-check.sh`)이 인증 없이 호출한다. **경로·인증 요구를 바꾸면 롤백이 오작동한다** |
| POST | `/api/v1/auth/signup/consumer` | PUBLIC | | | | | permitAll | 가입은 인증 전이다. 항상 `CONSUMER` 로 만든다 |
| POST | `/api/v1/auth/signup/business` | PUBLIC | | | | | permitAll + `SignupService` | 역할을 바디로 받되 `canOwnWallet()` 인 3개만 허용 → **ADMIN 은 API 로 만들 수 없다.** 자기 신고인 것이 §6-2 |
| POST | `/api/v1/auth/login` | PUBLIC | | | | | permitAll | 이메일 로그인. `EmailLoginService` (**다른 담당자**) |
| POST | `/api/v1/auth/wallet/nonce` | PUBLIC | | | | | permitAll | 로그인 전이므로 토큰이 없다. 지갑 미연결은 `WALLET_NOT_LINKED` |
| POST | `/api/v1/auth/wallet/verify` | PUBLIC | | | | | permitAll + `WalletAuthService` | 서명 검증 후 `canOwnWallet()` 아닌 계정은 `WALLET_ROLE_NOT_ALLOWED` |

### 3-2. 지갑 (`/api/v1/wallets`)

| Method | 경로 | SUP | BRA | PRO | CON | ADM | 강제 | 근거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/nonce` | ● | ● | ● | ○ | ○ | `WalletRegistrationService` `canOwnWallet()` | CONSUMER 는 지갑 없이 서비스를 쓰고 ADMIN 은 온체인 작업 주체가 아니다 (`UserRole#canOwnWallet` javadoc) |
| POST | `/` | ● | ● | ● | ○ | ○ | 같음 | 같음 |
| GET | `/me` | ●소 | ●소 | ●소 | AUTH | AUTH | 리포지토리 `findAllByUserId` | 자기 것만 조회된다. CONSUMER·ADMIN 은 빈 목록 — 막을 필요가 없다 |
| DELETE | `/{walletId}` | ●소 | ●소 | ●소 | ○ | ○ | `findByIdAndUserId` | 남의 지갑 id 를 넣으면 `WALLET_NOT_FOUND`. **역할 검사는 없다** — 지갑을 가질 수 없는 역할은 애초에 지울 지갑이 없다 |

### 3-3. 소재 (`/api/materials`)

| Method | 경로 | SUP | BRA | PRO | CON | ADM | 강제 | 근거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/` | ● | ● | ○ | ○ | ○ | `MaterialRegistrationService` `canRegisterMaterial()` | **PROCESSOR 가 빠진 것이 핵심.** 가공업체의 소재는 가공 확정의 산출물이지 직접 등록 대상이 아니다. 열어 주면 "모든 폐소재는 등록 가공업체를 거쳐 검증된다" 는 전제가 무너진다 |
| GET | `/{materialId}` | AUTH | AUTH | AUTH | `?` | AUTH | `anyRequest().authenticated()` | 소재 정보는 추적성이 목적이라 누가 보든 같은 값이어야 한다. CONSUMER 는 §6-4 |
| GET | `/me` | ●소 | ●소 | ●소 | AUTH | AUTH | JWT `sub` 강제 | 보유자를 파라미터로 받지 않는다. 미보유 역할은 빈 목록 |
| GET | `/{materialId}/balance` | AUTH | AUTH | AUTH | `?` | AUTH | 인증만 | 잔량은 추적성의 일부. CONSUMER 는 §6-4 |
| GET | `/{materialId}/lineage` | AUTH | AUTH | AUTH | `?` | AUTH | 인증만 | 혈통도 같다. **QR Journey(FR-051)가 비회원 조회를 요구**하는 것과의 관계는 §6-3 |

### 3-4. Marketplace 판매 글 (`/api/v1/listings`)

| Method | 경로 | SUP | BRA | PRO | CON | ADM | 강제 | 근거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/` | ● | ○ | ○ | ○ | ○ | `ListingCommandService` SUPPLIER + `isActive()` | **BRAND 가 빠진다.** 브랜드는 소재를 사서 제품을 만드는 쪽이라 판매자가 되지 않는다. `canRegisterMaterial()`(SUP·BRA)을 쓰지 않는 이유 — 등록과 판매는 다른 권한이다. `05-api.md` 와 어긋난다 → §7-1 |
| GET | `/` | AUTH | AUTH | AUTH | `?` | AUTH | 인증만 | 소재 시세·재고는 사업자 간 거래 정보라 목록까지 인증을 요구하는 것이 의도적이다. CONSUMER 는 §6-4 |
| GET | `/{listingId}` | AUTH | AUTH | AUTH | `?` | AUTH | 인증만 | 같음 |
| DELETE | `/{listingId}` | ●소 | ○ | ○ | ○ | ○ | `LISTING_NOT_OWNED` | 본인 글만. 진행 중 거래가 있으면 거부. 행은 남고 상태만 `CANCELED` |

### 3-5. 소재 거래 (`/api/v1/trades`)

| Method | 경로 | SUP | BRA | PRO | CON | ADM | 강제 | 근거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/quote` | AUTH | AUTH | AUTH | AUTH | AUTH | 인증만 | 아무것도 바꾸지 않는 금액 계산. 판매 글 정보로 계산되므로 목록 열람 권한과 같다 |
| POST | `/` | `?` | ● | `?` | ○ | ○ | `TradeCommandService` BRAND + `isActive()` | 구매는 BRAND 만. 다만 그 근거가 **정책이 아니라 컨트랙트 현재 구현**이라 `UserRole` 로 올리지 않았다 (`TradeCommandService` javadoc). SUP·PRO 는 §6-5 |
| GET | `/` | ●소 | ●소 | ●소 | ●소 | ●소 | JWT `sub` 강제 | 남의 목록을 볼 수 없다. `side=BUY/SELL` 로 방향만 고른다 |
| GET | `/{tradeId}` | ●소 | ●소 | ○ | ○ | ○ | `TRADE_NOT_PARTICIPANT` | 당사자(판매자·구매자)만 |
| POST | `/{tradeId}/payment/ready` | ○ | ●소 | ○ | ○ | ○ | 당사자 검사 | 청구 대상은 구매자다 |
| POST | `/{tradeId}/payment/approve` | ○ | ●소 | ○ | ○ | ○ | 당사자 검사 | 실제 청구 시점. 금액·tid 를 클라이언트가 말하게 두지 않는다 |
| POST | `/{tradeId}/cancel` | ●소 | ●소 | ○ | ○ | ○ | 당사자 검사 | 양쪽 모두 가능하고 발송 전까지만. 판매 잔여 수량이 복구된다 |
| PATCH | `/{tradeId}/ship` | ●소 | ○ | ○ | ○ | ○ | `@PreAuthorize("hasRole('SUPPLIER')")` + `TradeShipmentService` | **판매 등록 자격(SUPPLIER)과 항상 같은 목록이어야 한다.** 어긋나면 결제까지 됐는데 발송이 막히는 거래가 생기고 판매자가 끝낼 방법이 없다 |
| POST | `/{tradeId}/receive/confirm` | ○ | ●소 | ○ | ○ | ○ | `@PreAuthorize("hasRole('BRAND')")` + `TradeReceiptService` | 구매 자격(BRAND)과 같은 목록. 소유권이 실제로 넘어가는 자리 |

### 3-6. Processing (**다른 담당자 영역**)

| Method | 경로 | SUP | BRA | PRO | CON | ADM | 강제 | 근거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/processing-requests` | ○ | ● | ○ | ○ | ○ | `ProcessingRequestService` BRAND | 가공을 의뢰하는 주체는 브랜드다 |
| GET | `/processing-requests` | ○ | ●소 | ●소 | ○ | ○ | `ProcessingRequestQueryService` role→scope | BRAND 는 `brand_id=me`, PROCESSOR 는 `processor_id=me`, **그 외 역할은 `1=0`(빈 목록)** |
| GET | `/processing-requests/{requestId}` | ○ | ●소 | ●소 | ○ | ○ | `PROCESSING_FORBIDDEN` | 당사자 둘만 |
| POST·PATCH | `/processing-requests/{requestId}/cancel` | ○ | ●소 | ○ | ○ | ○ | BRAND + 본인 요청 | 취소는 의뢰한 쪽 |
| POST | `/processing-requests/{requestId}/reject` | ○ | ○ | ●소 | ○ | ○ | PROCESSOR + 지정된 본인 | 반려는 받은 쪽. **반려도 검증 결과다** |
| POST | `/processing-requests/{requestId}/quotes` | ○ | ○ | ●소 | ○ | ○ | PROCESSOR + 지정된 본인 | 견적을 내는 쪽 |
| GET | `/processing-requests/{requestId}/quotes` | ○ | ●소 | ○ | ○ | ○ | BRAND + 본인 요청 | 견적 비교는 의뢰한 쪽 |
| POST | `/quotes/{quoteId}/select` | ○ | ●소 | ○ | ○ | ○ | `ProcessingJobService` BRAND | 견적 선택 시점에 Lock 이 생긴다 (요청 단계 아님) |
| GET | `/processing-jobs/{jobId}` | ○ | ●소 | ●소 | ○ | ○ | 당사자 검사 | |
| POST | `/processing-jobs/{jobId}/payment` | ○ | ●소 | ○ | ○ | ○ | `ProcessingPaymentService` BRAND | 가공비를 내는 쪽 |
| POST | `/processing-jobs/{jobId}/payment/confirm` | ○ | ●소 | ○ | ○ | ○ | 같음 | |
| GET | `/processors/search` | ○ | ● | ○ | ○ | ○ | `ProcessorSearchService` BRAND | 가공업체를 찾는 쪽은 브랜드 |
| GET | `/processing-requests/materials` | ○ | ● | ○ | ○ | ○ | 같음 | 의뢰에 넣을 자기 소재 목록 |

⚠️ 이 표면은 **무프리픽스 별칭**(`/quotes`, `/processing-jobs`, …)을 함께 매핑한다.
§1 의 "URL 규칙을 쓰지 않는다" 결정의 직접적인 근거가 여기다.

### 3-7. 제품 모델 (`/api/v1/product-models`)

| Method | 경로 | SUP | BRA | PRO | CON | ADM | 강제 | 근거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/` | ○ | ● | ○ | ○ | ○ | `ProductModelRegistrationService` `canRegisterProduct()` | 업사이클 제품을 파는 주체가 브랜드다. `canRegisterMaterial()`(SUP·BRA)과 목록이 다른 이유 — 합치면 SUPPLIER 가 제품을 올릴 수 있게 조용히 넓어진다 |
| PATCH | `/{productModelId}/status` | ○ | ●소 | ○ | ○ | ○ | 등록자 대조 | 역할만 보면 브랜드 아무나 남의 제품을 단종시킬 수 있다 |

### 3-8. 증빙 (`/api/evidence`)

| Method | 경로 | SUP | BRA | PRO | CON | ADM | 강제 | 근거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/` | ●소 | ●소 | ●소 | ●소 | ●소 | `EvidenceUploadService` 대상 존재·관계 | **역할을 보지 않는다.** 증빙 열람 자격은 등록자·보유자 관계에서 나오고, 두 SUPPLIER 는 서로의 증빙과 아무 상관이 없다 — 역할이 관계를 대신할 수 없다 |
| GET | `/?referenceType&referenceId` | AUTH / `●소` | AUTH / `●소` | AUTH / `●소` | AUTH / `●소` | AUTH / `●소` | `EvidenceListService` + `EvidenceAccessPolicy#canReadFully` | **응답 모양이 보는 사람에 따라 다르다.** 업로더·소재 등록자·현재 보유자는 전체(`visibility: FULL`), 그 외 인증 사용자는 **종류별 개수만**(`visibility: SUMMARY` — 파일명·`contentPath`·`fileHash`·업로더가 없다). 관계 판정은 `/content` 와 **같은 클래스**를 쓴다 → §6-1 (결정됨) |
| GET | `/{evidenceFileId}/content` | ●소 | ●소 | ●소 | ●소 | ●소 | `EvidenceAccessPolicy` | 업로더 · 소재 등록자 · 그 소재의 현재 보유자. presigned URL 을 쓰지 않은 이유가 이것이다 |
| DELETE | `/{evidenceFileId}` | ●소 | ●소 | ●소 | ●소 | ●소 | `EvidenceDeletionService` | 업로더 또는 소재 등록자만. **온체인에 고정된 증빙은 아무도** 지울 수 없다 (409) |

---

## 4. `ADMIN` — 만능으로 두지 않는다

**`ADMIN` 은 위 표에서 단 한 칸도 `●` 를 갖지 않는다.** 열거하면 다음이 전부다.

| ADMIN 이 할 수 있는 것 | 근거 |
| --- | --- |
| 이메일 로그인 | `EmailLoginService` 는 역할을 보지 않는다 |
| `AUTH` 칸 조회 (소재 상세·잔량·혈통, 판매 글 목록·상세, 거래 견적) | `anyRequest().authenticated()` 에 걸린다. 역할 때문에 열린 것이 아니다 |
| 자기 목록 조회 (`/wallets/me`, `/materials/me`, `/trades`) | 항상 빈 결과. JWT `sub` 로 범위가 강제된다 |

**할 수 없는 것**: 지갑 등록 · 소재 등록 · 제품 등록 · 판매 등록 · 구매 · 결제 ·
발송 · 수령 확인 · 가공 요청 · 견적 · 남의 리소스 조작. 전부 `○` 다.

### 왜 만능이 아닌가

1. **이 시스템의 신뢰 모델은 "당사자 서명" 이다.** ADMIN 이 남의 리소스를 대신 조작할 수
   있으면 온체인에 남은 서명이 "누가 실제로 했는가" 를 증명하지 못한다.
   같은 이유로 **ADMIN 은 분쟁의 시비도 판정하지 않는다** — 모니터링만이고,
   확정은 당사자 합의 + 양측 서명이다.
2. **ADMIN 은 지갑을 가질 수 없다** (`canOwnWallet()` = false). 온체인 행위 주체가 아니므로
   서명이 필요한 어떤 흐름에도 들어갈 수 없다. 만능으로 열어 두면
   **"서버 DB 만 바뀌고 체인은 그대로" 인 상태**를 만들 수 있고, 그것이 가장 나쁜 불일치다.
3. **ADMIN 계정은 API 로 만들 수 없다** (`SignupService` 가 `canOwnWallet()` 로 막는다).
   DB 에 직접 넣어야만 존재한다. 열거되지 않은 권한을 주는 것은 그 계정의 성격과 맞지 않는다.

### ADMIN 이 필요해질 자리 — 아직 엔드포인트가 없다

`05-api.md` 는 `/admin/users` 조회·정지/활성, `/admin/processors` 심사,
`/admin/blockchain-transactions` 를 예정하고 있다. **하나도 구현돼 있지 않다.**
그것들이 생길 때 ADMIN 권한을 이 문서에 추가한다. 원칙은 **조회 + 계정 상태 변경까지**이고
도메인 리소스 조작은 열지 않는다. 가공업체 심사는 `processor_profile` 에
심사 상태 컬럼이 없어 스키마 결정이 선행한다 (§6-6).

---

## 5. `-287` 에서 구현할 것 / 하지 않을 것

미결(`?`)이 아닌 칸은 §3 의 "강제" 열이 보여 주듯 **거의 다 이미 구현되어 있다.**
남은 실제 갭은 하나다.

### 갭 — 발송·수령이 DB 를 다시 읽지 않는다

`PATCH /trades/{id}/ship` 과 `POST /trades/{id}/receive/confirm` 의 역할 판정은
`@PreAuthorize` **뿐**이다. `TradeShipmentService` 는 `trade.sellerId` 대조만 하고,
`TradeReceiptService` 는 `trade.buyerId` 대조만 한다. 둘 다 `User` 를 읽지 않는다.

그래서 **정지된 계정이 토큰 만료까지 발송·수령 확인을 계속 할 수 있다.**
수령 확인은 보유량이 이전되고 Lock 이 소비되는 자리라 되돌리기 어렵다.
주변 모든 서비스(`ListingCommandService`, `TradeCommandService`, …)가 `isActive()` 를
확인하는데 이 둘만 빠져 있다 — 설계 의도가 아니라 누락이다.

**조치**: 두 서비스에 DB 재조회 검사를 넣는다. `@PreAuthorize` 는 외곽 문으로 남긴다
(§1). 에러코드 `TRADE_SELLER_SUSPENDED` · `TRADE_SELLER_ROLE_NOT_ALLOWED` 를 추가한다 —
구매자 쪽은 기존 `TRADE_BUYER_SUSPENDED` · `TRADE_ROLE_NOT_ALLOWED` 를 그대로 쓴다.
**기존 코드를 바꾸거나 지우지 않으므로 프론트 계약은 가산만 된다.**

### 하지 않는 것

- **`SecurityConfig` 에 역할 규칙 추가** — §1 의 결정
- **`/api/health`** — 경로·인증 요구 무변경 (인프라 계약)
- **`/api/v1/auth/**` permitAll 축소** — §6-7 미결
- **`processing` 패키지** — 다른 담당자 영역이고, 이미 서비스 계층에서 역할·정지를 본다
- **`EmailLoginService`** — 다른 담당자 영역 (Songha Jung). 변경 필요 없음 (§8)
- **역할 에러코드가 이미 있는 엔드포인트에 `@PreAuthorize` 추가** — 응답 `code` 가
  `FORBIDDEN` 으로 덮여 프론트 분기가 깨진다 (§1)

---

## 6. 미결 — 사람이 정할 항목

억지로 정하지 않았다. 각 항목은 **누가 정해야 하는지**까지 적는다.

### 6-1. `GET /api/evidence` 목록에 인가가 없었다 — ✅ 결정·구현 (2026-09-08)

컨트롤러가 JWT 를 아예 받지 않았다. `referenceType` + `referenceId` 만 주면
**인증된 누구나 남의 소재 증빙 목록**(파일명·타입·`contentPath`·`fileHash`·업로더)을 볼 수
있었다. 바이트는 `EvidenceAccessPolicy` 가 막지만 목록은 그대로 나갔고,
`계근표_OO상사.pdf` 같은 이름 하나로 거래 상대가 드러난다 (계근표에는 중량·단가가 찍힌다).

선택지는 A(목록도 관계로 막는다) · B(파일명을 빼고 개수만) · C(현재 유지) 였다.

**결정: A + B.** 보는 사람에 따라 응답 모양을 바꾼다 (결정자 공병우, 프론트 화면 확인 후).

| 보는 사람 | 응답 |
| --- | --- |
| 업로더 · 소재 등록자 · 소재 현재 보유자 | `visibility: FULL` + `files[]` (기존 필드 전부) + `summary[]` |
| 그 외 인증 사용자 | `visibility: SUMMARY` + `summary[]` 만. **`files` 키가 없다** |

- **A 단독을 쓰지 않은 이유**: 소재 상세는 추적성 화면이고 "계근표 2건, 작업 전 사진 3건
  있음" 은 공개 가치가 있다. 목록을 통째로 403 으로 막으면 그 섹션이 대부분 방문자에게
  빈칸이 된다
- **C 를 쓰지 않은 이유**: "증빙이 존재한다" 는 추적성 정보이지만 **파일명·해시·업로더는
  아니다.** 개수까지가 추적성이고 그 뒤는 거래 정보다
- **관계 판정은 `EvidenceAccessPolicy` 에 넣었다** (`canReadFully`). 새로 만들면
  `/content` 는 열리는데 목록은 막히는(또는 그 반대) 상태가 조용히 생긴다
- **민감 필드를 `null` 로 두는 안은 버렸다.** "값이 없는 것" 과 "가려진 것" 이 구분되지
  않고, 다음에 추가되는 필드의 기본값이 "노출" 이 된다. 그래서 **가려졌다는 사실을
  `visibility` 로 응답에 싣는다**
- **별도 엔드포인트(`/api/evidence/summary`) 안도 버렸다.** 프론트가 자기가 어느 쪽인지
  미리 알 수 없고(관계 판정은 `material_balance` 조회다), 403 을 보고 재시도하는 방식은
  그 403 자체가 관계 유무를 알려주는 신호가 된다

**응답이 배열 → 객체로 바뀌므로 프론트 변경이 필요하다** (`EvidenceFile[]` →
`{ visibility, files?, summary }`). 기존 항목 필드는 하나도 지우거나 바꾸지 않았다.

**남은 것 — 비회원(미인증)**: 소재 상세는 공개 라우트인데 이 엔드포인트는 지금도 401 이다.
비회원에게 요약을 여는 문제는 §6-3 · §6-4 (기획 결정)에 걸려 있어 **미결**이다.
이번 변경은 인증된 사용자 사이의 구분만 한다.

### 6-2. 사업자 역할이 심사 없는 자기 신고다

`POST /api/v1/auth/signup/business` 는 permitAll 이고 `role` 을 바디로 받는다.
누구나 `SUPPLIER`·`BRAND`·`PROCESSOR` 로 가입할 수 있다.
**이 문서의 모든 역할 칸이 그만큼만 보장한다** — 역할 인가는 "권한 분리" 이지
"신원 검증" 이 아니다. `05-api.md` 는 ADMIN 의 가공업체 승인/반려를 예정하는데
그 상태를 담을 컬럼이 없다.

- 선택지 A: 사업자 가입을 `PENDING` 으로 만들고 승인 후 역할을 활성화 (스키마 변경 필요)
- 선택지 B: 현재대로 두고 **시연 범위임을 명시** (사업자등록번호 진위 확인 수단이 없다)

**정할 사람**: 기획 + User 담당(공병우) + 4번(스키마).

### 6-3. 소재 혈통·상세를 비회원이 볼 수 있어야 하는가

FR-051 은 `GET /qr/{qrToken}` 을 **회원가입·지갑 없이** 조회 가능하도록 요구한다.
QR Journey 는 제품을 거슬러 소재 혈통까지 보여 주는 것인데,
현재 `/api/materials/{id}/lineage` 는 인증을 요구한다. QR 엔드포인트는 아직 없다.

- 선택지 A: QR 전용 공개 엔드포인트를 따로 만들고 `qr_token` 으로만 접근
  (`qr_token` 을 `id` 와 분리한 이유가 이것이다)
- 선택지 B: `lineage` 자체를 PUBLIC 으로 연다 → **소재 id 가 순번이라 전수 조회가 된다.** 반대
- 선택지 C: QR 응답에 혈통을 미리 담아 내려준다

**정할 사람**: 기획 + Product/QR 담당. A 가 유력하지만 QR 티켓이 아직 없어 지금 정하지 않는다.

### 6-4. `CONSUMER` 가 B2B 소재 정보를 볼 수 있어야 하는가

`GET /api/materials/**` 와 `GET /api/v1/listings` 는 현재 `AUTH` 다.
CONSUMER 로 가입하면 **소재 시세와 재고를 전부 볼 수 있다.**
`ListingController` 는 "소재 시세와 재고는 사업자 간 거래 정보" 라고 적어 두었는데,
그 판단대로면 CONSUMER 는 배제되어야 한다. 코드는 배제하지 않는다.

- 선택지 A: 사업자 3역할로 좁힌다 (`SUPPLIER`·`BRAND`·`PROCESSOR`)
  → 소비자 화면이 소재 정보를 쓰지 않는지 확인 필요
- 선택지 B: 추적성 우선 — 소비자가 제품의 소재를 확인하는 것이 이 서비스의 가치라 열어 둔다
  (그렇다면 §6-3 과 함께 다뤄야 한다)

**정할 사람**: 기획. **A/B 로 방향이 갈리는 게 아니라 서비스 성격 자체의 문제**라 코드로 정하지 않는다.

### 6-5. `SUPPLIER`·`PROCESSOR` 가 소재를 살 수 있어야 하는가

`POST /api/v1/trades` 는 BRAND 만이다. 그런데 `TradeCommandService` javadoc 이
**그 근거가 정책이 아니라 컨트랙트의 현재 구현**이라고 밝히고,
그래서 `UserRole` 에 `canBuyMaterial()` 을 만들지 않았다고 적어 두었다.

현실적으로 SUPPLIER 가 남의 소재를 사서 재판매하거나 PROCESSOR 가 부자재를 사는 것은
있을 수 있는 일이다. 반면 `PATCH /trades/{id}/ship` 이 `SUPPLIER` 로 고정돼 있어
**BRAND 가 판매자가 되면 발송이 막힌다** — 구매 역할을 넓히면 발송·판매 역할도 같이 봐야 한다.

- 선택지 A: BRAND 만 유지 (현재). 컨트랙트와 일치
- 선택지 B: 넓힌다 → 판매(§3-4 `POST /listings`)·발송(§3-5 `ship`) 목록을 **함께** 재정의하고
  온체인 `Marketplace` 도 같이 고쳐야 한다

**정할 사람**: 기획 + Blockchain(4번). 서버만 넓히면 체인이 거부해 결제 후 실패한다.

### 6-6. ADMIN 관리 엔드포인트의 권한 범위

`/admin/*` 이 하나도 없다. §4 의 원칙(조회 + 계정 상태 변경까지, 도메인 조작은 안 됨)을
제안하지만 확정하지 않는다. 가공업체 심사는 `processor_profile` 에 심사 상태 컬럼이 없어
**스키마 결정이 선행**한다.

**정할 사람**: 기획 + 4번(스키마).

### 6-7. `/api/v1/auth/**` 와일드카드를 좁힐 것인가

현재 프리픽스 전체가 permitAll 이다. 실제로 공개여야 하는 것은 5개
(`signup/consumer`, `signup/business`, `login`, `wallet/nonce`, `wallet/verify`) 뿐이고,
**앞으로 이 프리픽스 아래 추가되는 어떤 경로도 자동으로 공개된다.**

좁히려면 5개를 명시적으로 열거하게 되는데, 그러면:
- `SecurityConfigTests#publicAuthPathDoesNotRequireToken` 이 고정한 `/api/v1/auth/test` 가
  401 이 된다 → 테스트를 함께 고쳐야 한다 (계약을 바꾸는 것이라 임의로 하지 않는다)
- 목록에서 빠뜨리면 **로그인 자체가 막힌다.** 배포 후에 드러난다
- `EmailAuthController` 는 다른 담당자 영역이다

**정할 사람**: 백엔드 공통 + Songha Jung. 이득(미래 경로 실수 방지)과 위험(로그인 차단)이
둘 다 실재하므로 혼자 정하지 않는다. 좁힐 때는 **`/api/v1/auth/**` 아래 새 컨트롤러를
만들지 않는다는 규칙**만으로도 같은 효과를 낼 수 있다는 점을 같이 검토할 것.

### 6-8. 온체인 `Marketplace.createListing` 이 서버보다 넓다

컨트랙트는 `SUPPLIER_ROLE || BRAND_ROLE` 을 허용하는데 서버는 SUPPLIER 만이다.
서버가 좁으므로 지금 잘못된 데이터가 생기지는 않지만,
**컨트랙트를 직접 부르면 서버가 모르는 판매 글이 체인에만 생긴다.**

**정할 사람**: Blockchain(4번). §6-5 와 함께 결정해야 한다.

---

## 7. 레퍼런스 API 명세와의 차이

구현이 명세와 다른 곳이다. **어긋난 곳은 도메인 규칙(구현)을 따랐고 이유를 적는다.**

### 7-1. `POST /listings` — 명세 `SUPPLIER, BRAND` / 구현 `SUPPLIER`

구현이 좁다. 근거가 코드에 적혀 있다 — 브랜드는 소재를 사서 제품을 만드는 쪽이라
판매자가 되지 않고, **소재를 등록하는 것과 파는 것은 다른 권한**이다.
넓히면 §6-5 처럼 발송 역할까지 함께 봐야 한다. **구현을 따른다.**

### 7-2. 명세에는 있고 구현에 없는 엔드포인트

`GET /materials`(목록·필터), `POST /materials/{id}/confirm`(서명 결과 기록),
`GET /materials/{id}/locks`, Processing 의 배송·수령·시작·결과 제출·승인/반려,
Production 전체, Product 주문 전체, `/admin/*`, `GET /qr/{qrToken}`, `GET /enums`.
**이 문서는 존재하는 엔드포인트만 다룬다.** 상상한 칸을 채워 두면
구현될 때 다시 정해야 하고, 그 사이 "정해졌다" 고 오해된다.

### 7-3. 경로 형태가 다르다

명세는 무버전(`/wallets/*`, `/listings`, …)이고 구현은 `/api/v1/*` 또는 `/api/*` 다.
통일은 프론트와 같은 주기에 한다고 이미 정해져 있다.
**이 표는 구현 경로를 적는다** — 인가를 걸 대상은 실제 매핑이다.

### 7-4. `GET /product-models` 는 명세상 PUBLIC 이다

마켓 노출용이라 공개가 맞지만 **아직 구현돼 있지 않다.**
만들 때 `SecurityConfig` permitAll 에 추가해야 하고,
그때가 이 문서에 `PUBLIC` 행이 하나 더 생기는 시점이다.

---

## 8. `-288` — JWT `roles` 클레임은 이미 채워져 있다

두 로그인 경로 모두 사용자 role 을 접두어 없이 넣고 있다.

```java
// WalletAuthService
jwtTokenService.issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()));
// EmailLoginService
jwtTokenService.issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()));
```

`JwtTokenService` 가 `ROLE_` 접두어를 거부하고, `JwtAuthorityConverter` 가 읽는 쪽에서
붙이고, `JwtSecurityConfig` 의 validator 가 접두어 있는 토큰을 401 로 거부한다.
**`EmailLoginService` 에 필요한 변경은 없다.**

그래서 `-288` 의 남은 일은 발급이 아니라 **확인**이다 — 로그인이 발급한 토큰의
`roles` 가 실제로 `@PreAuthorize` 를 통과·거부시키는지, 그리고
**토큰의 역할과 DB 의 역할이 어긋날 때 DB 가 이긴다는 것**을 통합 수준에서 고정한다.
두 번째가 §1 의 결정이 실제로 지켜지는지를 재는 유일한 방법이다.
