# deploy/master

Circular-Fashion의 배포 구성을 관리하는 브랜치입니다.
Docker Compose, Nginx, Jenkins Job 구조, 배포 스크립트의 **Source of Truth**이며,
GIT_CONVENTION에 따라 develop / feat 하위 브랜치 없이 이 브랜치에서 직접 관리합니다.

> EC2 지급 전 체크리스트와 Day-1 런북은 이 브랜치의 `docs/INFRA_SETUP.md` 참고.

## 구성

```text
├── docker-compose.yml         # 운영 구성 (nginx, frontend, backend, ai, mysql, jenkins)
├── docker-compose.local.yml   # 로컬 검증용 override (placeholder 이미지)
├── docker-compose.placeholder.yml  # EC2용 override — 아직 이미지가 없는 서비스만 대체
├── .env.example               # 환경변수 키 목록 (실값 커밋 금지)
├── nginx/nginx.conf           # 리버스 프록시: / → frontend, /api → backend
├── certbot/www/               # certbot webroot (HTTPS 인증서 발급용)
├── jenkins/
│   ├── Dockerfile             # Docker CLI + 사전 설치 플러그인 포함 Jenkins 이미지
│   ├── plugins.txt            # 사전 설치 플러그인 목록
│   └── casc.yaml              # JCasC — 계정/Credential/Multibranch Job 4개 자동 세팅
├── scripts/
│   ├── init-ec2.sh            # EC2 최초 세팅 (Docker, swap, UFW, 타임존)
│   ├── compose.sh             # placeholder override 자동 합성 (compose 호출의 단일 경로)
│   ├── deploy-service.sh      # 배포 + 헬스체크 실패 시 롤백 (본체)
│   ├── deploy-*.sh            # 파트 Jenkinsfile이 부르는 진입점 (deploy-service.sh 호출)
│   ├── health-check.sh        # 컨테이너 + 엔드포인트 확인 (2xx만 통과)
│   ├── backup-mysql.sh        # MySQL 일일 덤프 (cron 03:10)
│   ├── restore-mysql.sh       # 덤프로 복구
│   └── install-backup-cron.sh # 백업 cron 등록 (멱등)
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

## 로컬 검증 (EC2 없이)

frontend/backend/ai 실제 이미지가 없어도 placeholder 이미지로 프록시·네트워크·볼륨을 검증할 수 있습니다.

```bash
cp .env.example .env          # MYSQL_* 값만 임의로 채우면 됨
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build

# 확인
curl http://localhost/            # frontend placeholder 응답
curl http://localhost/api/health  # backend placeholder(whoami) 응답
# Jenkins: http://localhost:8081

# 정리
docker compose -f docker-compose.yml -f docker-compose.local.yml down
```

## EC2 배포

```bash
bash scripts/init-ec2.sh                          # 최초 1회
git clone -b deploy/master <REPO_URL> ~/deploy
cd ~/deploy && cp .env.example .env && vi .env    # 실값 입력
docker compose up -d --build
bash scripts/health-check.sh
```

이후 CI는 각 파트의 develop / feat 브랜치 push에서, 배포는 각 파트 master 브랜치 push에서
Jenkins Multibranch Pipeline이 자동 수행합니다.
단, **스마트 컨트랙트는 자동 배포하지 않고** 수동 승인 후 담당자가 직접 배포합니다. (README 12장)

> EC2에서 compose를 직접 치지 마세요. 아직 실제 이미지가 없는 서비스가 있어
> override가 필요합니다. `scripts/` 의 스크립트를 통하면 자동으로 처리됩니다.

## 주의사항

- `.env`, pem 키, private key는 절대 커밋하지 않습니다.
- Jenkins(8081)는 UFW/보안그룹에서 팀 IP 대역으로 제한합니다. webhook 수신을 위해 공개가 필요하므로
  강력한 admin 비밀번호가 사실상 유일한 방어선입니다.
- Jenkins 컨테이너는 root + docker.sock 을 가지므로 호스트 root 권한과 동등합니다.
  파이프라인에서 `set -x` 금지, `.env`를 `archiveArtifacts` 하지 말 것.
- HTTPS 적용 완료 (`j15e104.p.ssafy.io`, 만료 2026-11-29, 자동 갱신).
