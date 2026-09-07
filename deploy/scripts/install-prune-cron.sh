#!/usr/bin/env bash
# prune-images.sh 를 ubuntu 사용자 crontab 에 등록한다 (멱등 — 여러 번 실행해도 중복되지 않는다).
#
# 기본 스케줄: 매일 04:20 KST. backup-mysql.sh(03:10)와 겹치지 않게 띄웠고,
# 아무도 머지하지 않는 시각이라 빌드와 겹칠 일이 거의 없다 — 겹쳐도 prune-images.sh
# 의 나이 필터가 진행 중인 빌드 산출물을 걸러낸다.
#
# install-backup-cron.sh 와 같은 방식이다.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEDULE="${PRUNE_CRON_SCHEDULE:-20 4 * * *}"
MARKER="# circular-fashion image prune"
LOGFILE="$HOME/logs/prune-images.log"

mkdir -p "$(dirname "$LOGFILE")"

NEW_LINE="$SCHEDULE cd $DEPLOY_DIR && /usr/bin/env bash scripts/prune-images.sh >> $LOGFILE 2>&1 $MARKER"

# 기존 마커 줄을 제거한 뒤 새로 넣는다
CURRENT="$(crontab -l 2>/dev/null | grep -v "$MARKER" || true)"
printf '%s\n%s\n' "$CURRENT" "$NEW_LINE" | sed '/^$/d' | crontab -

echo "등록 완료:"
crontab -l | grep "$MARKER"
echo
echo "로그: $LOGFILE"
