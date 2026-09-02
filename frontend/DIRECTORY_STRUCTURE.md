# Frontend 디렉토리 구조

작업 위치를 통일하고 파일 충돌을 줄이기 위한 구조입니다.
각 Layer의 책임과 상태관리 기준은 [ARCHITECTURE.md](./ARCHITECTURE.md)를 보세요.

```text
saeroi/frontend/
├─ public/
│  ├─ img/                          # 공통 로고·브랜드 이미지
│  └─ images/<page>/                # 페이지별 정적 이미지
├─ src/
│  ├─ app/
│  │  ├─ providers/                 # AppProviders (QueryClientProvider 등)
│  │  └─ router/
│  │     ├─ routes.tsx              # 모든 경로 정의
│  │     ├─ index.tsx               # createBrowserRouter
│  │     └─ guards/                 # ProtectedRoute, RoleGuard
│  ├─ pages/<domain>/               # 화면. Feature 조합만 한다
│  │  └─ landing/components/        # 해당 페이지에서만 쓰는 컴포넌트
│  ├─ features/<domain>/            # 도메인 기능
│  │  ├─ api/                       # HTTP 호출 + Query Key
│  │  ├─ components/                # 이 도메인 전용 컴포넌트
│  │  ├─ hooks/                     # useQuery / useMutation 래핑
│  │  ├─ services/                  # 컨트랙트·지갑 호출 (필요한 도메인만)
│  │  └─ types/                     # 도메인 타입
│  ├─ components/common/            # 도메인에 의존하지 않는 공통 UI
│  ├─ services/                     # apiClient, queryClient, blockchain/
│  ├─ stores/                       # authStore, walletStore, uiStore (Zustand)
│  ├─ hooks/                        # 도메인 무관 훅
│  ├─ types/                        # role.types, api.types
│  ├─ utils/                        # errors, queryKeys, address
│  ├─ config/                       # env.ts, contracts.ts
│  ├─ assets/
│  ├─ test/                         # 전역 테스트 설정
│  ├─ App.tsx                       # AppProviders + RouterProvider
│  ├─ main.tsx                      # React 진입점
│  └─ index.css                     # 전역 스타일
├─ docker/                          # Frontend 컨테이너·Nginx 설정
├─ docs/                            # 설계 및 작업 문서
├─ ARCHITECTURE.md                  # 아키텍처 규칙 (작업 전 필독)
├─ GIT_CONVENTION.md                # 브랜치·커밋 규칙
├─ .env.example                     # 환경변수 목록 (실값은 .env, 커밋 금지)
├─ package.json
└─ vite.config.ts
```

## 협업 규칙

### 어디에 둘까

- 새 페이지는 `src/pages/<domain>/`. 페이지 전용 컴포넌트는 그 아래 `components/`.
- 도메인 상태·로직·API는 `src/features/<domain>/`.
- **여러 도메인에서 쓸 때만** `src/components/common/`으로 올립니다.
  하나의 도메인에서만 쓰면 `features/<domain>/components/`에 둡니다.
- 정적 자산은 공통이면 `public/img/`, 페이지 전용이면 `public/images/<page>/`.
  파일명은 kebab-case.
- 테스트는 대상 파일 옆에 `*.test.ts` / `*.test.tsx`로 작성합니다.

### 한 곳에서만 고치는 것

| 대상 | 위치 |
| --- | --- |
| 라우트 | `src/app/router/routes.tsx` |
| 환경변수 | `src/config/env.ts`, `src/config/contracts.ts` |
| API 공통 설정 | `src/services/apiClient.ts` |
| 전역 Provider | `src/app/providers/AppProviders.tsx` |

여러 명이 함께 건드리는 파일이므로 변경 전 팀에 공유해 주세요.

### 하지 말 것

- 페이지끼리 서로의 내부 파일을 직접 import하지 않습니다.
- Feature 간에도 내부 파일을 직접 import하지 않습니다.
  (Feature용 barrel `index.ts`를 만들지 마세요 — 순환 의존성이 생깁니다.
  `components/common/index.ts`만 예외입니다.)
- 전역 상태(Zustand)에 서버 조회 데이터를 복제하지 않습니다. → TanStack Query
- `node_modules/`, `dist/`, `.worktrees/`는 생성 결과이므로 수정·커밋하지 않습니다.
- 다른 작업자의 파일을 일괄 포맷하거나 이동하지 않습니다.
  기능 브랜치에는 담당 범위의 변경만 커밋합니다.
- `screens/`는 디자인 참고 자료입니다.
  팀에서 합의하지 않은 원본 캡처는 기능 커밋에 포함하지 않습니다.
