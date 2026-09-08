# Role 별 기능 권한 Matrix 확정

- 티켓: S15P21E104-286 (`[COMMON]`). 구현은 S15P21E104-287 (`[BE]`), 클레임은 S15P21E104-288 (`[BE]`)
- 결정자: 공병우 (인프라 · User/Wallet · Product/Material)
- 날짜: 2026-09-08
- 역할: `SUPPLIER` `BRAND` `PROCESSOR` `CONSUMER` `ADMIN` (`users.role` ENUM 과 1:1, `UserRole`)
- **2026-09-08 갱신**: §6 의 미결 8건이 전부 사람에 의해 판정됐다 (7건 결정 + 1건 보류). §6 은 이제
  **미결 목록이 아니라 결정 기록**이고, §3 표에 `?` 칸은 남아 있지 않다.
  결정이 뒤집힐 때 근거를 다시 찾지 않도록 각 항목에 **누가·언제·무엇을·왜** 를 적었다.
  §6-6(ADMIN 관리 엔드포인트)만 "보류" 이고, §6-3(비회원 QR 조회)은
  "결정됨 · 후속 티켓 필요" 다.

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
정본은 그 뒤의 서비스 검사다 (아직 없다 — §5. `trade` 담당자에게 넘겼다).

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
| `?` | ~~미결~~ — **2026-09-08 로 전량 해소됐다.** 표에 `?` 는 없다. 어떤 값으로 정해졌고 왜인지는 §6 의 해당 항목에 있다. 새로 미결이 생기면 이 표기를 다시 쓰고 §6 에 항목을 추가한다 |
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
| GET | `/{materialId}` | AUTH | AUTH | AUTH | AUTH | AUTH | `anyRequest().authenticated()` | 소재 정보는 추적성이 목적이라 누가 보든 같은 값이어야 한다. CONSUMER 도 본다 — §6-4 결정(열어 둔다) |
| GET | `/me` | ●소 | ●소 | ●소 | AUTH | AUTH | JWT `sub` 강제 | 보유자를 파라미터로 받지 않는다. 미보유 역할은 빈 목록 |
| GET | `/{materialId}/balance` | AUTH | AUTH | AUTH | AUTH | AUTH | 인증만 | 잔량은 추적성의 일부. CONSUMER 도 본다 — §6-4 결정 |
| GET | `/{materialId}/lineage` | AUTH | AUTH | AUTH | AUTH | AUTH | 인증만 | 혈통도 같다. **비회원**은 여기가 아니라 **QR 전용 공개 경로**로 본다 — §6-3 결정(후속 티켓). 이 경로의 인증 요구는 그대로 둔다 |

### 3-4. Marketplace 판매 글 (`/api/v1/listings`)

| Method | 경로 | SUP | BRA | PRO | CON | ADM | 강제 | 근거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/` | ● | ○ | ○ | ○ | ○ | `ListingCommandService` SUPPLIER + `isActive()` | **BRAND 가 빠진다.** 브랜드는 소재를 사서 제품을 만드는 쪽이라 판매자가 되지 않는다. `canRegisterMaterial()`(SUP·BRA)을 쓰지 않는 이유 — 등록과 판매는 다른 권한이다. `05-api.md` 와 어긋난다 → §7-1 |
| GET | `/` | AUTH | AUTH | AUTH | AUTH | AUTH | 인증만 | 소재 시세·재고는 사업자 간 거래 정보라 목록까지 인증을 요구하는 것이 의도적이다. 다만 **역할로 좁히지는 않는다** — §6-4 결정 |
| GET | `/{listingId}` | AUTH | AUTH | AUTH | AUTH | AUTH | 인증만 | 같음 |
| DELETE | `/{listingId}` | ●소 | ○ | ○ | ○ | ○ | `LISTING_NOT_OWNED` | 본인 글만. 진행 중 거래가 있으면 거부. 행은 남고 상태만 `CANCELED` |

### 3-5. 소재 거래 (`/api/v1/trades`)

| Method | 경로 | SUP | BRA | PRO | CON | ADM | 강제 | 근거 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/quote` | AUTH | AUTH | AUTH | AUTH | AUTH | 인증만 | 아무것도 바꾸지 않는 금액 계산. 판매 글 정보로 계산되므로 목록 열람 권한과 같다 |
| POST | `/` | ○ | ● | ○ | ○ | ○ | `TradeCommandService` BRAND + `isActive()` | 구매는 BRAND 만 — §6-5 결정(현재 유지). 근거가 **정책이 아니라 컨트랙트 현재 구현**이라 `UserRole` 로 올리지 않는다 (`TradeCommandService` javadoc). 넓히면 판매·발송 목록과 온체인 `Marketplace` 를 함께 고쳐야 한다 |
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

