#!/usr/bin/env bash
# Jenkins 빌드 결과를 GitLab 커밋 상태로 보고한다.
# 이걸 붙이면 GitLab 의 커밋/브랜치/MR 화면에 초록 체크·빨간 X 가 뜬다.
#
# 사용법 (각 파트 Jenkinsfile 에서 호출):
#   bash /home/ubuntu/deploy/scripts/gitlab-status.sh running
#   bash /home/ubuntu/deploy/scripts/gitlab-status.sh success
#   bash /home/ubuntu/deploy/scripts/gitlab-status.sh failed
#
# 설계 원칙 두 가지:
#   1. 절대 빌드를 깨뜨리지 않는다. 보고 실패는 경고만 남기고 exit 0 한다 —
#      GitLab 이 잠깐 느리다고 초록 빌드가 빨갛게 변하면 안 된다.
#   2. 토큰을 절대 출력하지 않는다. set -x 금지, curl 실패 응답도 그대로 찍지 않는다.
set -uo pipefail

STATE="${1:-}"
case "$STATE" in
    running|success|failed|canceled) ;;
    *) echo "gitlab-status: 사용법 gitlab-status.sh <running|success|failed|canceled>"; exit 0 ;;
esac

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"

TOKEN="${GITLAB_TOKEN:-}"
if [ -z "$TOKEN" ] && [ -f "$ENV_FILE" ]; then
    # 첫 '=' 뒤 전체를 값으로 본다. 예전에는 awk -F= '{print $2}' 였는데 값 안의 '=' 에서
    # 잘렸다. base64 비밀번호는 보통 '=' 로 끝나므로(openssl rand -base64) 잘린 값으로
    # 접속해 조용히 실패할 수 있었다.
    env_value() { sed -n "s/^$1=//p" "$ENV_FILE" | head -1; }
    TOKEN="$(env_value GITLAB_TOKEN)"
fi
[ -n "$TOKEN" ] || { echo "gitlab-status: GITLAB_TOKEN 없음 — 보고를 건너뜁니다."; exit 0; }

# Jenkins 가 주는 값들. 하나라도 없으면 조용히 넘어간다 (로컬 실행 등).
SHA="${GIT_COMMIT:-}"
[ -n "$SHA" ] || { echo "gitlab-status: GIT_COMMIT 없음 — 보고를 건너뜁니다."; exit 0; }

# JOB_NAME 은 multibranch 에서 "backend/backend%2Fdevelop" 형태다. 첫 구간이 파트 이름.
PART="${2:-${JOB_NAME%%/*}}"
[ -n "$PART" ] || PART="jenkins"

BRANCH="${BRANCH_NAME:-unknown}"
BUILD="${BUILD_NUMBER:-?}"
URL="${BUILD_URL:-}"

case "$STATE" in
    running)  DESC="$BRANCH #$BUILD 실행 중" ;;
    success)  DESC="$BRANCH #$BUILD 통과" ;;
    failed)   DESC="$BRANCH #$BUILD 실패" ;;
    canceled) DESC="$BRANCH #$BUILD 취소됨" ;;
esac

PROJECT="s15-blockchain-pred-sub1%2FS15P21E104"
API="${GITLAB_API_BASE:-https://lab.ssafy.com/api/v4}/projects/$PROJECT/statuses/$SHA"

# --max-time: GitLab 이 응답하지 않아도 빌드를 붙잡아두지 않는다.
CODE="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 \
    -X POST -H "PRIVATE-TOKEN: $TOKEN" \
    --data-urlencode "state=$STATE" \
    --data-urlencode "name=jenkins/$PART" \
    --data-urlencode "description=$DESC" \
    ${URL:+--data-urlencode "target_url=$URL"} \
    "$API" 2>/dev/null || echo 000)"

case "$CODE" in
    2??) echo "gitlab-status: $STATE 보고 완료 (jenkins/$PART, ${SHA:0:8})" ;;
    *)   echo "gitlab-status: 보고 실패 (HTTP $CODE) — 빌드에는 영향 없음" ;;
esac
exit 0
