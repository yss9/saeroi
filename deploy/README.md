# deploy/master

Circular-Fashion의 배포 구성을 관리하는 브랜치입니다.
Docker Compose, Nginx, Jenkins Pipeline, 배포 스크립트의 **Source of Truth**이며,
GIT_CONVENTION에 따라 develop / feat 하위 브랜치 없이 이 브랜치에서 직접 관리합니다.

> EC2 지급 전 체크리스트와 Day-1 런북은 이 브랜치의 `INFRA_SETUP.md` 참고.

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
│   ├── casc.yaml              # JCasC — 계정/Credential/Job 4개 자동 세팅
│   └── *.Jenkinsfile          # backend / frontend / ai / blockchain 파이프라인
└── scripts/
    ├── init-ec2.sh            # EC2 최초 세팅 (Docker, swap, 타임존)
    ├── deploy-*.sh            # 서비스별 재배포
    └── health-check.sh        # 컨테이너 + 엔드포인트 확인
```

## 이미지 이름 규약

애플리케이션 `Dockerfile`은 각 파트 master 브랜치에서 관리하고, 빌드 결과 이미지 이름은 다음으로 고정합니다.

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

이후 애플리케이션 배포는 각 파트 master 브랜치 push → Jenkins Pipeline이 자동 수행합니다.
단, **스마트 컨트랙트는 자동 배포하지 않고** 수동 승인 후 담당자가 직접 배포합니다. (README 12장)

## 주의사항

- `.env`, pem 키, private key는 절대 커밋하지 않습니다.
- Jenkins(8081)는 보안그룹에서 팀 IP 대역으로 제한합니다.
- `jenkins/*.Jenkinsfile`의 `REPO_URL`(TODO)을 실제 GitLab 주소로 교체해야 합니다.
- HTTPS는 EC2에서 도메인 확인 후 `nginx/nginx.conf`의 443 템플릿을 활성화합니다.
