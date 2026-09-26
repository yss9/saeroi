# BRAND 대화형 AI 에이전트 — 인수인계

> 새 대화 세션에서 이 기능을 이어받을 때 읽는 문서다. 설계 근거는
> `docs/superpowers/specs/2026-09-18-brand-agent-conversation-design.md` 에 있고,
> 여기에는 **지금 상태와 다음에 할 일**만 적는다.

**브랜치**: `backend/feat/brand-agent-conversation` (origin/backend/develop 기준)
**프론트**: 별도 저장소, 워킹트리에 미커밋 상태
**작성일**: 2026-09-19

---

## 1. 무엇을 만드는가

`saeroi`(circular-fashion)는 폐섬유를 등록·거래·가공해 제품으로 만들고 전 과정을 Besu 체인에
기록하는 순환패션 플랫폼이다. BRAND 담당자가 제품 하나를 만들려면 화면 15개를 순서대로
돌아야 하고 단계마다 MetaMask 서명이나 KakaoPay 결제가 낀다.

**이 전 과정을 대화창 하나로 축약한다.** 담당자는 대화 안에서 **서명과 결제만** 하고
페이지 이동을 하지 않는다.

여정 5단계: `INTAKE`(요구사항) → `MATERIAL_SOURCING`(소재) → `PROCESSING`(가공) →
`PRODUCTION`(생산) → `PRODUCT`(제품 등록)

---

## 2. 지금 되는 것 — 1·2단계

```
"에코백 만들려고 폐데님 필요해요"
  ↓ 요구사항 파악 (수량·소재·예산 되묻기)
  ↓ 보유 소재 확인 → 부족하면 마켓플레이스 검색
  ↓ 후보 비교 (단가·잔량·공급자 + 온체인 여부 표시)
  ↓ 금액 확인 → 배송지 제안 (회사 등록 주소 자동)
  💰 결제 카드 → 카카오페이 → 대화로 복귀 → 에이전트가 안내
  ✍️ 서명 카드 → MetaMask → 온체인 주문 기록
  ⏳ 판매자 발송 (상태 표시줄 20초 폴링)
  ✔ 수령 확정 → 소재가 보유 목록에
```

**실측 검증됨**: 거래 15번이 `PAID` → `chain_trade_id=26` → `SHIPPED` 까지 진행.

### tool 11개

| tool | 종류 | 위임 대상 | 단계 |
|---|---|---|---|
| `save_requirement` | 읽기 | 대화에 요구사항 고정 | 전체 |
| `set_phase` | 읽기 | 단계 이동 | 전체 |
| `get_my_materials` | 읽기 | `MaterialQueryService` | 소재·가공·생산 |
| `search_listings` | 읽기 | `ListingQueryService` | 소재 |
| `quote_trade` | 읽기 | `TradeCommandService#quote` | 소재 |
| `get_shipping_default` | 읽기 | `CompanyProfileRepository` + `UserRepository` | 소재 |
| `get_my_trades` | 읽기 | `TradeQueryService#findMyTrades` | 소재·가공 |
| `purchase_material` | 💰 PAYMENT | `TradeCommandService#create` + `PaymentService#ready` | 소재 |
| `request_chain_order` | ✍️ WALLET_SIGN | 프론트가 `createOrder` → chain-link | 소재 |
| `confirm_receipt` | ✔ CONFIRM | `TradeReceiptService#confirmReceipt` | 소재 |
| `search_processors` | 읽기 | `ProcessorSearchService` | 가공 |

---

## 3. 반드시 알아야 할 설계 원칙 5가지

새 세션에서 코드를 고칠 때 이걸 깨면 안 된다.

### ① 에이전트는 돈과 서명을 직접 하지 않는다

모델이 쓰기 tool 을 불러도 **실행되지 않고** `agent_pending_action` 행만 생긴다. 사용자가
UI 카드를 눌러야 실제 도메인 서비스가 돈다.

**이 분기는 개별 tool 이 아니라 `AgentToolExecutor` 에 있다.** tool 을 추가하는 사람이
실수로 우회할 수 없어야 하기 때문이다. `AgentTool.isWrite()` 가 true 면 자동으로 카드가 된다.

### ② 트랜잭션이 LLM 호출을 감싸면 안 된다

`AgentChatService` 에 `@Transactional` 이 **없는 것은 의도다.** LLM 호출이 20~30초 걸리는데
그동안 DB 커넥션을 쥐면, 풀(설정 20)이 고갈되어 에이전트뿐 아니라 로그인·결제까지 멈춘다.

저장은 `AgentTurnStore` 의 짧은 트랜잭션들이 맡는다. **여기에 `@Transactional` 을 추가하지 말 것.**

