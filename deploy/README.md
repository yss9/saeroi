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
│   ├── check-schema.sh        # 배포 전 스키마 검증 — DB가 엔티티보다 오래되면 앱이 기동에 실패한다
│   │                          #   없는 테이블·컬럼 + 컬럼 타입 불일치(CHAR vs VARCHAR 등)를 대조한다
│   │                          #   테이블 0개(빈 DB)와 접속 실패를 구분해 보고한다
│   ├── gitlab-status.sh       # 빌드 결과를 GitLab 커밋 상태로 보고 (초록 체크/빨간 X)
│   ├── shell-aliases.sh       # 서버 단축 명령 (~/.bashrc 가 source)
│   ├── backup-mysql.sh        # MySQL 일일 덤프 (cron 03:10)
│   ├── restore-mysql.sh       # 덤프로 복구
│   ├── install-backup-cron.sh # 백업 cron 등록 (멱등)
│   ├── prune-images.sh        # 떠도는 이미지·빌드 캐시 정리 (cron 04:20, 24h 보다 오래된 것만)
│   │                          #   ⚠️ 배포 경로에서 부르지 않는다 — 다른 파트의 빌드를 깨뜨렸다
│   ├── install-prune-cron.sh  # prune cron 등록 (멱등)
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

# ── backend 배포 전에 반드시 ─────────────────────────────
# application.yml 이 ddl-auto: validate 라서 스키마를 자동으로 만들지 않는다.
# 엔티티가 기대하는 컬럼이 DB에 없거나 타입이 다르면 Hibernate 검증이 실패해 컨테이너가 죽는다.
# 컬럼 이름만 맞고 타입이 다른 경우도 실패한다 (2026-09-08 tx_hash CHAR(66) vs VARCHAR(66) 롤백).
bash scripts/check-schema.sh          # 없는 테이블·컬럼 + 컬럼 타입 불일치 보고 (읽기만 함)
bash scripts/check-schema.sh --ddl    # 적용할 DDL 출력
SCHEMA_REF=origin/backend/master bash scripts/check-schema.sh   # 기준 브랜치 지정
# 대조 범위: 테이블 존재 / 컬럼 존재 / 컬럼 타입.
# NULL 허용·DEFAULT·인덱스는 일부러 보지 않는다 (거짓 양성이 많고 validate 도 보지 않는다).
# → OK 를 확인한 뒤에 <파트>/master 로 올려 배포를 트리거한다
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
- **Object Storage(MinIO)는 포트를 열지 않습니다.** Backend만 내부 네트워크로 붙습니다.
  콘솔(9001)도 상시 노출하지 않습니다 — 루트 자격증명 하나로 모든 증빙 파일에 닿는
  창구가 생깁니다. 필요하면 SSH 터널로 봅니다.
  버킷은 `minio-init` 잡이 만듭니다. MinIO는 기동만으로 버킷을 만들지 않고, 없는 버킷에
  업로드하면 `NoSuchBucket`이 되는데 그 실패는 사용자가 파일을 고른 뒤에 드러납니다.
  **버킷 공개 읽기를 켜지 않습니다** — 저장 키를 아는 누구나 받아 갈 수 있게 됩니다.
- **`STORAGE_TYPE`을 넣지 않으면 Backend는 로컬 파일시스템을 씁니다**(`/app/files`,
  `file-storage` 볼륨). MinIO 컨테이너는 떠 있지만 쓰이지 않습니다. `s3`로 바꾸기 전에
  **기존 파일 이관**이 필요합니다 — 바꾸기만 하면 이전 증빙의 저장 키를 찾지 못합니다.
- **배포 스크립트에서 `docker image prune` 을 부르지 않습니다** (2026-09-07 실사고).
  네 파트가 하나의 Docker 데몬을 공유하는데, prune 이 부르는 containerd content GC 가
  다른 파트의 진행 중인 빌드 ingest 블롭을 지워 `failed to export layer` 로 죽였습니다
  (frontend 배포 07:02:24 → backend 빌드 07:02:31 실패). 테스트는 전부 통과하고
  이미지 export 에서만 죽어서 코드 문제로 보이는 것이 고약합니다.
  정리는 `scripts/prune-images.sh` 가 cron(04:20)으로 하고, 24h 보다 오래된 것만 지웁니다.
  Jenkinsfile 의 `disableConcurrentBuilds()` 는 **같은 job 안에서만** 직렬화하므로
  파트 간 동시 실행은 막아 주지 않습니다.
