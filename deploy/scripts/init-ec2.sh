#!/usr/bin/env bash
# EC2 최초 세팅 스크립트 (Ubuntu 기준)
# 사용법: 지급받은 EC2에 접속한 뒤 이 파일을 복사해 실행
#   bash init-ec2.sh
set -euo pipefail

echo "[1/5] 타임존 설정"
sudo timedatectl set-timezone Asia/Seoul

echo "[2/5] swap 설정 (4G, 없을 때만)"
if ! sudo swapon --show | grep -q .; then
    sudo fallocate -l 4G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo "[3/5] UFW 포트 오픈 (SSAFY 규정: ufw는 반드시 enable 상태 유지)"
# 주의: ufw disable / deny 22 절대 금지 — SSH 잠기면 복구 불가(초기화만 가능)
sudo ufw allow 80/tcp    # nginx HTTP (443 리다이렉트 + acme-challenge)
sudo ufw allow 443/tcp   # nginx HTTPS — Jenkins 포함 모든 외부 진입점
# 8081은 열지 않는다. Jenkins는 127.0.0.1 루프백 바인딩 + nginx https /jenkins/ 경유다.
# (webhook도 https://<도메인>/jenkins/git/notifyCommit 으로 들어온다)
sudo ufw status verbose
# 참고: Docker가 publish한 포트는 iptables를 직접 조작해 UFW를 우회한다.
#       호스트 전용 포트는 compose에서 127.0.0.1:포트로 바인딩할 것.

echo "[4/5] Docker + Compose plugin 설치"
if ! command -v docker >/dev/null; then
    curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"

echo "[5/5] 완료"
cat <<'GUIDE'
남은 수동 작업:
  1. 재로그인 (docker 그룹 권한 적용)
  2. git clone -b deploy/master <REPO_URL> ~/deploy
  3. cd ~/deploy && cp .env.example .env && vi .env   # 실값 입력
  4. 첫 기동 — 새 서버에는 파트 이미지가 없으므로 local override 를 함께 쓴다:
       docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
  5. bash scripts/health-check.sh
  6. Jenkins 확인: https://j15e104.p.ssafy.io/jenkins/  (HTTPS 발급 전이면 컨테이너 안에서
     curl http://localhost:8081/jenkins/login 으로 기동만 확인)
     로그인: .env의 JENKINS_ADMIN_ID / JENKINS_ADMIN_PASSWORD (JCasC 자동 세팅)
  7. Jenkins 에서 backend/frontend/ai 의 <파트>/master 빌드 실행 → 실제 이미지로 교체됨
자세한 순서는 deploy/master의 docs/EC2_DAY1.md 런북 참고.
GUIDE
