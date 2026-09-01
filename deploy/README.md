# deploy/master

Circular-Fashion의 배포 구성을 관리하는 브랜치입니다.
Docker Compose, Nginx, Jenkins Job 구조, 배포 스크립트의 **Source of Truth**이며,
GIT_CONVENTION에 따라 develop / feat 하위 브랜치 없이 이 브랜치에서 직접 관리합니다.

> EC2 지급 전 체크리스트와 Day-1 런북은 이 브랜치의 `docs/INFRA_SETUP.md` 참고.

## 구성

```text
├── docker-compose.yml         # 운영 구성 (nginx, frontend, backend, ai, mysql, jenkins)
├── docker-compose.local.yml   # 로컬 검증용 override (placeholder 이미지)
├── .env.example               # 환경변수 키 목록 (실값 커밋 금지)
├── nginx/nginx.conf           # 리버스 프록시: / → frontend, /api → backend
├── certbot/www/               # certbot webroot (HTTPS 인증서 발급용)
├── jenkins/
│   ├── Dockerfile             # Docker CLI + 사전 설치 플러그인 포함 Jenkins 이미지
│   ├── plugins.txt            # 사전 설치 플러그인 목록
│   └── casc.yaml              # JCasC — 계정/Credential/Multibranch Job 4개 자동 세팅
├── scripts/
│   ├── init-ec2.sh            # EC2 최초 세팅 (Docker, swap, UFW, 타임존)
│   ├── compose.sh             # compose 호출의 단일 경로 (override 가 있으면 자동 합성)
│   ├── deploy-service.sh      # 배포 + 헬스체크 실패 시 롤백 (본체)
│   ├── deploy-*.sh            # 파트 Jenkinsfile이 부르는 진입점 (deploy-service.sh 호출)
│   ├── health-check.sh        # 컨테이너 + 엔드포인트 확인 (2xx만 통과)
│   ├── gitlab-status.sh       # 빌드 결과를 GitLab 커밋 상태로 보고 (초록 체크/빨간 X)
│   ├── shell-aliases.sh       # 서버 단축 명령 (~/.bashrc 가 source)
│   ├── backup-mysql.sh        # MySQL 일일 덤프 (cron 03:10)
│   ├── restore-mysql.sh       # 덤프로 복구
│   ├── install-backup-cron.sh # 백업 cron 등록 (멱등)
│   └── pull-backups.sh        # 덤프를 EC2 밖(작업용 PC)으로 반출 — 로컬에서 실행
└── docs/
    ├── INFRA_SETUP.md         # 구축 체크리스트 / 배경
    ├── EC2_DAY1.md            # 서버 수령 첫날 런북
    ├── DEVELOPMENT_STRATEGY.md
    └── notices/               # 팀 공지 (날짜순)

파이프라인 정의(`Jenkinsfile`)는 이 브랜치가 아니라 **각 파트 브랜치 루트**에 있습니다.
deploy/master 는 Job 구조(`casc.yaml`)와 배포 방식만 관리합니다.
```

## 이미지 이름 규약

애플리케이션 `Dockerfile`과 `Jenkinsfile`은 각 파트 브랜치에서 관리하고, 빌드 결과 이미지 이름은 다음으로 고정합니다.

```text
circular-fashion/backend:latest
circular-fashion/frontend:latest
circular-fashion/ai:latest
```

## 서버 접속 / 운영 단축 명령

로컬 `~/.ssh/config` 에 별칭을 두면 짧게 접속할 수 있습니다.

```
Host e104 cf j15e104
    HostName j15e104.p.ssafy.io
    User ubuntu
    IdentityFile ~/.ssh/J15E104T.pem
    IdentitiesOnly yes
```

```bash
ssh e104        # 세 이름 중 아무거나
```

접속하면 `scripts/shell-aliases.sh` 가 자동으로 로드됩니다 (`~/.bashrc` 에서 source).

