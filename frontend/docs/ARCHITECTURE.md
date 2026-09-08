# Frontend Architecture

여러 명이 도메인별로 동시에 개발해도 상태관리·API 호출·지갑 연동·공통 컴포넌트가
서로 뒤섞이지 않게 하기 위한 규칙입니다. **새 코드를 짜기 전에 이 문서의 §3, §4, §9를
먼저 확인하세요.**

## 1. 한 장 요약

| 무엇을 | 어디에 |
| --- | --- |
| Server State (API 조회 데이터) | TanStack Query |
| Global Client State | Zustand (`src/stores/`) |
| Local State (Tab, Dropdown, Toggle) | `useState` |
| Form State | React Hook Form |
| 외부 시스템 동기화 | `useEffect` |
| API 호출 | Feature `api/` + `services/apiClient.ts` |
| 지갑 | `features/wallet/services/` |
| 스마트 컨트랙트 | `features/{domain}/services/*ContractService.ts` |
| Page | Feature 조합만 |

## 2. 폴더 구조

```text
src/
├── app/
│   ├── providers/AppProviders.tsx   # 전역 Provider 조립
│   └── router/
│       ├── routes.tsx               # 모든 경로 정의 (여기에만 추가)
│       ├── index.tsx                # createBrowserRouter
│       └── guards/                  # ProtectedRoute, RoleGuard
│
├── pages/{domain}/                  # 화면. Feature 조합만 한다
├── features/{domain}/               # 도메인 기능
│   ├── api/                         # HTTP 호출 + Query Key
│   ├── components/                  # 이 도메인 전용 컴포넌트
│   ├── hooks/                       # useQuery / useMutation 래핑
│   ├── services/                    # 컨트랙트 호출 등 (필요한 도메인만)
│   └── types/                       # 도메인 타입
│
├── components/common/               # 도메인에 의존하지 않는 공통 UI
├── services/
│   ├── apiClient.ts                 # Axios 인스턴스 (단 하나)
│   ├── queryClient.ts               # QueryClient 기본 설정
│   └── blockchain/contractFactory.ts
├── stores/                          # authStore, walletStore, uiStore
├── hooks/                           # 도메인 무관 훅 (useBlockchainTransaction)
├── types/                           # role.types, api.types
├── utils/                           # errors, queryKeys, address
├── config/                          # env.ts, contracts.ts
└── assets/
```

## 3. 상태관리 기준 — 이것만은 지켜주세요

### Server State → TanStack Query

Backend에서 조회하는 모든 데이터(Material, Marketplace, Trade, Inventory,
Processing, Production, Product, Material Journey)는 TanStack Query가 관리합니다.

> **서버 데이터를 Zustand에 복제하지 마세요.**
> 캐시가 둘이 되면 어느 쪽이 최신인지 알 수 없고, 무효화 타이밍이 어긋나
> "등록했는데 목록에 안 보이는" 버그가 생깁니다.

```ts
// OK
const { data, isPending, isError } = useMaterials(filters)

// 하지 말 것 — 서버 데이터를 전역 스토어로 옮기지 않는다
useEffect(() => { setMaterialsInStore(data) }, [data])
```

### Global Client State → Zustand

전역이 꼭 필요한 것만 둡니다. 현재 3개뿐이고, 늘리기 전에 팀과 논의해 주세요.

| Store | 담는 것 |
| --- | --- |
| `authStore` | `user`, `accessToken`, `isAuthenticated` |
| `walletStore` | `address`, `chainId`, `isConnected` |
| `uiStore` | 사이드바 열림 여부, 전역 Modal |

> **`walletStore`에 Provider/Signer/Contract 인스턴스를 넣지 마세요.**
> 직렬화가 깨지고, 계정·체인을 바꿔도 낡은 객체를 계속 들고 있게 됩니다.
> 필요할 때마다 `walletService.getSigner()`로 새로 얻으세요.

### Local State → useState

