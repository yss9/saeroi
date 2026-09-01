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

- 외부에 여는 포트는 **80 / 443 / 22(SSH)** 만 — SSAFY EC2는 UFW로 관리(기본 22만 허용, `init-ec2.sh`가 나머지 오픈, **enable 유지 필수**).
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
- [ ] MetaMask/Besu 관련 값은 1번(Blockchain)에게 RPC URL·Chain ID 확인 요청

## 3. 환경변수 / 시크릿 목록

실값은 절대 커밋하지 않는다. EC2에서는 `deploy` 디렉터리의 `.env` 파일 + Jenkins Credentials로 관리한다.
전체 키 목록은 `deploy/master`의 `.env.example` 참고. 핵심 요약:

| 분류 | 키 | 출처 |
| --- | --- | --- |
| DB | `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` | Infra가 생성 |
| Backend | `SPRING_DATASOURCE_*`, `JWT_SECRET`, `AI_SERVER_BASE_URL` | Infra + Backend 합의 |
| Blockchain | `BESU_RPC_URL`, `BESU_CHAIN_ID`, `BLOCKCHAIN_SERVICE_PRIVATE_KEY` | 1번 Blockchain 담당 |
| Frontend 빌드 | `VITE_API_BASE_URL`, `VITE_BESU_*`, `VITE_*_ADDRESS` | 빌드 시점 주입 |
| Jenkins | `JENKINS_ADMIN_ID/PASSWORD`, `JENKINS_EXTERNAL_URL`, `GITLAB_USERNAME/TOKEN` | Infra가 생성 (JCasC에서 사용) |

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
- [ ] `BESU_RPC_URL` / `BESU_CHAIN_ID` / 컨트랙트 주소 수령 후 `.env` 반영 (1번 대기 중)

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