### 2026-09-08 결과 — `-287` 에서 바꾼 코드는 없다

§6 의 8건이 결정된 뒤 **표의 모든 칸을 실제 코드와 다시 대조했다.**
`?` 였던 칸의 결정이 전부 "현재 유지" 또는 "다른 티켓" 이라,
**표와 코드가 어긋나는 칸이 하나도 없다.** 확인한 것:

- 표에서 특정 역할만 허용인 칸 → 해당 서비스에 DB 재조회 검사가 전부 있다
  (`canRegisterMaterial` `canRegisterProduct` `canOwnWallet` 및 각 서비스의 역할·`isActive()` 검사)
- 표에서 허용인데 코드가 막는 칸 → 없다. `AUTH` 칸은 `anyRequest().authenticated()` 뿐이고
  역할 제약이 없다(§6-4 의 결정을 이미 만족한다)
- `@PreAuthorize` 는 여전히 `TradeController` 두 곳뿐이다. **새로 추가하지 않았다** (§1)
- `SecurityConfig` 무변경 — `/api/health`(§3-1, 인프라 계약)과 `/api/v1/auth/**`(§6-7 결정) 그대로

**아래 갭은 남아 있다. 다만 `-287` 이 아니라 `trade` 담당자에게 넘긴다** — 이유는
그 절 끝에 적었다.

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

> **2026-09-08 — 이 조치는 `trade` 담당자에게 넘긴다 (미해결).**
> 처음 이 문서는 `-287` 에서 채운다고 적었다. 그런데 고쳐야 하는 파일이
> `trade/service/TradeShipmentService.java` · `TradeReceiptService.java` ·
> `TradeErrorCode.java` 로 **전부 `trade` 패키지, 즉 다른 담당자 소유**다.
> 남의 브랜치 밖에서 남의 파일을 고치면 그 담당자는 자기 코드가 언제 왜 바뀌었는지
> 모르고, MR 리뷰 범위도 흐려진다. 그래서 **인가 설계(이 문서)만 여기서 확정하고
> 구현은 넘긴다.**
>
> 넘길 때 필요한 정보는 위 세 단락이 전부다 — **무엇이 뚫려 있는지**(정지된 계정이
> 토큰 만료까지 발송·수령 확인을 할 수 있다), **왜 지금 안 보이는지**(`@PreAuthorize`
> 가 통과시키고 서비스는 `User` 를 읽지 않는다), **어디를 고치는지**(위 두 서비스),
> **에러코드를 어떻게 늘리는지**(가산만).
>
> **막혀 있지는 않다** — 정지 계정이 실제로 생기기 전까지는 드러나지 않는다.
> 다만 수령 확인은 보유량이 이전되고 Lock 이 소비되는 자리라 사후 복구가 어렵다.

### 하지 않는 것

- **`SecurityConfig` 에 역할 규칙 추가** — §1 의 결정
- **`/api/health`** — 경로·인증 요구 무변경 (인프라 계약)
- **`/api/v1/auth/**` permitAll 축소** — §6-7 결정: **하지 않는다**
- **`processing` 패키지** — 다른 담당자 영역이고, 이미 서비스 계층에서 역할·정지를 본다
- **`EmailLoginService`** — 다른 담당자 영역 (Songha Jung). 변경 필요 없음 (§8)
- **역할 에러코드가 이미 있는 엔드포인트에 `@PreAuthorize` 추가** — 응답 `code` 가
  `FORBIDDEN` 으로 덮여 프론트 분기가 깨진다 (§1)

