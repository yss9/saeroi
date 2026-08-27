# Git Convention

## 1. 전체 브랜치 구조

본 프로젝트는 Backend, Frontend, AI, Blockchain 파트를 독립적으로 개발하며,
각 파트마다 `master`, `develop`, `feat/*` 브랜치를 운영합니다.

```text
master

backend/master
└── backend/develop
    └── backend/feat/*

frontend/master
└── frontend/develop
    └── frontend/feat/*

ai/master
└── ai/develop
    └── ai/feat/*

blockchain/master
└── blockchain/develop
    └── blockchain/feat/*
```

최상위 `master`는 프로젝트 전체 기준 브랜치로 유지합니다.

일반적인 개발 과정에서는 Backend, Frontend, AI, Blockchain의 작업 내용을
최상위 `master`에 계속 병합하지 않습니다.

각 파트는 자신의 `master`와 `develop`을 기준으로 독립적으로 개발합니다.

---

## 2. 브랜치 역할

### master

프로젝트 전체의 기준 브랜치입니다.

- 저장소 최초 기준 브랜치
- 일반적인 기능 개발에서는 직접 사용하지 않음

---

### backend/master

Backend의 안정 버전 브랜치입니다.

```text
backend/feat/*
      ↓
backend/develop
      ↓
backend/master
```

역할:

- Backend의 안정된 코드 유지
- 배포 또는 시연 가능한 Backend 버전 관리
- `backend/develop`에서 충분한 테스트가 완료된 경우에만 병합
- 일반 기능 개발을 직접 진행하지 않음

---

### backend/develop

Backend의 개발 통합 브랜치입니다.

Backend 기능 브랜치는 항상 `backend/develop`에서 생성하며,
완료된 기능은 다시 `backend/develop`로 Pull Request를 생성합니다.



---

### frontend/master

Frontend의 안정 버전 브랜치입니다.

```text
frontend/feat/*
       ↓
frontend/develop
       ↓
frontend/master
```

역할:

- Frontend의 안정된 코드 유지
- 시연 및 배포 가능한 Frontend 버전 관리
- `frontend/develop`에서 검증된 코드만 병합

---

### frontend/develop

Frontend의 개발 통합 브랜치입니다.



---

### ai/master

AI 파트의 안정 버전 브랜치입니다.

```text
ai/feat/*
   ↓
ai/develop
   ↓
ai/master
```

역할:

- 검증된 AI 코드 유지
- 안정된 모델 및 추론 서버 버전 관리
- `ai/develop`에서 테스트가 완료된 코드만 병합

---

### ai/develop

AI 기능의 개발 통합 브랜치입니다.


---

### blockchain/master

Blockchain 파트의 안정 버전 브랜치입니다.

```text
blockchain/feat/*
          ↓
blockchain/develop
          ↓
blockchain/master
```

역할:

- 검증된 Smart Contract 및 Blockchain 코드 유지
- 배포 가능한 Contract 버전 관리
- `blockchain/develop`에서 충분히 검증된 코드만 병합

---

### blockchain/develop

Blockchain 기능의 개발 통합 브랜치입니다.


---

## 3. 최초 브랜치 생성

원격 저장소의 기본 브랜치가 `master`라고 가정합니다.

먼저 최신 `master`를 가져옵니다.

```bash
git checkout master
git pull origin master
```

---

### Backend 브랜치 생성

```bash
git checkout master

git checkout -b backend/master
git push -u origin backend/master

git checkout -b backend/develop
git push -u origin backend/develop
```

`backend/develop`은 `backend/master`에서 생성됩니다.

---

### Frontend 브랜치 생성

```bash
git checkout master

git checkout -b frontend/master
git push -u origin frontend/master

git checkout -b frontend/develop
git push -u origin frontend/develop
```

---

### AI 브랜치 생성

```bash
git checkout master

git checkout -b ai/master
git push -u origin ai/master

git checkout -b ai/develop
git push -u origin ai/develop
```

---

### Blockchain 브랜치 생성

```bash
git checkout master

git checkout -b blockchain/master
git push -u origin blockchain/master

git checkout -b blockchain/develop
git push -u origin blockchain/develop
```

---

## 4. 최초 생성 후 브랜치 구조

원격 저장소에는 다음 장기 브랜치가 존재합니다.

```text
master

backend/master
backend/develop

frontend/master
frontend/develop

ai/master
ai/develop

blockchain/master
blockchain/develop
```

---

## 5. 기능 개발 방법

Backend에서 Material 등록 기능을 개발한다고 가정합니다.

### 1) 최신 Backend develop 가져오기

```bash
git checkout backend/develop
git pull origin backend/develop
```

### 2) 기능 브랜치 생성

```bash
git checkout -b backend/feat/material-register
```

### 3) 개발 후 Commit

```bash
git add .
git commit -m "feat: 폐소재 등록 기능 구현"
```

### 4) 원격 Push

```bash
git push -u origin backend/feat/material-register
```

### 5) Pull Request 생성

```text
backend/feat/material-register
        ↓
backend/develop
```

리뷰와 테스트가 완료되면 `backend/develop`에 병합합니다.

### 6) 기능 브랜치 삭제

```bash
git checkout backend/develop
git pull origin backend/develop

git branch -d backend/feat/material-register
git push origin --delete backend/feat/material-register
```

---

## 6. develop → 파트 master 병합

여러 기능이 `backend/develop`에 통합되고 테스트가 완료되면
`backend/master`로 Pull Request를 생성합니다.

```text
backend/feat/*
      ↓
backend/develop
      ↓
backend/master
```

