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

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
. "$DEPLOY_DIR/scripts/compose-test.sh"

# 1) 배포할 이미지가 로컬에 실제로 있는지 먼저 본다.
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "FAIL: $IMAGE 이미지가 없습니다. Jenkinsfile 의 :test 빌드 스테이지를 확인하세요."
    exit 1
fi

# 2) 롤백 대상 확보 — 지금 떠 있는 test 컨테이너의 이미지 ID.
#    그 이미지가 이 파트의 :test 이미지일 때만 롤백 대상으로 삼는다 (첫 배포엔 없음).
ROLLBACK_ID=""
PREVIOUS_ID="$(docker inspect --format '{{.Image}}' "$CONTAINER" 2>/dev/null || true)"
if [ -n "$PREVIOUS_ID" ]; then
    PREVIOUS_TAGS="$(docker inspect --format '{{join .RepoTags ","}}' "$PREVIOUS_ID" 2>/dev/null || true)"
    case ",$PREVIOUS_TAGS," in
        *",circular-fashion/${SERVICE}:test,"*) ROLLBACK_ID="$PREVIOUS_ID" ;;
    esac
fi
if [ -z "$ROLLBACK_ID" ]; then
    echo "INFO: 롤백 대상 없음 (현재 컨테이너가 ${SERVICE}:test 이미지가 아님 — 첫 배포로 간주)."
fi

# 3) 해당 서비스만 재기동 (--no-deps: mysql/minio 등은 건드리지 않는다)
dc_test up -d --no-deps "$SERVICE"

# 4) 실제로 :test 이미지가 떴는지 확인.
ACTUAL="$(docker inspect --format '{{.Config.Image}}' "$CONTAINER" 2>/dev/null || echo unknown)"
if [ "$ACTUAL" != "$IMAGE" ]; then
    echo "FAIL: $SERVICE(test) 컨테이너가 $IMAGE 가 아닌 다른 이미지로 실행 중입니다 (현재: $ACTUAL)."
    exit 1
fi

# 5) 헬스체크. scripts/health-check.sh 를 /test/ 베이스로 재사용한다.
#    주의: health-check.sh 의 "origin" 체크(컨테이너 안에서 http://backend:8080/... 로 직접
#    묻는 부분)는 여전히 운영 컨테이너를 가리킨다 — test 전용 origin 체크는 아직 없다.
#    여기서 신뢰할 판정은 "edge"(HEALTHCHECK_WEB_BASE 경유) 결과뿐이다.
if ! HEALTHCHECK_WEB_BASE="https://j15e104.p.ssafy.io/test" bash scripts/health-check.sh "$SERVICE"; then
    if [ -n "$ROLLBACK_ID" ]; then
        echo "ROLLBACK: 헬스체크 실패 — $IMAGE 를 이전 이미지($ROLLBACK_ID)로 되돌립니다."
        docker tag "$ROLLBACK_ID" "$IMAGE"
        dc_test up -d --no-deps "$SERVICE"
        if HEALTHCHECK_WEB_BASE="https://j15e104.p.ssafy.io/test" bash scripts/health-check.sh "$SERVICE"; then
            echo "ROLLBACK 완료: 이전 버전으로 복구되었습니다. 이번 빌드는 배포되지 않았습니다."
        else
            echo "ROLLBACK 실패: 이전 이미지로도 헬스체크가 통과하지 않습니다. 수동 확인이 필요합니다."
        fi
    else
        echo "FAIL: 헬스체크 실패. 되돌릴 이전 이미지가 없어 현재 상태를 유지합니다."
    fi
    exit 1
fi