---

## 6. 결정 기록 (원래 미결이었던 8건)

**2026-09-08, 공병우가 8건을 판정했다.** 처음 이 절은 "사람이 정할 항목" 이었다.
억지로 정하지 않고 선택지와 근거만 적어 둔 것을, 그대로 결정 기록으로 바꾼다.
**선택지와 버린 이유를 지우지 않는다** — 결정이 뒤집힐 때 근거를 다시 찾지 않게
하는 것이 이 절의 목적이다.

| 항목 | 결정 | 코드 변경 |
| --- | --- | --- |
| 6-1 증빙 목록 인가 | **A+B** (관계로 막고, 그 외에는 개수만) | 구현 완료 — `backend/fix/evidence-list-authorization` (`5b3af4e`) |
| 6-2 사업자 자기 신고 | **현재 유지.** 시연 범위·신원 확인 수단 없음을 명시 | 없음 (스키마도 없음) |
| 6-3 비회원 소재 이력 | **QR 로는 볼 수 있어야 한다** (A 방향) | 없음 — 실행 티켓 존재 (`-154`·`-105`·`-136`·`-147`·`-109`, 전부 미할당) |
| 6-4 CONSUMER 의 B2B 소재 정보 | **B — 열어 둔다** | 없음 — 현재 코드가 이미 만족 |
| 6-5 소재 구매 역할 | **A — BRAND 만 유지** | 없음 |
| 6-6 ADMIN 관리 엔드포인트 | **⏸ 보류** (관리 기능 설계 선행) | 없음 |
| 6-7 `/api/v1/auth/**` 와일드카드 | **현재 유지.** 좁히지 않는다 | 없음 |
| 6-8 온체인 `createListing` 이 더 넓음 | **서버 동기화로 흡수** | 없음 — `S15P21E104-205` (다른 담당자) |

**8건 중 6건이 "현재대로 유지" 이거나 다른 티켓으로 넘어간다.** 그래서 `-287` 의
정답은 코드를 거의 바꾸지 않는 것이다 (§5).

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

### 6-2. 사업자 역할이 심사 없는 자기 신고다 — ✅ 결정 (2026-09-08, 공병우)

`POST /api/v1/auth/signup/business` 는 permitAll 이고 `role` 을 바디로 받는다.
누구나 `SUPPLIER`·`BRAND`·`PROCESSOR` 로 가입할 수 있다.
`05-api.md` 는 ADMIN 의 가공업체 승인/반려를 예정하는데 그 상태를 담을 컬럼이 없다.

선택지는 A(`PENDING` 가입 + 승인 후 역할 활성화, 스키마 변경) · B(현재 유지 + 시연 범위 명시) 였다.

**결정: B — 현재대로 유지한다. 심사 없이 자기 신고다.**

그리고 그 한계를 여기에 못박는다:

> **이 시스템에는 사업자 신원을 확인하는 수단이 없다.** 사업자등록번호 진위를 조회하지
> 않고, 서류를 받지도 않는다. 따라서 `SUPPLIER`·`BRAND`·`PROCESSOR` 라는 값은
> "본인이 그렇게 신고했다" 는 뜻뿐이다. **이것은 시연 범위의 의도된 한계다.**
>
> **이 문서의 모든 역할 칸이 그만큼만 보장한다** — 역할 인가는 **권한 분리**이지
> **신원 검증**이 아니다. 어떤 칸을 "이 역할만 허용" 으로 좁혀도 그것이
> "실제 그 업종의 사업자만 한다" 를 의미하지 않는다.

- **A 를 쓰지 않은 이유**: 심사 주체(ADMIN 관리 화면)가 없고, `PENDING` 을 담을 컬럼도
  없다(`processor_profile` 에 심사 상태 컬럼이 없다 — §6-6 과 같은 선결 조건이다).
  심사 흐름 없이 상태만 넣으면 **아무도 승인해 주지 않아 가입이 그 자리에서 멈춘다.**