| 명령 | 설명 |
| --- | --- |
| `cfps` | 컨테이너 목록 |
| `cfhealth [서비스]` | 정상 여부 — 엔드포인트 응답까지 확인 |
| `cflog <서비스> [N]` | 로그 마지막 N줄 (기본 50) |
| `cftail <서비스>` | 로그 실시간 (Ctrl+C 종료) |
| `cfsh <서비스>` | 컨테이너 안으로 |
| `cfdb` | MySQL 접속 — **비밀번호 입력 불필요** |
| `cfpull` | `git pull` + 변경분 재기동 |
| `cfhelp` | 위 목록 다시 보기 |

`cfdb` 는 컨테이너가 이미 갖고 있는 `MYSQL_ROOT_PASSWORD` 를 `MYSQL_PWD` 로 넘깁니다.
비밀번호를 약하게 바꾸는 대신 **칠 일이 없게** 만든 것입니다.

## 접속 주소

| | 주소 |
| --- | --- |
| 서비스 | https://j15e104.p.ssafy.io |
| Jenkins | https://j15e104.p.ssafy.io/jenkins/ (8081 직접 접속은 루프백 전용) |

## 로컬 검증 (EC2 없이)

로컬 PC에는 `circular-fashion/*` 이미지가 없으므로 placeholder 이미지로 대체해
프록시·네트워크·볼륨 구성을 검증합니다.

```bash
cp .env.example .env          # MYSQL_* 값만 임의로 채우면 됨
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build

# 확인
curl http://localhost/            # frontend placeholder 응답
curl http://localhost/api/health  # backend placeholder(whoami) 응답
# Jenkins: http://localhost:8081/jenkins/

# 정리
docker compose -f docker-compose.yml -f docker-compose.local.yml down
```

## EC2 배포 (재구축 시)

```bash
bash scripts/init-ec2.sh                          # 최초 1회
git clone -b deploy/master <REPO_URL> ~/deploy
cd ~/deploy && cp .env.example .env && vi .env    # 실값 입력
# 첫 기동 — 새 서버에는 파트 이미지가 없으므로 local override 를 함께 쓴다
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
bash scripts/health-check.sh
# 이후 Jenkins 에서 backend/frontend/ai 의 <파트>/master 빌드를 실행하면
# deploy-*.sh 가 실제 이미지로 교체한다. DB 는 scripts/restore-mysql.sh 로 복구.
```

평상시 CI는 각 파트의 develop / feat 브랜치 push에서, 배포는 각 파트 master 브랜치 push에서
Jenkins Multibranch Pipeline이 자동 수행합니다. 배포 실패(헬스체크 불통과) 시 직전 이미지로
자동 롤백됩니다. 단, **스마트 컨트랙트는 자동 배포하지 않고** 수동 승인 후 담당자가 직접 배포합니다.

## 주의사항

- `.env`, pem 키, private key는 절대 커밋하지 않습니다. `.env` 실값은 EC2에만 존재하므로
  팀 비밀 저장소 백업이 필수입니다.
- Jenkins는 8081 을 `127.0.0.1` 에만 바인딩하고 외부 접근은 nginx https `/jenkins/` 로만 받습니다.
  **Docker publish 포트는 UFW를 우회**하므로, 포트를 막는 수단은 방화벽이 아니라 바인딩 주소입니다.
- Jenkins 컨테이너는 root + docker.sock 을 가지므로 호스트 root 권한과 동등합니다.
  파이프라인에서 `set -x` 금지, `.env`를 `archiveArtifacts` 하지 말 것.
- **설정 파일은 디렉터리 단위로 마운트**합니다. 단일 파일 bind mount는 inode에 고정되어
  `git pull` 후에도 컨테이너가 옛 내용을 봅니다 (2026-09-01 실사고).
- 컨테이너는 비root(uid 10001)로 실행합니다 — backend/ai 적용, frontend는 내부 nginx가
  80 포트를 쓰므로 예외, jenkins는 docker.sock 때문에 root(위 항목 참고).
- HTTPS 적용 완료 (`j15e104.p.ssafy.io`, 만료 2026-11-29, `certbot.timer` 자동 갱신).
- MySQL은 매일 03:10 자동 백업(`~/backups/mysql`, 14일 보관), 팀원 PC로의 반출은
  `scripts/pull-backups.sh`.