한 컴포넌트 안에서만 쓰는 상태(Tab 선택, Dropdown 열림, 단순 Toggle)는 `useState`입니다.
전역 스토어로 올리지 마세요.

### Form State → React Hook Form

Material 등록, Processing 결과 입력, Production 등록처럼 폼이 많습니다.
input마다 `useState`를 두지 마세요 — 검증·에러 표시·초기화가 흩어지고
입력할 때마다 폼 전체가 리렌더됩니다.

참고 예시: `features/material/components/MaterialForm.tsx`

### Side Effect → useEffect

`useEffect`는 **외부 시스템과 동기화할 때만** 씁니다.
(MetaMask `accountsChanged` / `chainChanged`, 이벤트 리스너, 타이머, `dialog` 제어)

```ts
// 하지 말 것 — 데이터 조회에 useEffect + axios 를 쓰지 않는다
useEffect(() => { axios.get('/materials').then(setMaterials) }, [])

// OK
const { data } = useMaterials()
```

## 4. API 호출 흐름

```text
Page → Feature Component → Custom Hook → TanStack Query → Feature API → apiClient → Spring Boot
```

- Component에서 `axios`를 직접 import하지 않습니다.
- API URL을 Component에 하드코딩하지 않습니다. Base URL은 `config/env.ts` 한 곳입니다.
- `apiClient`가 하는 일: 토큰 주입, `ApiResponse` 봉투 제거, 401 처리, `AppError` 정규화.
  덕분에 Feature API 함수는 알맹이 타입만 다루면 됩니다.

### Query Key 규칙

키 배열을 직접 만들지 말고 `createQueryKeys` 팩토리를 쓰세요.
제각각 지으면 무효화가 조용히 빗나갑니다.

```ts
export const materialKeys = createQueryKeys('material')

materialKeys.all           // ['material']
materialKeys.lists()       // ['material', 'list']
materialKeys.list(filters) // ['material', 'list', { ... }]
materialKeys.detail(1)     // ['material', 'detail', 1]

queryClient.invalidateQueries({ queryKey: materialKeys.lists() })
```

## 5. Wallet 호출 흐름

```text
WalletButton → useWallet → walletClient   (EIP-1193 상태·이벤트)
                        → walletService  (ethers: Provider/Signer/서명)
```

- `walletClient.ts` — 연결 상태를 들고 `accountsChanged` / `chainChanged`를 구독합니다.
- `walletService.ts` — Provider·Signer·`signMessage` 등 일회성 작업을 담당합니다.
- MetaMask 코드를 Page나 일반 Component에 직접 쓰지 마세요.
- EIP-1193 에러 파싱은 `walletService.toWalletError()`가 합니다.
  화면은 `error.kind`(`SIGNATURE_REJECTED`, `WRONG_NETWORK` 등)만 보면 됩니다.

### Wallet Login (구조만 준비됨)

```text
MetaMask 연결 → 주소 획득 → Nonce 요청 → 메시지 서명 → 서명 전송 → 검증 → JWT
```

`features/wallet/hooks/useWalletLogin.ts`에 배선이 끝나 있고,
Backend 엔드포인트 두 곳만 `TODO(6번)` 자리에 연결하면 동작합니다.

## 6. Blockchain 흐름

```text
Page → Feature Hook → Contract Service → contractFactory → walletService → ethers → MetaMask → Besu
```

- Component에서 `new ethers.Contract(...)`를 쓰지 마세요.
- 주소·ABI는 `config/contracts.ts`에서만 읽습니다. 하드코딩 금지.
- ABI/주소의 Source of Truth는 `blockchain/master` 브랜치입니다.

### Transaction 상태

`useBlockchainTransaction()`이 공통 상태 기계를 제공합니다.

```text
IDLE → SIGNING → PENDING → CONFIRMED
                         → FAILED (서명 거부 / revert / 드롭)
```