- **스키마 변경 없음.** `users`·`processor_profile` 어느 쪽도 건드리지 않는다
  (운영은 `ddl-auto: validate` 라 엔티티만 고치면 기동이 깨진다).
- 뒤집을 때 필요한 것: 심사 상태 컬럼 + ADMIN 심사 엔드포인트(§6-6) + 화면.
  세 개가 같이 와야 하므로 **§6-6 이 먼저 풀려야 A 로 갈 수 있다.**

### 6-3. 소재 혈통·상세를 비회원이 볼 수 있어야 하는가 — ✅ 결정 (2026-09-08, 공병우) · **후속 티켓 필요**

FR-051 은 `GET /qr/{qrToken}` 을 **회원가입·지갑 없이** 조회 가능하도록 요구한다.
QR Journey 는 제품을 거슬러 소재 혈통까지 보여 주는 것인데,
현재 `/api/materials/{id}/lineage` 는 인증을 요구한다. QR 엔드포인트는 아직 없다.

**결정: 로그인 없이 QR 을 찍으면 볼 수 있어야 한다 = 선택지 A 방향(QR 전용 공개 경로).**

- **B(`lineage` 자체를 PUBLIC) 는 거부한다.** 소재 `id` 가 순번이라 전수 조회가 된다.
  공개 범위를 `qr_token` 을 아는 사람으로 한정하는 것이 `qr_token` 을 `id` 와 분리해 둔 이유다.
- 그래서 **`/api/materials/**` 의 인증 요구는 이번에도 그대로 둔다.** 표 §3-3 이 여전히
  `AUTH` 인 것은 이 결정과 모순이 아니다 — 비회원 경로는 다른 URL 로 생긴다.

**이번 범위가 아니다 — 구현하지 않았다.** 공개할 응답의 모양(제품 → 생산 → 소재 혈통
중 어디까지, 보유자·단가 같은 거래 정보를 빼는지)을 정하는 것이 선행이다.
**공개 경로를 먼저 만들면 그 응답이 곧 계약이 되어 나중에 필드를 빼는 것이
파괴적 변경이 된다.**

**실행 티켓은 이미 있다** (2026-09-08 Jira 확인, 전부 미할당):
`S15P21E104-154`(BE Passport Public API Security 정책 적용) ·
`-105`(BE Passport 공개 조회 정책 구현) · `-136`(COMMON Product Passport URL 정책 정의) ·
`-147`(FE Public Passport Route) · `-109`(FE Consumer 전용 간소화 UI).
Epic `-10`(Material Passport / Traceability) 과 `-11`(QR) 에 관련 티켓이 44건 있다.

**티켓을 새로 만들 필요는 없다.** 위 티켓에 배정하면 된다. 그 티켓들에서 해야 할 일:

