#!/usr/bin/env bash
# 파트 공통 배포 루틴. deploy-<part>.sh 가 서비스 이름 하나만 넘겨 호출한다.
# (예전에는 deploy-backend/frontend/ai.sh 가 SERVICE= 한 줄만 다른 완전 중복이라
#  한쪽만 고쳐지는 드리프트 위험이 있었다.)
#
# 사용법: bash scripts/deploy-service.sh <backend|frontend|ai>
set -euo pipefail

SERVICE="${1:?사용법: deploy-service.sh <backend|frontend|ai>}"
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

# 2) 롤백 대상 확보 — 지금 떠 있는 컨테이너의 이미지 ID 를 기억해 둔다.
#    단, 그 이미지가 "우리 파트 이미지"일 때만 롤백 대상으로 삼는다.
#    placeholder(traefik/whoami 등)로 되돌리면 3) 의 졸업 가드가 통과해 버려서
#    placeholder 가 정상 배포로 위장되기 때문이다. 첫 배포에는 롤백 대상이 없다.
ROLLBACK_ID=""
PREVIOUS_ID="$(docker inspect --format '{{.Image}}' "$CONTAINER" 2>/dev/null || true)"
if [ -n "$PREVIOUS_ID" ]; then
    PREVIOUS_TAGS="$(docker inspect --format '{{join .RepoTags ","}}' "$PREVIOUS_ID" 2>/dev/null || true)"
    case ",$PREVIOUS_TAGS," in
        *",circular-fashion/${SERVICE}:"*) ROLLBACK_ID="$PREVIOUS_ID" ;;
    esac
fi
if [ -z "$ROLLBACK_ID" ]; then
    echo "INFO: 롤백 대상 없음 (현재 컨테이너가 $SERVICE 이미지가 아님 — 첫 배포로 간주)."
fi

# 3) 해당 서비스만 재기동 (--no-deps: mysql 등 의존 서비스는 건드리지 않는다)
dc up -d --no-deps "$SERVICE"

# 4) 실제로 우리 이미지가 떴는지 확인.
#    compose override 가 이 서비스를 덮어쓰고 있으면 방금 만든 이미지가 아니라 다른 것이 뜬다.
#    (2026-09-01 이전에는 placeholder override 가 그 역할을 했다 — 지금은 모두 졸업했지만
#     override 가 다시 생길 수 있으므로 가드는 유지한다.)
#    조용히 잘못 배포되는 대신, 무엇을 해야 하는지 알려주며 실패한다.
ACTUAL="$(docker inspect --format '{{.Config.Image}}' "$CONTAINER" 2>/dev/null || echo unknown)"
if [ "$ACTUAL" != "$IMAGE" ]; then
    cat <<MSG
FAIL: $SERVICE 컨테이너가 $IMAGE 가 아닌 다른 이미지로 실행 중입니다 (현재: $ACTUAL).
  → compose override 가 이 서비스를 덮어쓰고 있는지 확인하세요.
    deploy/master 에 docker-compose.placeholder.yml 같은 override 가 있다면 '$SERVICE:' 블록을
    삭제하고 커밋 → MR → EC2 에서 'cd ~/deploy && git pull' 후 파이프라인을 다시 실행하세요.
MSG
    exit 1
fi

# 5) 헬스체크. 실패하면 이전 이미지로 되돌린다 —
#    되돌리지 않으면 깨진 컨테이너가 그대로 트래픽을 받는다.
#    compose 가 :latest 로 서비스를 고정하므로, 롤백은 :latest 태그를 옛 이미지로 되돌린 뒤 재기동하는 방식이다.
if ! bash scripts/health-check.sh "$SERVICE"; then
    if [ -n "$ROLLBACK_ID" ]; then
        echo "ROLLBACK: 헬스체크 실패 — $IMAGE 를 이전 이미지($ROLLBACK_ID)로 되돌립니다."
        docker tag "$ROLLBACK_ID" "$IMAGE"
        dc up -d --no-deps "$SERVICE"
        if bash scripts/health-check.sh "$SERVICE"; then
            echo "ROLLBACK 완료: 이전 버전으로 복구되었습니다. 이번 빌드는 배포되지 않았습니다."
        else
            echo "ROLLBACK 실패: 이전 이미지로도 헬스체크가 통과하지 않습니다. 수동 확인이 필요합니다."
        fi
    else
        echo "FAIL: 헬스체크 실패. 되돌릴 이전 이미지가 없어 현재 상태를 유지합니다."
    fi
    exit 1
fi

# 6) 교체되어 떠도는 이미지 정리 (EC2 디스크 가득참 예방).
#    prune -a 는 쓰지 않는다 — 잠시 내려둔 서비스의 베이스 이미지까지 지워 재다운로드를 유발한다.
docker image prune -f >/dev/null || true
