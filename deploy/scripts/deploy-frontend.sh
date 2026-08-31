#!/usr/bin/env bash
# 최신 circular-fashion/frontend:latest 이미지로 frontend 컨테이너를 재기동한다.
# Jenkins의 frontend 파이프라인이 frontend/master 빌드에서 호출한다 (컨테이너 안에서 실행됨).
set -euo pipefail

SERVICE="frontend"
IMAGE="circular-fashion/${SERVICE}:latest"
CONTAINER="cf-${SERVICE}"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
. "$DEPLOY_DIR/scripts/compose.sh"

# 1) 배포할 이미지가 로컬에 실제로 있는지 먼저 본다.
#    없는 채로 compose 를 부르면 registry pull 을 시도해 엉뚱한 에러가 난다.
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "FAIL: $IMAGE 이미지가 없습니다. 파이프라인의 Docker Build 스테이지를 확인하세요."
    exit 1
fi

# 2) 해당 서비스만 재기동 (--no-deps: mysql 등 의존 서비스는 건드리지 않는다)
dc up -d --no-deps "$SERVICE"

# 3) placeholder 졸업 확인.
#    override 가 아직 이 서비스를 덮어쓰고 있으면 방금 만든 이미지가 아니라 placeholder 가 뜬다.
#    조용히 잘못 배포되는 대신, 무엇을 해야 하는지 알려주며 실패한다.
ACTUAL="$(docker inspect --format '{{.Config.Image}}' "$CONTAINER" 2>/dev/null || echo unknown)"
if [ "$ACTUAL" != "$IMAGE" ]; then
    cat <<MSG
FAIL: $SERVICE 가 아직 placeholder 로 실행 중입니다 (현재 이미지: $ACTUAL).
  → deploy/master 의 docker-compose.placeholder.yml 에서 '$SERVICE:' 블록을 삭제하고 커밋한 뒤,
    EC2 에서 'cd ~/deploy && git pull' 후 이 파이프라인을 다시 실행하세요.
MSG
    exit 1
fi

# 4) 헬스체크
bash scripts/health-check.sh "$SERVICE"

# 5) 교체되어 떠도는 이미지 정리 (EC2 디스크 가득참 예방).
#    prune -a 는 쓰지 않는다 — 잠시 내려둔 서비스의 베이스 이미지까지 지워 재다운로드를 유발한다.
docker image prune -f >/dev/null || true
