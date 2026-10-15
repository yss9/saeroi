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

# ── 머지된 MR에 실패 코멘트 남기기 ───────────────────────────────────
# 2026-09-17 실사고 계기: MR 화면의 초록불은 그 MR 소스 브랜치 커밋에 붙은 상태일
# 뿐이다. 머지 후 <파트>/develop 에서 도는 빌드(Docker 빌드 + Test Deploy + 스키마
# 대조까지 포함)는 **다른 커밋**에서 돌고, 그 실패는 이미 닫힌 MR 화면에 저절로
# 나타나지 않는다 — 그래서 "MR이 초록이었으니 배포도 됐겠지" 라고 오판하기 쉬웠다.
#
# 커밋 상태(빨간 X)는 못 옮겨 붙이지만(그러려면 gitlab-branch-source 가 필요하고,
# 그건 MR 을 SCM head 로 잡아 파트 필터를 깨뜨려서 일부러 안 쓴다), 실패했다는
# 사실 자체는 그 커밋을 만든 MR에 **코멘트**로 남길 수 있다. GitLab 은 머지 커밋
# 메시지에 `See merge request <project>!<iid>` 트레일러를 자동으로 남기므로
# 그걸 역파싱해서 MR 번호를 찾는다 (fast-forward/squash 로 이 트레일러가 없는
# 커밋이면 조용히 건너뛴다 — 코멘트를 못 다는 것도 빌드를 막을 이유는 아니다).
#
# 같은 커밋에 대해 재시도·재빌드가 여러 번 나도 코멘트가 중복 쌓이지 않도록,
# 먼저 그 MR에 이 커밋 SHA 언급이 이미 있는지 확인하고 없을 때만 단다.
#
# 2026-09-17 테스트 중 실사고: GitLab은 커밋이 그 MR을 참조하면 "mentioned in
# commit <SHA>" 라는 시스템 노트를 **자동으로** 남긴다 — 특히 그 커밋이 MR을
# 머지하는 커밋 자신일 때 100% 생긴다. 이걸 걸러내지 않고 body 에서 SHA 앞자리를
# 문자열로만 찾으면 이 자동 시스템 노트에 매번 걸려서 "이미 있음"으로 오판하고
# 실제로는 단 한 번도 코멘트를 달지 못한다(기능이 조용히 죽어 있는 것과 같다).
# 그래서 `system` 필드가 false 인 노트(사람/스크립트가 직접 단 것)만 대상으로
# SHA 앞자리를 찾는다. python3 는 Jenkins 이미지와 EC2 호스트 양쪽에 있다
# (jenkins-multibranch-ci 메모 참고) — 혹시라도 없거나 파싱이 깨지면 "없음"으로
# 취급해 코멘트를 단다. 중복 코멘트가 한 번씩 더 다는 것이, 이 기능 자체가
# 계속 조용히 아무것도 안 하는 것보다 낫다.
if [ "$STATE" = "failed" ]; then
    MR_IID="$(git log -1 --format=%B "$SHA" 2>/dev/null | grep -oE '![0-9]+' | tail -1 | tr -d '!')"
    if [ -n "$MR_IID" ]; then
        NOTES_API="${GITLAB_API_BASE:-https://lab.ssafy.com/api/v4}/projects/$PROJECT/merge_requests/$MR_IID/notes"

        EXISTING="$(curl -sS --max-time 15 -H "PRIVATE-TOKEN: $TOKEN" \
            "${NOTES_API}?per_page=100" 2>/dev/null)"
        ALREADY_NOTED="$(printf '%s' "$EXISTING" | SHA8="${SHA:0:8}" python3 -c '
import json, os, sys
try:
    notes = json.load(sys.stdin)
except Exception:
    notes = []
sha8 = os.environ.get("SHA8", "")
found = any(isinstance(n, dict) and not n.get("system") and sha8 in n.get("body", "") for n in notes)
print("yes" if found else "no")
' 2>/dev/null)"
        case "$ALREADY_NOTED" in
            yes)
                echo "gitlab-status: MR !$MR_IID 에 이미 이 커밋(${SHA:0:8})에 대한 코멘트가 있음 — 건너뜀"
                ;;
            *)
                BODY="⚠️ 머지 후 \`$PART/$BRANCH\` 빌드가 실패했습니다 (#$BUILD, 커밋 ${SHA:0:8}).
이 MR 자체의 CI는 통과했지만, 머지된 커밋에서 도는 후속 빌드(이미지 빌드/Test Deploy 등)가 실패했다는 뜻입니다.
${URL:+로그: $URL}"
                NOTE_CODE="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 \
                    -X POST -H "PRIVATE-TOKEN: $TOKEN" \
                    --data-urlencode "body=$BODY" \
                    "$NOTES_API" 2>/dev/null || echo 000)"
                case "$NOTE_CODE" in
                    2??) echo "gitlab-status: MR !$MR_IID 에 실패 코멘트 등록 완료" ;;
                    *)   echo "gitlab-status: MR 코멘트 등록 실패 (HTTP $NOTE_CODE) — 빌드에는 영향 없음" ;;
                esac
                ;;
        esac
    fi
fi

exit 0