단, **카드 승인의 원자성은 유지된다** — `PendingActionService.complete` 의 "도메인 쓰기 +
카드 상태 전이"는 한 트랜잭션이다. 이중 결제를 막는 지점이다.

### ③ 체인이 진행 상태의 정본이다

다단계 서명에서 `step_done` 을 믿고 다음 단계를 정하면 안 된다. 트랜잭션은 성공했는데 서버
보고가 실패하면 이 값이 체인보다 뒤처지고, 그 상태에서 다시 서명하면 revert 한다.
프론트가 온체인 상태를 읽어 다음 단계를 정하고, `step_done` 은 표시용이다.

### ④ 모델에게 숫자를 맡기지 않는다

- 금액은 `quote_trade` 로 서버가 계산한다. 모델이 곱해서 말하지 않는다
- 서명 파라미터(온체인 listingId, 수량)는 모델 인자가 아니라 **서버에서 재조회**한다
  (`AgentChat.tsx` 의 `signTradeOrder`)
- 사용자 id 는 `AgentToolContext` 에서만 읽는다. 모델이 인자로 주게 두면 인가가 뚫린다

### ⑤ 크레딧은 토큰당 차감된다

tool 루프가 한 턴에 같은 프롬프트를 최대 4회 재전송한다. 다음을 건드릴 때는 비용을 함께 본다.

- `max-tool-iterations` (4) — 이 값만큼 LLM 호출이 는다
- `ConversationStore.MAX_HISTORY_MESSAGES` (20)
- `SearchListingsTool.SIZE` (5) — tool 결과도 입력 토큰이다
- 다단계 중간 보고(`/step`)는 **LLM 을 부르지 않는다.** 부르면 비용이 단계 수만큼 곱해진다
- 실시간 상태 표시는 도메인 API 폴링이다. **여기에 LLM 을 끼우면 가만히 있어도 과금된다**

---

## 4. 파일 지도

### 백엔드 `com.ssafy.upcycle.agent`

```
AgentErrorCode.java
config/     AgentProperties(app.agent.*)  AgentConfig(공급자별 클라이언트 선택)
entity/     AgentConversation  AgentMessage  AgentPendingAction
            JourneyPhase  ConversationStatus  AgentMessageRole
            PendingActionType  PendingActionStatus
repository/ 3개
dto/        ConversationDetailResponse  PendingActionResponse  등 6개
controller/ AgentConversationController  AgentActionController
service/
  AgentChatService        턴 루프 (트랜잭션 없음)
  AgentTurnStore          짧은 트랜잭션들 + 프롬프트 로딩
  AgentConversationService 세션 생성·조회·종료
  PendingActionService    카드 승인 — 쓰기 tool 이 도는 유일한 지점
  AgentToolExecutor       쓰기 경계
  AgentToolRegistry       단계별 tool 노출
  AgentPromptBuilder      시스템 프롬프트
  ConversationStore       이력 저장·복원·윈도 자르기
  llm/  LlmClient(인터페이스)  GmsLlmClient(Anthropic)
        OpenAiLlmClient  UnavailableLlmClient  + 값 타입들
tool/       AgentTool  AbstractAgentTool  + 위 표의 tool 들
```

기존 파일 수정: `KakaoPayProperties`(agentReturnUrl 추가), `PaymentService`(fromAgent 인자),
`application.yml`(app.agent.*, hikari 풀), `schema.sql`, `SchemaSqlTests`

### 프론트엔드

```
features/agent/
  types/agent.types.ts
  api/agentApi.ts  agentKeys.ts
  hooks/useAgentConversation.ts
  components/ AgentChat  PaymentCard  WalletSignCard  ConfirmCard
              TradeStatusStrip  AgentLauncher
pages/agent/  AgentPage.tsx  AgentPaymentCallbackPage.tsx  agent.css
services/sessionSync.ts     창 간 토큰 동기화
```

수정: `apiClient.ts`(창 간 재발급 조율), `authStore.ts`(adoptAccessToken),
`routes.tsx`, `MarketplacePage.tsx`(진입 버튼), 커버리지 테스트 2개

### DB

`agent_conversation` · `agent_message` · `agent_pending_action`
마이그레이션: `db/migrations/2026-09-18-brand-agent-conversation.sql`

`agent_conversation.active_brand_user_id` 는 **생성 컬럼**이다 — 상태가 ACTIVE 일 때만 값이
채워지고 UNIQUE 가 걸려 담당자당 활성 대화 1개를 DB 가 보장한다 (`user_wallet` 과 같은 방식).

---

## 5. API