도메인은 "무엇을 보낼지"만 넘기면 됩니다.

참고 예시: `features/material/hooks/useMintMaterial.ts`

> 서버 조회 무효화는 **CONFIRMED 이후에** 하세요. Backend가 이벤트를 수집해
> MySQL 조회 모델을 갱신하므로, 확정 전에 다시 조회하면 옛 값이 옵니다.

## 7. 공통 vs 도메인 컴포넌트

```text
여러 도메인에서 사용   → src/components/common/
하나의 도메인에서만    → src/features/{domain}/components/
```

`MaterialCard`는 Material에서만 쓰므로 `features/material/components/`에 있습니다.
**미리 common으로 올리지 마세요.** 두 번째 도메인이 필요로 할 때 논의하고 옮깁니다.

공통 컴포넌트는 특정 스토어나 도메인 타입에 의존하지 않습니다.
`StatusBadge`가 도메인 상태 문자열 대신 `tone`을 받는 이유입니다.

현재 공통 컴포넌트 (14개):
`Button` `Input` `Select` `Modal` `ConfirmModal` `Loading` `EmptyState` `ErrorMessage`
`StatusBadge` `QuantityInput` `FileUploader` `WalletChip` `TransactionStatus` `VerificationBadge`

## 8. Loading 표현 구분

| 상황 | 사용 |
| --- | --- |
| 목록·상세 조회 | `<Loading variant="skeleton" />` |
| 버튼 요청 | `<Button loading />` |
| 블록체인 트랜잭션 | `<TransactionStatus />` |

"결과 없음"과 "에러"는 다른 상태입니다.
조회 성공 + 0건 → `EmptyState`, 조회 실패 → `ErrorMessage`.

## 9. Routing / Role Guard

경로는 `app/router/routes.tsx`에만 정의합니다.

| 구역 | 예 |
| --- | --- |
| Public | `/`, `/login`, `/materials`, `/marketplace`, `/products` |
| **Public (비회원 필수)** | `/passport/:productId` |
| Protected | `/inventory`, `/trades` |
| Role별 | `/supplier/*`, `/brand/*`, `/processor/*`, `/admin/*` |
| 안내 | `/403` (권한 없음), `*` (없는 경로) |

### 미인증(401)과 권한없음(403)을 구분합니다

Backend가 401과 403을 나눠 주는 것과 같은 이유로 가드도 나눕니다.

| 상황 | 가드 | 결과 |
| --- | --- | --- |
| 로그인 안 됨 | `ProtectedRoute` / `RoleGuard` | `/login` (`state.from`으로 원래 위치를 넘겨 로그인 후 복귀) |
| 로그인했지만 Role이 다름 | `RoleGuard` | `/403` (`FORBIDDEN_PATH`) |
| 로그인했지만 Role을 아직 모름 | `RoleGuard` | 판정 보류 — `Loading`을 렌더 |

**권한없음을 `/login`으로 보내지 마세요.** 이미 로그인한 사용자가 로그인 화면을 다시 보고,
로그인에 성공하면 `LoginPage`가 `state.from`으로 되돌려 보내 같은 자리에서 또 튕깁니다.

`/403`은 Public입니다. 이 화면 자체가 가드에 막히면 사용자가 나갈 길이 없어집니다.
Role 판정의 출처는 `useAuth()`(= `authStore`) 하나입니다. JWT를 따로 디코딩하지 마세요 —
같은 값을 읽는 두 번째 경로가 생기면 둘이 어긋납니다.

> ### Frontend Guard는 보안이 아닙니다
> `ProtectedRoute`와 `RoleGuard`는 **UX를 위한 장치**입니다. 브라우저에서 도는 코드는
> 사용자가 얼마든지 우회할 수 있습니다. 실제 접근 통제는 Spring Security가 매 요청마다
> JWT를 검증해서 수행합니다.
>
> 프론트에서 가드를 빠뜨리면 사용자 경험이 나빠지지만,
> **Backend에서 인가를 빠뜨리면 데이터가 샙니다.**

