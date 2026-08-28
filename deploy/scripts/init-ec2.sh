#!/usr/bin/env bash
# EC2 최초 세팅 스크립트 (Ubuntu 기준)
# 사용법: 지급받은 EC2에 접속한 뒤 이 파일을 복사해 실행
#   bash init-ec2.sh
set -euo pipefail

echo "[1/4] 타임존 설정"
sudo timedatectl set-timezone Asia/Seoul

echo "[2/4] swap 설정 (4G, 없을 때만)"
if ! sudo swapon --show | grep -q .; then
    sudo fallocate -l 4G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo "[3/4] Docker + Compose plugin 설치"
if ! command -v docker >/dev/null; then
    curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"

echo "[4/4] 완료"
cat <<'GUIDE'
남은 수동 작업:
  1. 재로그인 (docker 그룹 권한 적용)
  2. git clone -b deploy/master <REPO_URL> ~/deploy
  3. cd ~/deploy && cp .env.example .env && vi .env   # 실값 입력
  4. docker compose up -d --build
  5. bash scripts/health-check.sh
  6. Jenkins 초기 세팅: http://<EC2 IP>:8081
     초기 비밀번호: docker exec cf-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
자세한 순서는 deploy/master의 INFRA_SETUP.md 5장(EC2 Day-1 런북) 참고.
GUIDE