```
POST   /api/v1/agent/conversations                 시작 (활성 대화 있으면 이어붙임)
GET    /api/v1/agent/conversations/active          진행 중인 대화 (없으면 null)
GET    /api/v1/agent/conversations                 목록
GET    /api/v1/agent/conversations/{id}            상세
POST   /api/v1/agent/conversations/{id}/messages   발화
POST   /api/v1/agent/conversations/{id}/events     대화 밖 사건 보고 (결제 복귀 등)
POST   /api/v1/agent/conversations/{id}/complete   담당자가 완료
DELETE /api/v1/agent/conversations/{id}            중단
POST   /api/v1/agent/actions/{id}/step             다단계 중간 보고 (LLM 미호출)
POST   /api/v1/agent/actions/{id}/complete         카드 승인 → 실행
POST   /api/v1/agent/actions/{id}/reject           거절
```

BRAND 인가는 `@PreAuthorize` 가 아니라 **서비스 계층 DB 재조회**다
(`specs/2026-09-08-role-permission-matrix.md` 가 이 저장소의 정본 방식).
대화 소유권은 조회 쿼리에 박혀 있다(`findByIdAndBrandUserId`).

---

## 6. 환경 설정

### GMS (SSAFY Gen AI Management System)

**통합 규격이 아니라 공급자별 패스스루 프록시다.**

```
Anthropic : https://gms.ssafy.io/gmsapi/api.anthropic.com/v1/messages
            헤더 x-api-key, anthropic-version: 2023-06-01
OpenAI    : https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions
            헤더 Authorization: Bearer
```

`provider` 와 `base-url` 은 **모델 이름에서 자동 추론**된다 (`claude-*` → anthropic).

### `application-local.yml`

```yaml
app:
  agent:
    api-key: <GMS 팀 키>
    model: gpt-4.1-nano          # 개발. 아래 표 참고
    thinking: false              # Sonnet 4.6 이상에서만 true (Haiku 는 400)
    prompt-cache: true           # Anthropic 전용. gpt-* 에서는 무시됨
    max-tool-iterations: 4
```

### 모델 선택 (크레딧은 토큰당 차감, 팀 총액 100,000)

| 모델 | 카드 크레딧 | 용도 |
|---|---|---|
| 스텁 (테스트) | **0** | 로직·안전장치·회귀 — 개발의 80% |
| `gpt-4.1-nano` | 1 | 배선·흐름 확인 |
| `claude-haiku-4-5-20251001` | 10 | 중간 |
| `claude-sonnet-4-6` | 30 | 품질 검증·시연 |

**경량 모델로 대화 품질을 판단하지 말 것.** 다단계 tool 연쇄에 약해서 거기 맞춰 프롬프트를
다듬으면 잘못된 대상에 튜닝하게 된다.

### 로컬 실행 (Windows PowerShell)

```powershell
# DB 마이그레이션 (최초 1회)
$env:Path += ";C:\Program Files\MySQL\MySQL Server 8.4\bin"
cmd /c "mysql -u root -p -P 3307 --protocol=TCP --default-character-set=utf8mb4 upcycle < backend\src\main\resources\db\migrations\2026-09-18-brand-agent-conversation.sql"

# 백엔드
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

# 프론트 (실행 정책 때문에 npm.cmd 를 쓴다)
cd frontend; npm.cmd run dev
```

기동 로그에서 확인:
`Agent LLM configured: provider=... model=... maxToolIterations=...`

### 서명 없이 테스트하기

`app.blockchain.*` 를 비우면 온체인 경로가 501 이 되고, 소재 등록·판매글·거래·결제·수령이
**전부 DB 에서 완결된다.** 근거:

- `ListingCommandService` — "DB 만 채운다. 온체인 createListing 은 별도 경로다"
- `TradeShipmentService` / `TradeReceiptService` — 체인 참조 없음
- `KAKAOPAY_SECRET_KEY` 가 비면 `MockPaymentGateway` (실제 청구 없음)

**단 지갑은 필요하다** — `OffChainMaterialIssuer` 가 활성 지갑을 요구한다. 계정당
`personal_sign` 1회면 되고 가스도 체인 기록도 없다.

**가짜 `chain_listing_id` 를 DB 에 넣지 말 것.** 체인에 없는 번호로 서명하면 revert 하고
원인이 드러나지 않는다.

---

## 7. 알려진 문제

| | 내용 |
|---|---|
| **거래 만료 워커 없음** | `MaterialTradeExpiryWorker` 가 `backend/feat/material-trade-payment-expiry` 브랜치에만 있다. 이 브랜치에는 없어서 **결제 안 된 거래가 소재 수량을 계속 묶는다.** 테스트를 반복하면 판매글 잔량이 줄어든다 — `/trades` 에서 수동 취소 |
| **`cancel_trade` tool 없음** | 대화에서 이탈한 거래를 정리할 수 없다 |
| **프롬프트 캐싱 미검증** | GMS 가 `cache_control` 을 통과시키는지 확인 안 됨. 400 이 나면 `prompt-cache: false` |
| **미커밋** | 백엔드·프론트 모두 워킹트리 상태 |
| 저울 OCR 계약 불일치 | backend 가 `/v1/vision/weight` 를 부르는데 `ai/app` 에 없다. **이번 범위 밖** (사용자 확인됨) |

