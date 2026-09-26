# EC2 사전 인프라 작업 (Infra Setup)

> EC2 서버 지급 전(D-7)에 6번 Infra 담당이 미리 끝낼 수 있는 작업과,
> 지급 당일 실행할 런북을 정리한 문서입니다.
> 목표: **EC2를 받는 날 `서버 세팅 → clone → docker compose up → Jenkins 접속`까지 최소 작업으로 끝나는 상태.**

---

## 목차

1. [포트 맵](#1-포트-맵)
2. [EC2 지급 전 체크리스트](#2-ec2-지급-전-체크리스트)
3. [환경변수 / 시크릿 목록](#3-환경변수--시크릿-목록)
4. [Jenkins 초기 세팅 목록](#4-jenkins-초기-세팅-목록)
5. [EC2 Day-1 런북](#5-ec2-day-1-런북)
6. [EC2 없이는 할 수 없는 작업](#6-ec2-없이는-할-수-없는-작업)
7. [deploy/master 구성 요약](#7-deploymaster-구성-요약)

---

## 1. 포트 맵

| 서비스 | 컨테이너 포트 | 호스트 노출 | 외부 공개 | 비고 |
| --- | --- | --- | --- | --- |
| Nginx | 80, 443 | 80, 443 | O | 유일한 서비스 진입점 |
| Frontend (React) | 80 | - | X | Nginx가 `/` 프록시 |
| Backend (Spring Boot) | 8080 | - | X | Nginx가 `/api` 프록시 |
| AI Server (FastAPI) | 8000 | - | X | Backend만 내부 호출 |
| MySQL | 3306 | - (로컬 검증 시만 3306) | X | 내부 네트워크 전용 |
| Jenkins | 8080 | 127.0.0.1:8081 | X (nginx `/jenkins/` 경유) | `https://j15e104.p.ssafy.io/jenkins/` — 8081 직접 공개는 2026-09-01 폐지 |
| MinIO (Object Storage) | 9000, 9001 | - | X | Backend만 내부 호출. 콘솔(9001)도 열지 않는다 — 루트 키 하나로 모든 증빙에 닿는다 |

- 외부에 여는 포트는 **80 / 443 / 22(SSH)** 만 — SSAFY EC2는 UFW로 관리(기본 22만 허용, `init-ec2.sh`가 나머지 오픈, **enable 유지 필수**).
- **test 스택은 새 포트를 열지 않습니다.** 기존 443 nginx 뒤에 `/test/`(프론트)·`/test/api/`(백엔드)
  경로만 추가되고, `cft-*` 컨테이너는 포트를 host에 노출하지 않습니다 (§7-1 참고).
- **Docker publish 포트는 UFW를 우회**하므로 호스트 전용 포트는 compose에서 `127.0.0.1:` 바인딩으로 막는다 (예: 로컬 override의 MySQL 3306).
- (참고) GPU 서버: Jupyter `http://70.12.130.109`, 계정 `j15e104`, **Device1 지정 사용** — AI 파트 소관.
- MySQL, Backend, AI는 Docker 내부 네트워크로만 통신한다.

## 2. EC2 지급 전 체크리스트

지급 전 로컬에서 완료 가능한 작업:

- [x] `deploy/master` 브랜치에 배포 스켈레톤 작성
  - `docker-compose.yml` (운영 구성) / `docker-compose.local.yml` (로컬 검증용 override)
  - `nginx/nginx.conf` — `/` → Frontend, `/api` → Backend 리버스 프록시
  - `jenkins/casc.yaml` — Multibranch Job 4개 정의 (Jenkinsfile 은 각 파트 브랜치가 소유)
  - `jenkins/Dockerfile` — Docker CLI가 포함된 Jenkins 이미지
  - `scripts/init-ec2.sh` — EC2 최초 세팅 스크립트
  - `scripts/deploy-*.sh`, `scripts/health-check.sh`
  - `.env.example` — 전체 환경변수 키 목록
- [x] Jenkins 자동 세팅(JCasC) — 플러그인 사전 설치, admin 계정, gitlab-token Credential, Job 4개 자동 등록
- [x] 컨테이너 로그 로테이션 (서비스별 10MB x 3) — 디스크 가득참 예방
- [x] Nginx certbot webroot(`/.well-known/acme-challenge/`) / gzip / 프록시 버퍼 설정
- [x] 로컬 Docker Desktop에서 전체 구성 기동 검증
  - Nginx → Frontend/Backend placeholder 프록시 동작
  - MySQL volume 영속성
  - Jenkins 기동 및 초기 비밀번호 확인
- [x] 팀 공유: 포트 맵 / 이미지 이름 규약 / `.env` 키 목록 공지 (2026-08-31, `notices/TEAM_NOTICE_2026-08-31.md`)
- [x] GitLab Access Token(또는 Deploy Token) 발급 — Jenkins가 리포를 clone할 때 사용
- [x] Besu RPC URL·Chain ID 수령 (2026-09-01) — SSAFY 제공 외부 노드 `https://rpc.ssafy-blockchain.com`,
      Chain ID `31221`. HTTPS 443 공개라 우리 쪽 방화벽·nginx 작업은 없다 (`scripts/check-besu.sh` 로 검증)

## 3. 환경변수 / 시크릿 목록

실값은 절대 커밋하지 않는다. EC2에서는 `deploy` 디렉터리의 `.env` 파일 + Jenkins Credentials로 관리한다.
전체 키 목록은 `deploy/master`의 `.env.example` 참고. 핵심 요약:

| 분류 | 키 | 출처 |
| --- | --- | --- |
| DB | `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` | Infra가 생성 |
| Backend | `SPRING_DATASOURCE_*`, `JWT_SECRET`, `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL`, `AI_SERVER_BASE_URL` | Infra + Backend 합의 |
| Backend (지갑 인증) | `WALLET_NONCE_TTL` — 서명 nonce 유효시간(ISO-8601 Duration, 기본 `PT5M`) | Backend `app.auth.wallet.nonce-ttl` |
| Backend (CORS) | `CORS_ALLOWED_ORIGINS` — 콤마 구분 허용 origin, 기본값은 `localhost:5173`뿐이라 **실제 서비스 도메인을 반드시 넣어야 한다** (없으면 로그인이 403 "Invalid CORS request"로 막힌다, 2026-09-11 발견) | Backend `app.cors.allowed-origins` |
| Backend (주소 검증) | `KAKAO_REST_API_KEY` — 없으면 **사업자 회원가입·주소 변경이 의도적으로 거절된다** (좌표 없는 업체 저장 방지, 2026-09-11 발견). [카카오 디벨로퍼스](https://developers.kakao.com)에서 앱 생성 후 발급 — 인프라가 값을 만들어낼 수 없다 | Backend `app.geocoding.kakao.rest-api-key` |
| Backend (결제) | `KAKAOPAY_SECRET_KEY`(운영 PRD / test는 DEV 키 구분), `KAKAOPAY_CID`(기본 `TC0ONETIME`), `KAKAOPAY_RETURN_URL`, `KAKAOPAY_PROCESSING_RETURN_URL` — return-url 기본값이 `localhost:5173`이라 반드시 실제 도메인으로 넘겨야 한다(2026-09-11 발견) | Backend `app.payment.kakaopay.*` |
| Backend (QR) | `QR_BASE_URL` — QR 에 인쇄되는 Passport 화면 베이스 URL(끝이 `/qr`). backend/develop 에서 **필수값** — 비어 있으면 기동 거부. 운영 `.env`·compose 전달 경로는 2026-09-18 추가(그전엔 test 에만 있어 master 승격 시 기동 실패로 롤백될 상태였다) | Backend `app.qr.base-url` |
| Backend (예측시장) | `PREDICTION_MARKET_ADDRESS`, `PREDICTION_POINT_ADDRESS`(정본 `ssafy.json` predictionMarket/predictionPoint), `PREDICTION_OPERATOR_PRIVATE_KEY`(MARKET_CREATOR/ORACLE/MINTER role 계정 — 1번이 준다, 미수령), `PREDICTION_ORACLE_ENABLED`(기본 false). RPC/체인은 비우면 `BESU_*` 를 따른다. 비어 있으면 예측시장 API 만 거절 | Backend `app.prediction.*` |
| Blockchain | `BESU_RPC_URL`, `BESU_CHAIN_ID`, `BLOCKCHAIN_SERVICE_PRIVATE_KEY`, `MATERIAL_TOKEN_ADDRESS`, `MARKETPLACE_ADDRESS`, `PRODUCTION_REGISTRY_ADDRESS` — 정본은 `blockchain/develop:deployments/ssafy.json`. Escrow는 안 쓴다(폐기). 2026-09-11 확정: 이전 `PAYMENT_TOKEN_ADDRESS`/`ESCROW_ADDRESS`는 backend가 안 읽는 죽은 키였다 | 1번 Blockchain 담당 |
| Frontend 빌드 | `VITE_API_BASE_URL`, `VITE_BESU_*`, `VITE_CHAIN_FROM_BLOCK`, `VITE_MATERIAL_CONTRACT_ADDRESS`, `VITE_MARKETPLACE_CONTRACT_ADDRESS`, `VITE_PROCESSING_CONTRACT_ADDRESS`, `VITE_PRODUCTION_CONTRACT_ADDRESS`, `VITE_PREDICTION_POINT_CONTRACT_ADDRESS`, `VITE_PREDICTION_MARKET_CONTRACT_ADDRESS`(2026-09-18 추가 — Dockerfile ARG 는 있었는데 `.env` 에 없어 예측시장 화면이 빈 주소로 빌드되고 있었다), `VITE_BESU_EXPLORER_URL`(익스플로러 없음, 비움) — 2026-09-11 확정: 이전 `VITE_*_TOKEN_ADDRESS`/`VITE_ESCROW_ADDRESS`는 `docker/Dockerfile`의 실제 ARG 이름과 달라 값이 조용히 버려지는 죽은 키였다. **`VITE_CHAIN_FROM_BLOCK`은 2026-09-14까지 `.env.example`에도 빠져 있던 죽은 키** — 컨트랙트 배포 블록(`11221171`, `deployments/ssafy.json`의 `deployedAt` 기준 이진 탐색으로 실측)을 안 채우면 소재 등록 시 MetaMask 팝업 전 전체 체인 스캔으로 수십 초가 걸린다 | 빌드 시점 주입 |
| Jenkins | `JENKINS_ADMIN_ID/PASSWORD`, `JENKINS_EXTERNAL_URL`, `GITLAB_USERNAME/TOKEN` | Infra가 생성 (JCasC에서 사용) |
| Object Storage | `STORAGE_TYPE`(`local`/`s3`, 없으면 `local`), `STORAGE_BUCKET`(기본 `evidence`), `STORAGE_ENDPOINT`(기본 `http://minio:9000`), `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | Infra가 생성 |

### 3-0-1. Object Storage 키 제약

- `MINIO_ROOT_PASSWORD` 는 **8자 이상**이어야 한다. 짧으면 MinIO 가 기동을 거부한다
  (`MINIO_ROOT_USER` 는 3자 이상).
- 두 키에는 **기본값을 두지 않는다.** 기본값을 두면 값이 빠진 환경에서 약한 루트 키로
  조용히 떠 버리는데, 이 키 하나로 모든 증빙 파일에 닿는다.
- 값이 없으면 `docker compose` 호출마다
  `The "MINIO_ROOT_PASSWORD" variable is not set. Defaulting to a blank string.` 경고가 나고
  **`minio` / `minio-init` 만** 뜨지 못한다. 나머지 서비스는 정상 기동한다 —
  `backend` 는 `minio` 를 기다리지 않는다(바로 아래 항목). 진단은 `cflog minio`.
- `STORAGE_TYPE=s3` 로 바꿀 때는 자격증명이 실제로 채워졌는지 확인해야 한다. 비어 있어도
  backend 는 정상 기동한 뒤 **업로드 시점에** 인증 오류를 내므로 기동 로그로는 안 보인다.

### 3-0-2. backend 는 minio 를 기다리지 않는다 (2026-09-08)

`docker-compose.yml` 의 `backend.depends_on` 에서 `minio` 를 제거했다. 저장소 자격증명이
없거나 8자 미만이면 minio 가 healthy 가 되지 못하고, 그러면 `STORAGE_TYPE` 이 기본값
`local`(MinIO 를 전혀 쓰지 않는 모드)인 상태에서도 `cf-backend` 가 생성되지 않아
`/api/health` 와 인프라 헬스체크 전체가 죽었다. 대가는 `STORAGE_TYPE=s3` 일 때 기동
직후 수 초 동안 첫 업로드가 실패할 수 있다는 것뿐이다(업로드 요청 하나가 5xx, 재시도로
해결, 저장 데이터 영향 없음). 버킷 생성을 실제로 기다려야 하는 `minio-init` 의 의존성은
그대로다.

## 3-1. nginx rate limit

`/api/v1/auth/` 하위 전체가 **IP 당 분당 10회**(버스트 20, `nodelay`)로 제한된다.
초과하면 백엔드까지 가지 않고 nginx 가 **429** 를 `ApiResponse` 봉투 모양 JSON 으로 돌려준다
(`code: TOO_MANY_REQUESTS`).

이유: `POST /api/v1/auth/wallet/nonce` 는 인증 없이 열려 있고 미등록 지갑을 즉시 거절해서
**지갑 등록 여부 오라클**이 된다. `/verify` 는 서명 대입 반복이 가능하다.

`/api/v1/auth/` 아래에 엔드포인트를 추가하면 자동으로 이 제한을 받는다.
`/test/api/v1/auth/`(test 스택)에도 같은 zone 으로 같은 제한이 걸린다(2026-09-18 — 그전엔 test 만 무제한이라
25회 연타가 전부 통과했다). zone 을 공유하므로 같은 IP 가 운영·test 를 합쳐 분당 10회다.
공지: `docs/notices/TEAM_NOTICE_2026-09-07-auth-rate-limit.md`

## 4. Jenkins 초기 세팅 목록

Jenkins 세팅은 JCasC(`jenkins/casc.yaml`)로 자동화되어 있다. 컨테이너가 뜨면 아래가 자동 적용된다.

### 자동으로 되는 것

- 플러그인 설치 — `jenkins/plugins.txt` 기준으로 이미지 빌드 시 사전 설치 (초기 설치 마법사 생략)
- admin 계정 생성 — `.env`의 `JENKINS_ADMIN_ID` / `JENKINS_ADMIN_PASSWORD`
- `gitlab-token` Credential 등록 — `.env`의 `GITLAB_USERNAME` / `GITLAB_TOKEN`
- **Multibranch Pipeline Job 4개** 자동 등록 (파트당 1개, 브랜치 자동 발견)

### 파이프라인 정의 위치 — deploy/master 가 아니라 각 파트 브랜치

`Jenkinsfile`은 **각 파트 브랜치 루트**에 있고, 파트가 소유한다. deploy/master 는 Job 구조와
배포 방식(compose·nginx·`scripts/deploy-*.sh`)만 관리한다. 파트원이 빌드 스텝을 고칠 때
인프라 담당을 거치지 않아도 된다.

| Job | 발견 브랜치 | CI (테스트) | CD (이미지 빌드 + 배포) |
| --- | --- | --- | --- |
| backend | `backend/master·develop·feat/*·fix/*·refactor/*` | 전 브랜치 | `backend/master` 만 |
| frontend | `frontend/*` (동일 규칙) | 전 브랜치 | `frontend/master` 만 |
| ai | `ai/*` (동일 규칙) | 전 브랜치 | `ai/master` 만 |
| blockchain | `blockchain/develop·feat/*·fix/*·refactor/*` | 전 브랜치 | **없음** (컨트랙트는 수동 배포) |

- 새 feat 브랜치를 만들면 Job이 **자동 생성**되고, 브랜치를 지우면 자동 회수된다.
  회수는 즉시가 아니라 **브랜치가 원격에서 사라진 뒤 2일**이다 (`orphanedItemStrategy`,
  2026-09-07에 7일 → 2일). 원격에 살아 있는 브랜치의 Job은 오래돼도 지워지지 않는다 —
  UI에서 지워도 다음 인덱싱(최대 5분)에 되살아난다.
- 컨테이너 시간대는 **Asia/Seoul**이다 (`docker-compose.yml`의 `TZ`). 이걸 안 주면 호스트가
  KST여도 컨테이너는 UTC로 떠서 빌드 시각이 9시간 어긋난다.
- `Jenkinsfile`이 없는 브랜치는 Job이 생기지 않는다 (실패가 아니라 미생성). 파트가 준비되는 순서대로 켜진다.
- `includes` 필터에 없는 ref(`deploy/master`, `master`, 타 파트 브랜치)는 인덱싱 단계에서 버려진다.

> **MR 파이프라인은 없다.** MR 소스 브랜치(`backend/feat/x`)가 필터에 걸리므로 MR에 올린
> 모든 커밋은 push 시점에 이미 CI를 통과한다.
>
> 2026-09-01 부터 **GitLab 화면에도 결과가 표시된다.** 각 파트 Jenkinsfile 이
> `scripts/gitlab-status.sh` 로 GitLab Commit Status API 에 보고하므로 커밋·브랜치·MR 에
> 초록 체크/빨간 X 가 뜬다. GitLab CI 파이프라인을 돌리는 것이 아니라 Jenkins 결과를
> 되돌려주는 방식이다.
> `gitlab-branch-source`를 쓰지 않은 이유: MR을 발견하면 SCM head가 `MR-12` 형태가 되어
> 파트 필터에 걸리지 않고, 필터를 넓히면 **4개 Job이 모든 파트의 MR을 발견**해
> frontend Job이 backend MR을 빌드하는 사고가 난다.
>
> **⚠️ MR의 초록불은 MR 소스 브랜치 커밋까지만 검증한 결과다 (2026-09-17 실사고로 확인).**
> 머지 후 `<파트>/develop`에서 도는 빌드(이미지 빌드 + Test Deploy + 스키마 대조 등,
> `develop`에서만 도는 스테이지)는 **다른(머지) 커밋**에서 실행되고, 그 결과는 이미
> 닫힌 MR 화면에 저절로 나타나지 않는다 — "MR이 초록이었으니 배포까지 됐다"는
> 판단은 틀릴 수 있다. develop 빌드 실패 여부는 Repository → Commits에서
> `<파트>/develop` 브랜치의 최신 커밋 상태로 직접 확인해야 한다.
> 2026-09-17부터 `gitlab-status.sh`가 develop 빌드 실패 시 그 커밋을 만든 MR에도
> 코멘트를 남긴다(커밋 메시지의 `See merge request ...!<iid>` 트레일러로 역추적) —
> 다만 이건 코멘트일 뿐 MR의 초록/빨간 배지 자체를 바꾸지는 않는다.

### 남은 수동 작업 (EC2에서)

- `.env`에 `JENKINS_ADMIN_PASSWORD`, `GITLAB_TOKEN` 실값 입력 — 기본값(admin/changeme)은 로컬 검증용이므로 반드시 교체
- GitLab webhook **1개** 등록 (Job별 등록 불필요 — 리포 단위로 모든 Multibranch Job이 인덱싱된다):
  ```
  https://j15e104.p.ssafy.io/jenkins/git/notifyCommit?url=https://lab.ssafy.com/s15-blockchain-pred-sub1/S15P21E104.git
  Push events 만 체크. Secret token 비움.
  ```
  webhook이 유실돼도 5분 주기 스캔이 안전망으로 잡는다.

## 5. EC2 Day-1 런북 (2026-08-31 완료)

지급 당일 아래 순서대로 실행한다.

1. **SSH 접속 확인** — 지급받은 pem 키 권한 설정 후 접속, 키는 팀 비밀 저장소에 보관
2. **최초 세팅** — `deploy/master`의 `scripts/init-ec2.sh` 내용 실행 (Docker, Docker Compose, swap, 타임존)
3. **리포 clone** — `git clone -b deploy/master <repo-url> ~/deploy`
4. **`.env` 작성** — `.env.example`을 복사해 실값 채움 (Besu 값은 1번에게 받음)
5. **기동** — 새 서버에는 파트 이미지가 없으므로 `docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build` → `bash scripts/health-check.sh`
6. **Jenkins 확인** — `https://<도메인>/jenkins/` 접속(HTTPS 발급 전이면 `curl http://localhost:8081/jenkins/login`), `.env`의 admin 계정으로 로그인. 플러그인·계정·Credential·Job은 JCasC로 자동 세팅됨 (4장 참고)
7. **GitLab webhook 연결** — 리포 단위 1개: `https://<도메인>/jenkins/git/notifyCommit?url=<repo>.git`, Push 테스트 (4장 참고)
8. **도메인 / HTTPS** — 확정 도메인 `j15e104.p.ssafy.io`로 certbot 인증서 발급, `nginx.conf`의 HTTPS 블록 활성화
9. **UFW 확인** — 22, 80, 443만 허용 + enable 유지 (`init-ec2.sh`가 처리, `sudo ufw status`로 검증)

**2026-08-31 전 단계 완료** — DEVELOPMENT_STRATEGY.md의 6번 첫 목표
**"빈 프로젝트라도 Merge하면 Jenkins를 통해 EC2에 올라가는 상태"** 달성.
HTTPS까지 적용되어 서비스는 `https://j15e104.p.ssafy.io`, Jenkins는 `https://j15e104.p.ssafy.io/jenkins/`.
상세 실행 이력과 트러블슈팅은 `EC2_DAY1.md`, 팀 공지는 `notices/TEAM_NOTICE_2026-08-31.md` 참고.

## 6. 남은 작업 (EC2 지급 후)

지급 전 "EC2 없이는 할 수 없는 작업"으로 미뤄둔 항목의 현재 상태:

- [x] GitLab webhook ↔ Jenkins 연결 (4개 Job)
- [x] HTTPS 인증서 발급 — Let's Encrypt, 만료 2026-11-29, `certbot.timer` 자동 갱신 + nginx reload deploy hook
- [x] UFW 포트 오픈 및 접속 검증
- [ ] 배포 스크립트의 실제 서버 경로·계정 확정 — 현재 `~/deploy` 기준, 첫 실배포 때 검증 필요
- [x] 실제 애플리케이션 이미지 배포 (2026-09-01) — backend/frontend/ai 모두 졸업,
      `docker-compose.placeholder.yml` 삭제됨
- [x] `BESU_RPC_URL` / `BESU_CHAIN_ID` 수령 및 `.env.example` 반영 (2026-09-01)
- [x] Chain ID 실측 확인 (2026-09-01, 개발 PC) — `eth_chainId` → `0x79f5` = 31221 일치,
      `admin_peers` 차단 확인, IP allowlist 없음 확인
- [x] EC2 `.env` 에 Besu 값 반영 + EC2 에서 `bash scripts/check-besu.sh` 실행 (2026-09-01) — `결과: OK`.
      `cf-backend` **컨테이너 안에서** 실행해 확인했다 (호스트/개발 PC 성공은 컨테이너 경로를 증명하지 않는다).
      backend 재생성으로 환경변수 주입까지 실증
- [x] 컨트랙트 환경변수 **이름 확정** (2026-09-01) — `MATERIAL_TOKEN_ADDRESS` / `PAYMENT_TOKEN_ADDRESS` /
      `ESCROW_ADDRESS` (+ `VITE_` 접두 3종). `.env.example` 과 일치, `docker-compose.yml` 에 주입 추가 완료
- [x] `BLOCKCHAIN_SERVICE_PRIVATE_KEY` 수령·EC2 `.env` 반영 (2026-09-01). **0x 없는 16진수 64자.**
      컨테이너 주입 확인, 기동 로그에 키 미노출 확인
- [x] **가스 잔액은 문제가 아니다 — 프리가스 네트워크로 확인 (2026-09-01)**
      `eth_gasPrice` → `0x0`, 최신 블록에 `baseFeePerGas` 필드 자체가 없음(EIP-1559 미적용),
      `gasLimit` `0x1fffffffffffff`. 즉 **잔액 0 인 계정으로도 트랜잭션이 나간다.**
      `PaymentToken.sol` 주석("SSAFY Besu 네트워크는 네이티브 코인을 지급받을 수 없으므로
      결제 수단을 직접 발행한다")과도 일치한다 — 코인을 안 주는 대신 가스를 안 받는 구성이다.
      `scripts/check-besu.sh` 가 매번 `eth_gasPrice` 를 확인하므로, SSAFY 가 나중에 유료로
      바꾸면 그때 드러난다
- [ ] **🔴 서명 계정의 컨트랙트 role 확인 — 가스보다 이쪽이 진짜 관문이다.**
      `MaterialToken` 은 `AccessControl` 기반이라 `mintMaterial()` 은 `SUPPLIER_ROLE`,
      `verifyMaterial()` 은 `VERIFIER_ROLE`, `setMaterialStatus()` 는 `STATUS_MANAGER_ROLE` 이
      필요하다. 생성자는 **admin 에게만** 전 권한을 준다. 서버 서명 계정이 admin 이 아니면
      가스가 공짜여도 `AccessControlUnauthorizedAccount` 로 리버트된다.
      1번에게 확인할 것: 서명 계정 주소, 그 계정이 가진 role, 필요하면 `grantRole` 요청.
      주소와 컨트랙트 주소를 받으면 `hasRole()` 을 직접 조회해 확인할 수 있다
- [ ] Frontend / Backend 담당 배정 시 `docs/notices/BESU_REQUESTS_FE_BE.md` 전달
- [ ] 컨트랙트 주소 3종 수령 (1번 실배포 전 — 미정). `.env` 에 값만 채우면 backend 는 컨테이너
      재생성으로 반영되지만, **frontend 는 이미지 재빌드가 필요하다** (`VITE_*` 는 빌드 시점 인라인)
- [ ] 컨트랙트 주소 **freeze 시점 합의** — 재배포하면 주소가 바뀌고 프론트 재빌드·재배포가 따라온다
- [ ] `Escrow` 컨트랙트 실체 확인 — `ESCROW_ADDRESS` 자리는 잡아뒀으나 `blockchain/develop` 에는
      `MaterialToken.sol` / `PaymentToken.sol` 둘뿐이고 `Escrow.sol` 과 그 ABI 가 아직 없다

## 7. deploy/master 구성 요약

배포 설정의 Source of Truth는 `deploy/master` 브랜치다. (GIT_CONVENTION: develop/feat 하위 브랜치 없이 직접 관리)

```text
deploy/master
├── docker-compose.yml         # 운영 구성 (nginx, frontend, backend, ai, mysql, jenkins)
├── docker-compose.local.yml   # 로컬 검증용 override (placeholder 이미지)
├── .env.example
├── nginx/nginx.conf
├── jenkins/
│   ├── Dockerfile             # Docker CLI + Node/Python 툴체인 포함 Jenkins 이미지
│   ├── plugins.txt
│   └── casc.yaml              # Multibranch Job 4개 정의 (Jenkinsfile은 파트 브랜치에 있음)
├── scripts/
│   ├── init-ec2.sh
│   ├── compose.sh             # compose 호출의 단일 경로 (override 가 있으면 자동 합성)
│   ├── deploy-service.sh      # 배포 + 헬스체크 실패 시 롤백 (본체)
│   ├── deploy-{backend,frontend,ai}.sh   # 파트 Jenkinsfile 이 부르는 진입점
│   ├── health-check.sh        # 2xx 만 통과, 기동 대기 최대 90초
│   ├── backup-mysql.sh / restore-mysql.sh / install-backup-cron.sh
│   └── pull-backups.sh        # 백업 반출 — 팀원 로컬에서 실행
└── README.md                  # 로컬 검증 / EC2 배포 방법
```

각 애플리케이션의 `Dockerfile`과 `Jenkinsfile`은 각 파트 브랜치에서 관리하고,
deploy는 이미지의 **실행·연결·배포 방식**만 관리한다. 이미지 이름 규약:

```text
circular-fashion/backend:latest
circular-fashion/frontend:latest
circular-fashion/ai:latest
```

## 7-1. test 스택 (`/test`, 상시 통합 테스트 환경)

목적: `<파트>/develop` 병합(=CI 통과)과 실제로 눈으로/API로 확인하는 시점 사이의 공백을
메운다. Deploy 스테이지는 `when { branch '<파트>/master' }` 조건이라 develop 에 아무리
병합돼도 지금까지는 EC2 에 아무것도 뜨지 않았다.

- 트리거: 각 파트(`backend`/`frontend`/`ai`) `Jenkinsfile`의 `<파트>/develop` 스테이지가
  이미지를 `:test` 태그로 빌드·push 하고, SSH로 `scripts/test-deploy.sh <service>`를 호출
- 구성: `docker-compose.test.yml` — 별도 프로젝트 `circular-fashion-test`, 컨테이너 접두사
  `cft-*`, 프론트/백엔드만 운영 nginx의 `circular-fashion_web` 네트워크에 추가 조인
  (nginx가 `resolver 127.0.0.11` 동적 resolve로 찾는다), mysql/minio는 별도 internal
  네트워크·별도 자격증명(`.env.test`)으로 운영과 완전히 분리
- 경로: `https://j15e104.p.ssafy.io/test/`(프론트), `/test/api/`(백엔드) — 새 포트 없음
- 배포 단위: `--no-deps`로 병합된 서비스 하나만 재기동 — 한 파트 병합이 다른 파트 서비스를
  건드리지 않는다
- `blockchain`은 대상 아님 (이미지 빌드 대상이 아니고 컨트랙트는 수동 배포)
- 롤백: 각 파트 Jenkinsfile 이 `:test` 와 `:test-<빌드번호>` 를 함께 찍어 직전 이미지가 태그를 잃지 않게
  한다(frontend 2026-09-11, backend/ai 2026-09-18 — 그전엔 backend/ai 의 모든 test 배포가 "롤백 대상 없음"
  이었다). 번호 태그는 `prune-images.sh` 크론이 서비스별 최신 5개만 남긴다(`PRUNE_TEST_TAG_KEEP`)
- 자동 마이그레이션 전 백업: 호스트 `~/backups/mysql/<DB>-test-<시각>-before-auto-migration.sql.gz`
  (2026-09-18 — 그전엔 Jenkins 컨테이너 `/root/backups` 에만 쌓여 컨테이너 재생성 시 소실되는 위치였다)
- 운영 배포 preflight(`deploy-service.sh`)의 스키마 기준은 `origin/backend/master`(배포하는 브랜치), test
  preflight(`test-deploy.sh`)의 기준은 `origin/backend/develop` 이다 — 2026-09-18 전에는 운영도 develop 기준이라
  develop 이 앞서 있는 평상시에 정상 배포를 막을 수 있는 상태였다
