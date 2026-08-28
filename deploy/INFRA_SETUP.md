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
| Jenkins | 8080 | 8081 | O (보안그룹 제한) | `http://<EC2>:8081` |

- 외부에 여는 포트는 **80 / 443 / 8081 / 22(SSH)** 만.
- MySQL, Backend, AI는 Docker 내부 네트워크로만 통신한다.

## 2. EC2 지급 전 체크리스트

지급 전 로컬에서 완료 가능한 작업:

- [x] `deploy/master` 브랜치에 배포 스켈레톤 작성
  - `docker-compose.yml` (운영 구성) / `docker-compose.local.yml` (로컬 검증용 override)
  - `nginx/nginx.conf` — `/` → Frontend, `/api` → Backend 리버스 프록시
  - `jenkins/*.Jenkinsfile` — backend / frontend / ai / blockchain 파이프라인 초안
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
- [ ] 팀 공유: 포트 맵 / 이미지 이름 규약 / `.env` 키 목록 공지
- [ ] GitLab Access Token(또는 Deploy Token) 발급 — Jenkins가 리포를 clone할 때 사용
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
- Pipeline Job 4개 자동 등록 (Push 트리거 + 브랜치 필터 포함):

| Job | 트리거 브랜치 | Jenkinsfile (deploy/master 기준) |
| --- | --- | --- |
| backend | `backend/master` | `jenkins/backend.Jenkinsfile` |
| frontend | `frontend/master` | `jenkins/frontend.Jenkinsfile` |
| ai | `ai/master` | `jenkins/ai.Jenkinsfile` |
| blockchain | `blockchain/develop` | `jenkins/blockchain.Jenkinsfile` (컴파일·테스트만, 배포 없음) |

### 남은 수동 작업 (EC2에서)

- `.env`에 `JENKINS_ADMIN_PASSWORD`, `GITLAB_TOKEN` 실값 입력 — 기본값(admin/changeme)은 로컬 검증용이므로 반드시 교체
- GitLab webhook 등록: 각 Job에 `http://<EC2 IP>:8081/project/<job>` (Push events)

## 5. EC2 Day-1 런북

지급 당일 아래 순서대로 실행한다.

1. **SSH 접속 확인** — 지급받은 pem 키 권한 설정 후 접속, 키는 팀 비밀 저장소에 보관
2. **최초 세팅** — `deploy/master`의 `scripts/init-ec2.sh` 내용 실행 (Docker, Docker Compose, swap, 타임존)
3. **리포 clone** — `git clone -b deploy/master <repo-url> ~/deploy`
4. **`.env` 작성** — `.env.example`을 복사해 실값 채움 (Besu 값은 1번에게 받음)
5. **기동** — `docker compose up -d` → `bash scripts/health-check.sh`
6. **Jenkins 확인** — `http://<EC2 IP>:8081` 접속, `.env`의 admin 계정으로 로그인. 플러그인·계정·Credential·Job은 JCasC로 자동 세팅됨 (4장 참고)
7. **GitLab webhook 연결** — 각 Job에 `http://<EC2 IP>:8081/project/<job>` webhook 등록, Push 테스트
8. **도메인 / HTTPS** — SSAFY 제공 도메인(`*.p.ssafy.io`) 확인 후 certbot으로 인증서 발급, `nginx.conf`의 HTTPS 블록 활성화
9. **보안그룹 확인** — 22, 80, 443, 8081만 오픈 (8081은 가능하면 팀 IP 대역으로 제한)

여기까지 완료되면 DEVELOPMENT_STRATEGY.md의 6번 첫 목표
**"빈 프로젝트라도 Merge하면 Jenkins를 통해 EC2에 올라가는 상태"** 달성.

## 6. EC2 없이는 할 수 없는 작업

아래는 미리 준비만 해 두고, 지급 후 진행한다.

- GitLab webhook ↔ Jenkins 연결 (공인 URL 필요)
- HTTPS 인증서 발급 (도메인 필요)
- 배포 스크립트의 실제 서버 경로·계정 확정
- 보안그룹 / 방화벽 포트 오픈
- 실제 애플리케이션 이미지 배포 (각 파트 스켈레톤이 나온 뒤)

## 7. deploy/master 구성 요약

배포 설정의 Source of Truth는 `deploy/master` 브랜치다. (GIT_CONVENTION: develop/feat 하위 브랜치 없이 직접 관리)

```text
deploy/master
├── docker-compose.yml         # 운영 구성 (nginx, frontend, backend, ai, mysql, jenkins)
├── docker-compose.local.yml   # 로컬 검증용 override (placeholder 이미지)
├── .env.example
├── nginx/nginx.conf
├── jenkins/
│   ├── Dockerfile             # Docker CLI 포함 Jenkins 이미지
│   ├── backend.Jenkinsfile
│   ├── frontend.Jenkinsfile
│   ├── ai.Jenkinsfile
│   └── blockchain.Jenkinsfile
├── scripts/
│   ├── init-ec2.sh
│   ├── deploy-backend.sh
│   ├── deploy-frontend.sh
│   ├── deploy-ai.sh
│   └── health-check.sh
└── README.md                  # 로컬 검증 / EC2 배포 방법
```

각 애플리케이션의 `Dockerfile`은 각 파트 master 브랜치(backend/frontend/ai)에서 관리하고,
deploy는 이미지의 **실행·연결·배포 방식**만 관리한다. 이미지 이름 규약:

```text
circular-fashion/backend:latest
circular-fashion/frontend:latest
circular-fashion/ai:latest
```
