#!/usr/bin/env bash
# 컨테이너 상태와 해당 서비스의 엔드포인트를 확인한다.
# 사용법:
#   bash scripts/health-check.sh            # 전체 확인
#   bash scripts/health-check.sh backend    # backend 컨테이너 + backend 엔드포인트만
#
# 서비스별로 검사 대상을 나누는 이유: 예전에는 인자와 무관하게 세 엔드포인트를 모두 검사해서,
# frontend 를 배포했는데 backend 의 /api/health 가 없어 실패하는 오탐이 났다.
set -uo pipefail
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
. "$DEPLOY_DIR/scripts/compose.sh"

SERVICE="${1:-}"
FAIL=0

# 이 스크립트는 호스트에서도, Jenkins 컨테이너 안에서도 실행된다.
# 컨테이너 안에서는 localhost 가 자기 자신이라 nginx 에 닿지 못하므로 서비스명으로 붙는다
# (Jenkins 와 nginx 는 같은 web 네트워크에 있다).
if [ -f /.dockerenv ]; then
    WEB_BASE="${HEALTHCHECK_WEB_BASE:-http://nginx}"
    JENKINS_BASE="${HEALTHCHECK_JENKINS_BASE:-http://localhost:8080}"
else
    WEB_BASE="${HEALTHCHECK_WEB_BASE:-http://localhost}"
    JENKINS_BASE="${HEALTHCHECK_JENKINS_BASE:-http://localhost:8081}"
fi

echo "── 컨테이너 상태 ─────────────────────────"
dc ps ${SERVICE:+"$SERVICE"}

if [ -n "$SERVICE" ]; then
    STATE=$(dc ps --format '{{.State}}' "$SERVICE" 2>/dev/null || echo "missing")
    if [ "$STATE" != "running" ]; then
        echo "FAIL: $SERVICE 컨테이너가 running 상태가 아님 ($STATE)"
        FAIL=1
    fi
fi

echo "── 엔드포인트 확인 (기준 URL: $WEB_BASE) ──"
check() {
    local name="$1" url="$2"
    local code
    code=$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 5 "$url" 2>/dev/null) \
        && echo "OK  : $name ($url -> $code)" \
        || { echo "FAIL: $name ($url)"; FAIL=1; }
}

case "${SERVICE:-all}" in
    frontend)
        check "nginx → frontend" "$WEB_BASE/"
        ;;
    backend)
        # backend 에 GET /api/health 가 있어야 통과한다. 없으면 배포가 성공해도 여기서 FAIL 이 뜬다
        # — 버그가 아니라 "헬스 엔드포인트가 배포 성공 조건"이라는 신호다.
        check "nginx → backend " "$WEB_BASE/api/health"
        ;;
    ai)
        # ai 는 internal 네트워크 전용이라 외부 엔드포인트가 없다. 컨테이너 상태로만 판정.
        echo "INFO: ai 는 internal 전용 — 컨테이너 상태만 확인함"
        ;;
    mysql|jenkins|nginx)
        echo "INFO: $SERVICE 는 컨테이너 상태만 확인함"
        ;;
    all)
        check "nginx → frontend" "$WEB_BASE/"
        check "nginx → backend " "$WEB_BASE/api/health"
        check "jenkins         " "$JENKINS_BASE/login"
        ;;
    *)
        echo "INFO: 알 수 없는 서비스 '$SERVICE' — 컨테이너 상태만 확인함"
        ;;
esac

exit $FAIL