1. `GET /qr/{qrToken}` 을 `SecurityConfig` permitAll 에 추가 (§1 의 "URL 규칙은 인증
   여부만" 결정과 일치한다 — 역할 규칙이 아니다)
2. 응답에서 **거래 정보를 제거**한다. 비회원에게 나가는 것은 추적성(소재 종류·원산·가공
   이력·해시)까지이고 보유자·단가·거래 상대는 아니다 (§6-1 에서 증빙 목록에 적용한 것과 같은 선)
3. `qr_token` 은 추측 불가능해야 한다 — 순번이면 B 를 거부한 이유가 그대로 되살아난다
4. **증빙 목록(§6-1)의 비회원 취급도 여기서 같이 정해진다.** §6-1 은 "비회원은 미결"
   상태로 인증된 사용자 사이의 구분만 했다. 이 결정에 따르면 비회원도 QR 경로에서는
   **요약(종류별 개수)까지** 볼 수 있어야 하고, `files[]` 는 여전히 안 된다.
   `/api/evidence` 자체를 열지 말고 **QR 응답에 요약을 담는다** — 그래야 공개 범위가
   `qr_token` 을 아는 사람으로 유지된다

**담당**: Product/QR. 기획 확인은 끝났다(비회원 조회 필요).

### 6-4. `CONSUMER` 가 B2B 소재 정보를 볼 수 있어야 하는가 — ✅ 결정 (2026-09-08, 공병우)

`GET /api/materials/**` 와 `GET /api/v1/listings` 는 현재 `AUTH` 다.
CONSUMER 로 가입하면 **소재 시세와 재고를 전부 볼 수 있다.**
`ListingController` 는 "소재 시세와 재고는 사업자 간 거래 정보" 라고 적어 두었는데,
그 판단대로면 CONSUMER 는 배제되어야 한다. 코드는 배제하지 않는다.

선택지는 A(사업자 3역할로 좁힌다) · B(추적성 우선, 열어 둔다) 였다.

**결정: B — 열어 둔다. 좁히지 않는다.**

근거는 서비스 성격이다. 소비자가 제품의 소재를 거슬러 확인할 수 있다는 것이 이
플랫폼의 가치이고, 그 확인을 "사업자만" 으로 좁히면 추적성이 B2B 내부 기능이 된다.
§6-3(비회원 QR 조회를 열기로 한 결정)과 같은 방향이다 — 두 결정은 함께 봐야 하고,
비회원에게까지 열기로 한 정보를 로그인한 소비자에게 막는 것은 앞뒤가 맞지 않는다.

**코드 변경 없음 — 확인된 사실이다.** 이 경로들은 `SecurityConfig` 의
`anyRequest().authenticated()` 에 걸려 있고 **역할 제약이 아예 없다.** 즉 B 는
**지금 코드가 이미 만족한다.** `-287` 에서 여기에 손대지 않는 것이 결정을 지키는 것이다.

`ListingController` javadoc 의 "사업자 간 거래 정보" 라는 서술은 **왜 비회원에게
열지 않았는가**(= 인증을 요구하는 이유)에 대한 것으로 읽는다. 역할로 좁히라는
지시가 아니다. 표 §3-4 의 CON 칸을 `AUTH` 로 채운 근거가 이것이다.

### 6-5. `SUPPLIER`·`PROCESSOR` 가 소재를 살 수 있어야 하는가 — ✅ 결정 (2026-09-08, 공병우)

**결정: A — BRAND 만 유지한다. 현재가 맞다. 변경 없음.**

근거는 **온체인과의 일치**다. `Marketplace` 컨트랙트의 구매 경로가 BRAND 기준이므로
서버만 넓히면 결제까지 진행된 뒤 체인이 거부한다 — 그 실패는 되돌리기 가장 어려운
자리에서 난다. 그리고 구매를 넓히면 판매(§3-4 `POST /listings`)와
발송(§3-5 `ship`) 목록을 **함께** 재정의해야 하는데(아래 원문 참고),
그 세 곳을 동시에 옮기는 것은 컨트랙트 변경과 같은 주기여야 한다.

- 표 §3-5 `POST /` 의 SUP·PRO 칸을 `○` 로 확정했다.
- `UserRole` 에 `canBuyMaterial()` 을 **만들지 않는다.** 근거가 정책이 아니라 컨트랙트
  현재 구현이라는 사실을 `TradeCommandService` javadoc 에 남겨 두는 쪽이 정확하다 —
  `UserRole` 로 올리면 "정책으로 정해졌다" 로 읽힌다.
- 뒤집을 때 필요한 것: 컨트랙트 role 변경 + 판매·발송 목록 재정의 + 서버.
  **Blockchain(4번)이 먼저 움직여야 한다.**

아래는 결정 전 정리다.

`POST /api/v1/trades` 는 BRAND 만이다. 그런데 `TradeCommandService` javadoc 이
**그 근거가 정책이 아니라 컨트랙트의 현재 구현**이라고 밝히고,
그래서 `UserRole` 에 `canBuyMaterial()` 을 만들지 않았다고 적어 두었다.

현실적으로 SUPPLIER 가 남의 소재를 사서 재판매하거나 PROCESSOR 가 부자재를 사는 것은
있을 수 있는 일이다. 반면 `PATCH /trades/{id}/ship` 이 `SUPPLIER` 로 고정돼 있어
**BRAND 가 판매자가 되면 발송이 막힌다** — 구매 역할을 넓히면 발송·판매 역할도 같이 봐야 한다.

- 선택지 A: BRAND 만 유지 (현재). 컨트랙트와 일치 ← **채택**
- 선택지 B: 넓힌다 → 판매(§3-4 `POST /listings`)·발송(§3-5 `ship`) 목록을 **함께** 재정의하고
  온체인 `Marketplace` 도 같이 고쳐야 한다

### 6-6. ADMIN 관리 엔드포인트의 권한 범위 — ⏸ **보류 (2026-09-08, 공병우)**

`/admin/*` 이 하나도 없다. §4 의 원칙(조회 + 계정 상태 변경까지, 도메인 조작은 안 됨)을
제안하지만 확정하지 않는다. 가공업체 심사는 `processor_profile` 에 심사 상태 컬럼이 없어
**스키마 결정이 선행**한다.

**결정: 보류한다.** 8건 중 유일하게 결정되지 않은 항목이다.
**관리 기능 설계가 선행**해야 한다 — 무엇을 관리하는 화면인지 정해지기 전에 권한 표를
채우면, 엔드포인트가 생길 때 그 표가 이미 "정해진 것" 으로 읽혀 재검토를 막는다.
`-287` 에서 **손대지 않는다.** §4 의 열거(ADMIN 이 할 수 있는 것 3줄)가 현재 전부다.

**풀기 위해 필요한 것**: 관리 기능 설계 → `processor_profile` 심사 상태 컬럼(4번, 스키마)
→ 엔드포인트 → 이 문서에 행 추가. §6-2 의 A 안도 여기에 묶여 있다.

**정할 사람**: 기획 + 4번(스키마).

### 6-7. `/api/v1/auth/**` 와일드카드를 좁힐 것인가 — ✅ 결정 (2026-09-08, 공병우)

**결정: 현재대로 와일드카드를 유지한다. 좁히지 않는다.**

- **위험이 이득보다 크다.** 좁히려면 공개 5개를 명시 열거해야 하고, **하나라도 빠뜨리면
  로그인 자체가 막힌다.** 그 실패는 컴파일·테스트를 통과하고 **배포 후에 드러난다.**
  막아 주는 이득(미래에 이 프리픽스 아래 실수로 추가되는 경로)은 가정이고, 위험은 확정이다.
- **같은 효과를 규칙으로 낼 수 있다.** 아래 규칙을 이 문서의 계약으로 삼는다:

  > **`/api/v1/auth/**` 아래에는 공개여야 하는 엔드포인트만 만든다.**
  > 인증이 필요한 사용자 기능은 이 프리픽스에 두지 않는다 (예: 지갑 등록은
  > `/api/v1/wallets` 다 — 이미 그렇게 되어 있다).

- **`SecurityConfigTests#publicAuthPathDoesNotRequireToken`(`/api/v1/auth/test` 가 401 이
  아님)을 그대로 둔다.** 그 테스트가 고정하는 것이 바로 이 결정이다.
- `EmailAuthController` 는 다른 담당자 영역이고, 이 결정은 그쪽에 아무 변경도 요구하지 않는다.
- 뒤집을 때: 5개 열거 + 위 테스트 수정 + 배포 직후 로그인 수동 확인을 **한 묶음**으로 한다.

현재 프리픽스 전체가 permitAll 이다. 실제로 공개여야 하는 것은 5개
(`signup/consumer`, `signup/business`, `login`, `wallet/nonce`, `wallet/verify`) 뿐이고,
**앞으로 이 프리픽스 아래 추가되는 어떤 경로도 자동으로 공개된다.**

좁히려면 5개를 명시적으로 열거하게 되는데, 그러면:
- `SecurityConfigTests#publicAuthPathDoesNotRequireToken` 이 고정한 `/api/v1/auth/test` 가
  401 이 된다 → 테스트를 함께 고쳐야 한다 (계약을 바꾸는 것이라 임의로 하지 않는다)
- 목록에서 빠뜨리면 **로그인 자체가 막힌다.** 배포 후에 드러난다
- `EmailAuthController` 는 다른 담당자 영역이다

(위 세 항목은 결정 전 정리다. 좁히지 않기로 했으므로 셋 다 그대로 남는다.)

### 6-8. 온체인 `Marketplace.createListing` 이 서버보다 넓다 — ✅ 결정 (2026-09-08, 공병우)

컨트랙트는 `SUPPLIER_ROLE || BRAND_ROLE` 을 허용하는데 서버는 SUPPLIER 만이다.
서버가 좁으므로 지금 잘못된 데이터가 생기지는 않지만,
**컨트랙트를 직접 부르면 서버가 모르는 판매 글이 체인에만 생긴다.**

**결정: 컨트랙트를 막지 않는다. 서버 동기화로 흡수한다.**

- **컨트랙트 role 을 좁히지 않는 이유**: 체인은 서버의 하위 집합이 아니다. 체인에
  기록된 것이 정본이므로, 서버가 모르는 이벤트가 생길 수 있다는 것을 **결함이 아니라
  전제로 다룬다.** 컨트랙트를 서버 정책에 맞춰 좁히면 정책이 바뀔 때마다 배포된
  컨트랙트를 고쳐야 한다.
- **흡수 방법 = 이벤트 리스너.** 체인 이벤트를 읽어 서버 상태로 반영한다.
  §6-5 에서 서버 구매 역할을 넓히지 않기로 한 것과 모순이 아니다 —
  거기서는 **서버가 체인에 쓰는 쪽**이라 좁은 것이 안전하고,
  여기서는 **체인이 서버에 알리는 쪽**이라 넓은 것을 받아들이면 된다.

**실행 티켓이 이미 있다 — `S15P21E104-205` (이벤트 리스너). 다른 담당자 몫이다.**
근거: `BlockchainTransaction` javadoc 26행, `BlockchainVerificationService` 21행이
`-205` 를 가리키고 있다. **이 브랜치에서 구현하지 않는다** — 여기 남기는 것은
"§6-8 은 `-205` 로 해결한다" 는 연결뿐이다.

`-205` 가 채워야 하는 최소 조건: `createListing` 이벤트로 체인에만 생긴 판매 글을
서버에 반영하거나(또는 최소한) **불일치로 표시**한다. 조용히 버리면
"체인에는 있는데 마켓에 안 보이는 판매 글" 이 남는다.

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

### 2026-09-08 결과 — 발급부는 무변경. 남은 갭은 "어느 역할까지 확인했는가" 였다

발급 호출부를 다시 확인했다. **두 경로 모두 `user.getRole().name()` 을 접두어 없이
넣고 있다** (`WalletAuthService` 142행, `EmailLoginService` 56–57행).
**넣지 않는 갭은 없었다.** `EmailLoginService` 는 손대지 않았다 (다른 담당자 영역).

확인 쪽도 대부분 이미 있었다 — `RoleAuthorizationIntegrationTests`(`a2f1425`)가
실제 지갑 로그인으로 받은 토큰이 `@PreAuthorize` 를 통과·거부시키는 것과
**클레임과 DB 가 어긋날 때 DB 가 이기는 것**(`MATERIAL_ROLE_NOT_ALLOWED`)을 고정한다.

남아 있던 좁은 갭은 **발급된 클레임 자체를 검사하는 곳이 `SUPPLIER` 한 역할뿐**이었다는
것이다 (`WalletAuthControllerTests`). 한 역할만 보면 값이 어딘가에 **고정**돼 있어도
통과한다. `WalletAuthServiceTests` 에 지갑을 가질 수 있는 세 역할을 모두 도는 검사를
넣어 그 구멍을 막았다 — 클레임이 **DB 역할 그대로 정확히 하나**이고 `ROLE_` 접두어가
없음을 확인한다. `ADMIN`·`CONSUMER` 는 지갑 로그인 대상이 아니라
(`canOwnWallet()` = false) 이 경로로 확인할 수 없고, 통합 테스트가 별도로 막고 있다.