> ### `/passport/:productId`는 Public Route입니다
> 소비자가 제품 QR을 찍고 바로 들어오는 진입점입니다.
> 로그인이나 지갑 연결을 요구하면 서비스의 목적 자체가 무너집니다.
> **절대 `ProtectedRoute` 안으로 옮기지 마세요.**
> 이 화면이 부르는 API도 인증을 요구하면 안 됩니다.

## 10. 타입 규칙

- 도메인 타입은 `features/{domain}/types/{domain}.types.ts`에 둡니다.
- **문자열 Enum 값은 Backend Enum과 정확히 일치시킵니다.**
  (`backend/src/main/resources/schema.sql`)
  프론트에서 이름을 바꾸면 서버 응답을 그대로 화면 상태로 쓸 수 없습니다.
- API DTO 필드는 camelCase입니다.
- TS `enum`을 쓰지 마세요 — `tsconfig.app.json`의 `erasableSyntaxOnly: true`가 금지합니다.
  문자열 유니온 + `as const` 배열을 씁니다.

## 11. Error 규칙

모든 에러는 `AppError`로 정규화되어 `kind`로 분기합니다.

```text
API_ERROR  NETWORK_ERROR
WALLET_NOT_FOUND  WALLET_DISCONNECTED  SIGNATURE_REJECTED  WRONG_NETWORK
TRANSACTION_FAILED  UNKNOWN
```

Page에서 axios 에러나 MetaMask 에러를 직접 파싱하지 마세요.
변환은 `apiClient`와 `walletService`가 이미 했습니다.

## 12. 팀 개발 규칙

1. 자기 도메인 폴더 안에서 작업합니다.
   **다른 Feature의 내부 파일을 직접 import하지 않습니다.**
   Feature 간 barrel(`index.ts`)을 만들지 마세요 — 순환 의존성이 생깁니다.
   `components/common`만 예외입니다. 도메인에 의존하지 않아 순환이 불가능합니다.
2. Page에 axios 호출·컨트랙트 생성·비즈니스 로직·응답 가공을 쓰지 않습니다.
3. `any`를 쓰지 않습니다. 모르면 `unknown`으로 받고 좁히세요.
4. 공통화가 필요하지 않은 컴포넌트를 `common`으로 올리지 않습니다.
5. 한 파일의 책임이 지나치게 커지면 분리합니다.
6. 다른 사람의 파일을 일괄 포맷하거나 이동하지 않습니다.
   기능 브랜치에는 담당 범위의 변경만 커밋합니다.
7. 커밋 전 `npm run lint && npm test && npm run build`를 통과시킵니다.
8. 브랜치·커밋 규칙은 `GIT_CONVENTION.md`를 따릅니다.

## 13. 담당자별 작업 위치

| 담당 | 도메인 | 주요 작업 위치 |
| --- | --- | --- |
| 팀원 1 | User / Wallet | `features/auth/`, `features/wallet/`, `pages/auth/` |
| 팀원 2 | Material | `features/material/`, `features/inventory/`, `pages/material/`, `pages/inventory/` |
| 팀원 3 | Marketplace / Escrow | `features/marketplace/`, `features/trade/`, `pages/marketplace/`, `pages/trade/` |
| 팀원 4 | Processing | `features/processing/`, `pages/processing/` |
| 팀원 5 | Production / Product | `features/production/`, `features/product/`, `pages/production/`, `pages/product/` |
| 팀원 6 | Traceability / QR | `features/traceability/`, `pages/traceability/` |

여러 명이 함께 고쳐야 하는 파일(변경 전 공유해 주세요):

- `app/router/routes.tsx` — 새 경로 추가
- `components/common/` — 공통 컴포넌트 추가·변경
- `services/apiClient.ts` — 공통 요청·응답 처리
- `config/contracts.ts` — ABI·주소 연결