명령어로 직접 병합하는 경우:

```bash
git checkout backend/master
git pull origin backend/master

git merge backend/develop
git push origin backend/master
```

하지만 팀 프로젝트에서는 직접 Merge보다 다음 방식이 권장됩니다.


Frontend, AI, Blockchain도 동일하게 운영합니다.

```text
frontend/develop → frontend/master
ai/develop → ai/master
blockchain/develop → blockchain/master
```

---

## 7. 각 파트의 개발 흐름

### Backend

```text
backend/master
      ↑
backend/develop
      ↑
backend/feat/*
```

### Frontend

```text
frontend/master
       ↑
frontend/develop
       ↑
frontend/feat/*
```

### AI

```text
ai/master
   ↑
ai/develop
   ↑
ai/feat/*
```

### Blockchain

```text
blockchain/master
          ↑
blockchain/develop
          ↑
blockchain/feat/*
```

---

## 8. 브랜치 네이밍 규칙

### 기능 개발

```text
{part}/feat/{feature-name}
```

예시:

```text
backend/feat/auth-login
backend/feat/material-register
backend/feat/material-order

frontend/feat/material-detail
frontend/feat/qr-passport

ai/feat/material-classification

blockchain/feat/material-token
blockchain/feat/escrow-contract
```

### 버그 수정

```text
{part}/fix/{issue-name}
```

예시:

```text
backend/fix/order-lock
frontend/fix/material-layout
ai/fix/inference-error
blockchain/fix/token-burn
```

### 리팩토링

```text
{part}/refactor/{target}
```

예시:

```text
backend/refactor/material-service
frontend/refactor/api-client
ai/refactor/inference-pipeline
blockchain/refactor/token-contract
```

---

## 9. Commit Message 규칙

Conventional Commit 스타일을 사용합니다.

```text
feat: 새로운 기능
fix: 버그 수정
refactor: 기능 변경 없는 코드 구조 개선
docs: 문서 변경
test: 테스트 코드
chore: 설정 및 기타 작업
```

예시:

```text
feat: 폐소재 등록 API 구현
feat: Material Token 발행 기능 구현
fix: 주문 수량 동시성 문제 수정
refactor: MaterialService 구조 개선
docs: 브랜치 전략 추가
test: MaterialService 테스트 추가
chore: Docker Compose 설정 추가
```

필요하면 scope를 사용할 수 있습니다.

```text
feat(material): 폐소재 등록 API 구현
feat(auth): JWT 로그인 구현
feat(blockchain): Material Token mint 구현
```

---

## 10. Pull Request 규칙

각 파트의 `develop` 및 `master`에는 직접 Push하지 않고
Pull Request를 통해 병합하는 것을 권장합니다.

기능 개발:

```text
backend/feat/material-register
        ↓ PR
backend/develop
```

안정 버전 반영:


권장 규칙:

- 최소 1명 이상 리뷰
- Conflict 해결 후 Merge
- 테스트 성공 후 Merge
- 기능 단위로 PR 생성
- 너무 많은 기능을 하나의 PR에 포함하지 않음
- Merge 완료된 기능 브랜치는 삭제
- 각 파트의 `master`, `develop` 직접 Push 금지 권장

---

## 11. 중요 규칙

### 기능 브랜치는 반드시 자신의 파트 develop에서 생성

잘못된 예:

```text
master
   ↓
backend/feat/material-register
```

권장:


---

### 기능 브랜치는 다른 파트에 직접 병합하지 않음

Backend 기능은:

```text
backend/feat/*
      ↓
backend/develop
```

Frontend 기능은:

```text
frontend/feat/*
       ↓
frontend/develop
```

AI 기능은:

```text
ai/feat/*
   ↓
ai/develop
```

Blockchain 기능은:

```text
blockchain/feat/*
          ↓
blockchain/develop
```

---

### 파트별 master는 안정 버전만 유지

예를 들어 Backend의 일반적인 개발은:

```text
backend/feat/*
      ↓
backend/develop
```

에서 진행합니다.

`backend/master`에는 기능 하나가 완성될 때마다 바로 병합하는 것이 아니라,
여러 기능이 통합되고 충분한 테스트가 끝난 시점에 반영합니다.

---


---

## 12. 최종 브랜치 구조

```text
master

backend/master
└── backend/develop
    ├── backend/feat/auth-login
    ├── backend/feat/material-register
    ├── backend/feat/material-marketplace
    ├── backend/feat/transaction
    ├── backend/feat/processing
    └── backend/feat/production

frontend/master
└── frontend/develop
    ├── frontend/feat/auth
    ├── frontend/feat/material-marketplace
    ├── frontend/feat/inventory
    └── frontend/feat/material-passport

ai/master
└── ai/develop
    └── ai/feat/*

blockchain/master
└── blockchain/develop
    ├── blockchain/feat/material-token
    ├── blockchain/feat/escrow
    └── blockchain/feat/traceability
```

---

## 13. 정리

각 파트는 다음 3단계 구조를 가집니다.

```text
feat/*
  ↓
develop
  ↓
master
```

예를 들어 Backend는:

```text
backend/feat/*
      ↓
backend/develop
      ↓
backend/master
```

- `feat/*`: 실제 기능 개발용 단기 브랜치
- `develop`: 파트 내부 기능 통합 및 테스트
- `master`: 검증된 안정 버전

기능 브랜치는 작업 완료 후 삭제하고,
각 파트의 `develop`과 `master`는 프로젝트 기간 동안 유지하는 장기 브랜치입니다.
