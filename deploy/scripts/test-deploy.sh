#!/usr/bin/env bash
# test 스택 서비스 단위 배포. 각 파트 Jenkinsfile 의 <파트>/develop 스테이지가
# 이미지를 :test 태그로 빌드한 뒤 SSH 로 이 스크립트를 호출한다.
#
# 사용법: bash scripts/test-deploy.sh <backend|frontend|ai>
#
# scripts/deploy-service.sh(운영)와 같은 패턴이다 — 이미지 존재 확인 → --no-deps 배포 →
# 헬스체크 → 롤백. 차이는 태그(:test)와 컨테이너 접두사(cft-)뿐이다.
# **반드시 --no-deps** — 한 파트만 develop 에 병합해도 다른 파트 서비스(mysql/minio 포함)가
# 재기동되면 안 된다. deploy-service.sh 의 "이미지 정리는 여기서 하지 않는다" 원칙도 그대로
# 따른다 — prune 은 scripts/prune-images.sh 크론에만 맡긴다.
set -euo pipefail

SERVICE="${1:?사용법: test-deploy.sh <backend|frontend|ai>}"
IMAGE="circular-fashion/${SERVICE}:test"
CONTAINER="cft-${SERVICE}"

# docker-compose.test.yml 의 서비스 키는 backend/frontend 가 아니라 test-backend/
# test-frontend 다 — 운영과 같은 circular-fashion_web 네트워크에 붙을 때 Compose 가
# 서비스 키를 네트워크 별칭으로 자동 등록하는데, "backend"/"frontend" 를 그대로 쓰면
# 운영 cf-backend/cf-frontend 와 별칭이 충돌해 nginx 재시작 시 운영 트래픽이 test 로
# 샐 수 있다. ai/mysql/minio 는 internal 전용이라 충돌이 없어 그대로 둔다.
case "$SERVICE" in
    backend)  COMPOSE_SVC="test-backend" ;;
    frontend) COMPOSE_SVC="test-frontend" ;;
    *)        COMPOSE_SVC="$SERVICE" ;;
esac

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
. "$DEPLOY_DIR/scripts/compose-test.sh"

# 1) 배포할 이미지가 로컬에 실제로 있는지 먼저 본다.
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "FAIL: $IMAGE 이미지가 없습니다. Jenkinsfile 의 :test 빌드 스테이지를 확인하세요."
    exit 1
fi

# 2) 롤백 대상 확보 — 지금 떠 있는 test 컨테이너의 이미지 ID.
#    태그가 아니라 "circular-fashion/${SERVICE}:" 로 시작하는 태그가 하나라도 있으면
#    롤백 대상으로 삼는다(운영 deploy-service.sh 와 동일한 판정). 정확히 ":test" 태그만
#    찾으면 안 된다 — Jenkinsfile 이 새 이미지를 빌드하며 ":test" 를 새 이미지로 옮기고 나면
#    지금 떠 있는(이전) 이미지는 그 태그를 이미 잃은 뒤이기 때문이다. Jenkinsfile 이 같이
#    붙이는 ":test-$BUILD_NUMBER" 처럼 서비스별 다른 태그가 하나라도 남아있으면 잡아낸다.
ROLLBACK_ID=""
PREVIOUS_ID="$(docker inspect --format '{{.Image}}' "$CONTAINER" 2>/dev/null || true)"
if [ -n "$PREVIOUS_ID" ]; then
    PREVIOUS_TAGS="$(docker inspect --format '{{join .RepoTags ","}}' "$PREVIOUS_ID" 2>/dev/null || true)"
    case ",$PREVIOUS_TAGS," in
        *",circular-fashion/${SERVICE}:"*) ROLLBACK_ID="$PREVIOUS_ID" ;;
    esac
fi
if [ -z "$ROLLBACK_ID" ]; then
    echo "INFO: 롤백 대상 없음 (현재 컨테이너가 ${SERVICE} 이미지가 아님 — 첫 배포로 간주)."
fi

# 3) 해당 서비스만 재기동 (--no-deps: mysql/minio 등은 건드리지 않는다)
dc_test up -d --no-deps "$COMPOSE_SVC"

# 4) 실제로 :test 이미지가 떴는지 확인.
ACTUAL="$(docker inspect --format '{{.Config.Image}}' "$CONTAINER" 2>/dev/null || echo unknown)"
if [ "$ACTUAL" != "$IMAGE" ]; then
    echo "FAIL: $SERVICE(test) 컨테이너가 $IMAGE 가 아닌 다른 이미지로 실행 중입니다 (현재: $ACTUAL)."
    exit 1
fi

# 5) 헬스체크. scripts/health-check.sh 를 /test/ 베이스 + test compose 모드로 재사용한다.
#    HEALTHCHECK_COMPOSE=test 를 주면 health-check.sh 내부의 컨테이너 상태 확인이
#    운영이 아니라 cft-* 컨테이너를 본다 (이전엔 항상 운영 컨테이너를 봐서, 운영 쪽
#    상태와 무관하게 test 배포가 실패/롤백될 수 있는 버그가 있었다).
if ! HEALTHCHECK_COMPOSE=test HEALTHCHECK_WEB_BASE="https://j15e104.p.ssafy.io/test" bash scripts/health-check.sh "$SERVICE"; then
    if [ -n "$ROLLBACK_ID" ]; then
        echo "ROLLBACK: 헬스체크 실패 — $IMAGE 를 이전 이미지($ROLLBACK_ID)로 되돌립니다."
        docker tag "$ROLLBACK_ID" "$IMAGE"
        dc_test up -d --no-deps "$COMPOSE_SVC"
        if HEALTHCHECK_COMPOSE=test HEALTHCHECK_WEB_BASE="https://j15e104.p.ssafy.io/test" bash scripts/health-check.sh "$SERVICE"; then
            echo "ROLLBACK 완료: 이전 버전으로 복구되었습니다. 이번 빌드는 배포되지 않았습니다."
        else
            echo "ROLLBACK 실패: 이전 이미지로도 헬스체크가 통과하지 않습니다. 수동 확인이 필요합니다."
        fi
    else
        echo "FAIL: 헬스체크 실패. 되돌릴 이전 이미지가 없어 현재 상태를 유지합니다."
    fi
    exit 1
fi
