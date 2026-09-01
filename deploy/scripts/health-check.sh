#!/usr/bin/env bash
# 컨테이너 상태와 해당 서비스의 엔드포인트를 확인한다.
# 사용법:
#   bash scripts/health-check.sh            # 전체 확인
#   bash scripts/health-check.sh backend    # backend 컨테이너 + backend 엔드포인트만
#
# 서비스별로 검사 대상을 나누는 이유: 예전에는 인자와 무관하게 세 엔드포인트를 모두 검사해서,
# frontend 를 배포했는데 backend 의 /api/health 가 없어 실패하는 오탐이 났다.
#
# 판정 규칙 두 가지 (2026-09-01 첫 CD 실검증에서 드러난 결함을 고친 것):
#   1. 2xx 만 통과. 예전에는 `curl -f` 로만 봤는데 -f 는 3xx 를 실패로 보지 않아서,
#      nginx 의 80 → 443 리다이렉트(301)가 그대로 "OK" 로 찍혔다. 앱에 닿은 적도 없이 통과한 것이다.
#      그래서 평문 http 가 아니라 https(에지) 로 묻고, 응답 코드를 직접 2xx 로 검사한다.
#   2. 기동 대기. 컨테이너가 뜬 직후 1초 만에 검사하면 Spring 이 아직 포트를 열기 전이다.
#      HEALTHCHECK_TIMEOUT(기본 90초) 동안 2초 간격으로 재시도한다.
set -uo pipefail
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
. "$DEPLOY_DIR/scripts/compose.sh"

SERVICE="${1:-}"
FAIL=0

# nginx 의 443 server_name. 에지로 물을 때 Host 헤더로 넘겨야 올바른 server 블록에 붙는다.
DOMAIN="${HEALTHCHECK_DOMAIN:-j15e104.p.ssafy.io}"
TIMEOUT="${HEALTHCHECK_TIMEOUT:-90}"

# 이 스크립트는 호스트에서도, Jenkins 컨테이너 안에서도 실행된다.
# 컨테이너 안에서는 localhost 가 자기 자신이라 nginx 에 닿지 못하므로 서비스명으로 붙는다
# (Jenkins 와 nginx 는 같은 web 네트워크에 있다).
if [ -f /.dockerenv ]; then
    IN_CONTAINER=1
    EDGE="https://nginx"
    JENKINS_BASE="${HEALTHCHECK_JENKINS_BASE:-http://localhost:8080/jenkins}"
else
    IN_CONTAINER=0
    EDGE="https://localhost"
    JENKINS_BASE="${HEALTHCHECK_JENKINS_BASE:-http://localhost:8081/jenkins}"
fi
EDGE="${HEALTHCHECK_WEB_BASE:-$EDGE}"

echo "── 컨테이너 상태 ─────────────────────────"
dc ps ${SERVICE:+"$SERVICE"}

if [ -n "$SERVICE" ]; then
    STATE=$(dc ps --format '{{.State}}' "$SERVICE" 2>/dev/null || echo "missing")
    if [ "$STATE" != "running" ]; then
        echo "FAIL: $SERVICE 컨테이너가 running 상태가 아님 ($STATE)"
        FAIL=1
    fi
fi

echo "── 엔드포인트 확인 (에지: $EDGE) ──"

# probe <표시이름> <URL> [추가 curl 인자...]
# 2xx 가 나올 때까지 TIMEOUT 동안 재시도한다. 3xx/4xx/5xx 는 모두 실패다.
probe() {
    local name="$1" url="$2"
    shift 2
    local deadline=$(( SECONDS + TIMEOUT )) code=000
    while :; do
        code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$@" "$url" 2>/dev/null || echo 000)
        case "$code" in
            2??) echo "OK  : $name ($url -> $code)"; return 0 ;;
        esac
        [ "$SECONDS" -ge "$deadline" ] && break
        sleep 2
    done
    echo "FAIL: $name ($url -> $code) — 2xx 아님. 3xx 도 실패로 본다(리다이렉트는 앱에 닿았다는 증거가 아니다)."
    FAIL=1
}

# 에지(nginx) 경유 검사. 자체 서명이 아닌 정식 인증서지만, 컨테이너 안에서는 SNI 호스트와
# 접속 대상이 달라 검증이 깨지므로 -k 를 쓰고 Host 헤더로 라우팅만 맞춘다.
probe_edge() {
    probe "$1" "$EDGE$2" -k -H "Host: $DOMAIN"
}

# 오리진(컨테이너) 직접 검사. nginx 설정 문제와 앱 문제를 분리해서 볼 수 있다.
# web 네트워크 안에서만 가능하므로 컨테이너에서 실행될 때만 돈다.
probe_origin() {
    [ "$IN_CONTAINER" = 1 ] || return 0
    probe "$1" "$2"
}

case "${SERVICE:-all}" in
    frontend)
        probe_origin "frontend origin" "http://frontend:80/"
        probe_edge   "nginx → frontend" "/"
        ;;
    backend)
        probe_origin "backend origin " "http://backend:8080/api/health"
        probe_edge   "nginx → backend " "/api/health"
        ;;
    ai)
        # ai 는 internal 네트워크 전용이라 web 에 있는 Jenkins 에서도 닿지 않는다. 컨테이너 상태로만 판정.
        echo "INFO: ai 는 internal 전용 — 컨테이너 상태만 확인함"
        ;;
    mysql|jenkins|nginx)
        echo "INFO: $SERVICE 는 컨테이너 상태만 확인함"
        ;;
    all)
        probe_edge "nginx → frontend" "/"
        probe_edge "nginx → backend " "/api/health"
        probe "jenkins         " "$JENKINS_BASE/login"
        ;;
    *)
        echo "INFO: 알 수 없는 서비스 '$SERVICE' — 컨테이너 상태만 확인함"
        ;;
esac

exit $FAIL
