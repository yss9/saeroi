# 새로이 Frontend

폐소재가 새로운 제품으로 재탄생하는 과정을 추적하고 QR Passport로 확인할 수 있는 순환 패션 플랫폼의 Frontend 프로젝트입니다.

## 기술 스택

- React
- TypeScript
- Vite
- ESLint

## 프로젝트 문서

- [Frontend 아키텍처](./docs/ARCHITECTURE.md)
- [Frontend 디렉토리 구조](./docs/DIRECTORY_STRUCTURE.md)
- [Git 브랜치 및 커밋 규칙](./docs/GIT_CONVENTION.md)
- [랜딩 페이지 디자인 QA](./docs/design-qa.md)

## 실행 환경

- Node.js `^20.19.0` 또는 `>=22.12.0`
- 권장 개발 버전: Node.js `24.20.0`
- npm 사용

## 로컬 실행

의존성을 설치합니다.

```bash
npm install
```

개발 서버를 실행합니다.

```bash
npm run dev
```

기본 접속 주소는 `http://localhost:5173`입니다.

## Docker 이미지 빌드

`docker/Dockerfile`은 `VITE_*` 값을 빌드 시점 `--build-arg`로 받아 번들에 인라인한다.
Jenkins가 `.env`의 `VITE_*` 줄을 그대로 `--build-arg`로 넘기므로, **`.env.example`에 `VITE_*` 키를
추가하면 `docker/Dockerfile`의 `ARG` 목록에도 반드시 같은 이름을 추가해야 한다.** 선언이 없으면
Docker가 경고만 내고 값을 조용히 버려 빈 문자열로 빌드된다.

## 명령어

```bash
npm run dev
npm run build
npm run lint
npm run preview
```

- `npm run dev`: 개발 서버 실행
- `npm run build`: TypeScript 검사 및 프로덕션 빌드
- `npm run lint`: ESLint 검사
- `npm run preview`: 프로덕션 빌드 결과 미리보기
