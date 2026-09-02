# Frontend 디렉토리 구조

작업 위치를 통일하고 파일 충돌을 줄이기 위한 최소 구조입니다.

```text
S15P21E104_front/
├─ public/
│  ├─ img/                    # 공통 로고·브랜드 이미지
│  └─ images/<page>/          # 페이지별 정적 이미지
├─ src/
│  ├─ app/                    # Router 등 앱 진입 구성
│  ├─ config/                 # 환경변수 파싱·검증
│  ├─ features/<domain>/      # 도메인 상태·로직·컴포넌트
│  ├─ pages/<page>/           # 페이지와 페이지 전용 요소
│  │  └─ components/          # 해당 페이지에서만 쓰는 컴포넌트
│  ├─ shared/                 # 여러 기능에서 재사용하는 코드
│  │  └─ api/                 # 공통 Axios 클라이언트
│  ├─ test/                   # 전역 테스트 설정
│  ├─ App.tsx                 # Router 연결
│  ├─ main.tsx                # React 진입점
│  └─ index.css               # 전역 스타일
├─ docker/                    # Frontend 컨테이너·Nginx 설정
├─ docs/                      # 설계 및 작업 문서
├─ GIT_CONVENTION.md          # 브랜치·커밋 규칙
├─ package.json
└─ vite.config.ts
```

## 협업 규칙

- 새 페이지는 `src/pages/<page>/`, 페이지 전용 컴포넌트와 스타일도 그 아래에 둡니다.
- 도메인 상태와 로직은 `src/features/<domain>/`에 둡니다. 전역 상태는 React Context를 사용합니다.
- 두 기능 이상에서 재사용할 때만 `src/shared/`로 이동합니다. 페이지끼리 서로의 내부 파일을 직접 import하지 않습니다.
- 라우트는 `src/app/router.tsx`, 환경변수는 `src/config/`, API 기본 설정은 `src/shared/api/`에서만 수정합니다.
- 정적 자산은 공통이면 `public/img/`, 페이지 전용이면 `public/images/<page>/`에 두고 kebab-case 파일명을 사용합니다.
- 테스트는 대상 파일 가까이에 `*.test.ts` 또는 `*.test.tsx`로 작성합니다.
- `node_modules/`, `dist/`, `.worktrees/`는 생성 결과이므로 수정하거나 커밋하지 않습니다.
- `screens/`는 디자인 참고 자료입니다. 팀에서 합의하지 않은 원본 캡처는 기능 커밋에 포함하지 않습니다.
- 다른 작업자의 파일을 일괄 포맷하거나 이동하지 말고, 기능 브랜치에는 담당 범위의 변경만 커밋합니다.