---

## 8. 다음에 할 일 (우선순위 순)

### A. 지금 바로 (작고 확실한 것)

1. **커밋 정리** — 백엔드/프론트 각각 브랜치 커밋
2. **`cancel_trade` tool** — 이탈 거래 정리. 기존 취소 API 를 감싸면 끝
3. **프롬프트 캐싱 검증** — 로그의 `cacheRead` 값 확인. 0 보다 크면 먹는 것

### B. 가공 단계 (가치가 가장 큼)

견적 비교는 대화로 하면 확실히 편해지는 작업이라 "AI 가 일한다"는 인상이 가장 강하다.
서명은 3~4회로 생산(4회)보다 적다.

필요한 tool: `create_processing_request` · `list_quotes` · `select_quote`(💰) ·
`request_custody_transfer`(✍️) · `approve_processing_result`

**선행 필요**: 필수 가공 규칙 (§9 참고)

### C. 생산·제품 단계

서명이 4회 더 붙고 컨트랙트가 순서를 강제한다
(`createBatch → submitPlanAndLock → confirmProduction → linkProductItems`).
다단계 카드 구조는 백엔드에 이미 있다 — `AgentTool.signatureSteps()` 를 4로 두면 된다.
프론트 `WalletSignCard` 에 온체인 상태로 다음 단계를 정하는 분기를 추가해야 한다
(`useProductionChainStatus` 재사용).

### D. 운영 개선

- 스트리밍 (지금은 요청–응답. `apiClient` 의 봉투 인터셉터와 10초 타임아웃 때문에 별도 처리 필요)
- 알림 연동 (지금은 폴링. 견적 도착·발송을 먼저 알려주려면)
- 대화 아카이빙 (규모가 커지면)

---

## 9. 아직 정해지지 않은 것

| | 내용 | 필요 시점 |
|---|---|---|
| **필수 가공 규칙** | 소재 출처(`VIRGIN`/`WASTE`)·종류별로 반드시 거쳐야 하는 공정 표. **코드에 전혀 없다** | 가공 단계 전 |
| 강제 지점 | 사용자 확인: **생산 투입 시** 검증 (가공 요청은 막지 않음). 투입/확정/제품등록 중 어디인지 확정 필요 | 〃 |
| "거쳤다"의 근거 | `processing_request_type` 은 *요청한* 공정이다. 승인된 작업의 요청 공정을 수행 근거로 볼지 | 〃 |
| 기존 데이터 소급 | 규칙을 켜면 이미 판매 중인 제품이 위반 상태가 될 수 있다 | 〃 |

**검사는 기존 데이터로 가능하다** (스키마 변경 불필요):

```
투입 소재
 └ material_lineage (relation_type=PROCESSING, reference_id=processing_job_id)
    └ processing_job.processing_request_id
       └ processing_request_type.process_type[]
 └ 계보를 위로 거슬러 공정을 합집합으로
```

`MaterialLineageQueryService` 에 순회 로직이 이미 있다(깊이 제한·순환 방지 포함).
`root_origin` 은 가공해도 바뀌지 않아 규칙 기준으로 안정적이다.

**에이전트 2층 구조**: 필수 가공은 tool 이 인자에 없으면 자동으로 채운다(모델이 잊어도 결과가
같다). 추천 가공은 모델이 제품·원단 특성을 보고 이유와 함께 제안하고 담당자가 동의한 것만
들어간다. 화면에서 둘을 시각적으로 구분한다.

---

## 10. 테스트

```powershell
cd backend; .\mvnw.cmd test                    # 1,878건
cd frontend; npm.cmd run test                  # 1,184건
cd backend; bash scripts/verify-migrations.sh  # 스키마 대조
```

`LlmClient` 를 `@MockitoBean` 으로 스텁한다 — **크레딧 0, 서명 0.**
핵심 안전 테스트(깨지면 안 되는 것):

- 쓰기 tool 이 카드만 만들고 DB 를 바꾸지 않는다
- 같은 카드를 두 번 승인할 수 없다
- 남의 대화·카드를 건드릴 수 없다
- 단계에 없는 tool 은 실행되지 않는다
- `/step` 이 LLM 을 부르지 않는다

`ProductionQrApiTests` 가 가끔 zxing 디코딩으로 깨진다 — **플래키이며 이 기능과 무관하다.**
재실행하면 통과한다.
