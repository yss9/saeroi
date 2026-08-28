#!/usr/bin/env bash
# 컨테이너 상태와 핵심 엔드포인트를 확인한다.
# 사용법:
#   bash scripts/health-check.sh            # 전체 확인
#   bash scripts/health-check.sh backend    # 특정 서비스 컨테이너 상태 + 전체 엔드포인트 확인
set -uo pipefail
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"

SERVICE="${1:-}"
FAIL=0

echo "── 컨테이너 상태 ─────────────────────────"
docker compose ps ${SERVICE:+"$SERVICE"}

if [ -n "$SERVICE" ]; then
    STATE=$(docker compose ps --format '{{.State}}' "$SERVICE" 2>/dev/null || echo "missing")
    if [ "$STATE" != "running" ]; then
        echo "FAIL: $SERVICE 컨테이너가 running 상태가 아님 ($STATE)"
        FAIL=1
    fi
fi

echo "── 엔드포인트 확인 ───────────────────────"
check() {
    local name="$1" url="$2"
    local code
    code=$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 5 "$url" 2>/dev/null) \
        && echo "OK  : $name ($url -> $code)" \
        || { echo "FAIL: $name ($url)"; FAIL=1; }
}

check "nginx → frontend" "http://localhost/"
check "nginx → backend " "http://localhost/api/health"
check "jenkins         " "http://localhost:8081/login"

exit $FAIL
