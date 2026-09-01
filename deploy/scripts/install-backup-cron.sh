#!/usr/bin/env bash
# backup-mysql.sh 를 ubuntu 사용자 crontab 에 등록한다 (멱등 — 여러 번 실행해도 중복되지 않는다).
# 기본 스케줄: 매일 03:10 KST.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEDULE="${BACKUP_CRON_SCHEDULE:-10 3 * * *}"
MARKER="# circular-fashion mysql backup"
LOGFILE="$HOME/backups/mysql/backup.log"

mkdir -p "$(dirname "$LOGFILE")"

NEW_LINE="$SCHEDULE cd $DEPLOY_DIR && /usr/bin/env bash scripts/backup-mysql.sh >> $LOGFILE 2>&1 $MARKER"

# 기존 마커 줄을 제거한 뒤 새로 넣는다
CURRENT="$(crontab -l 2>/dev/null | grep -v "$MARKER" || true)"
printf '%s\n%s\n' "$CURRENT" "$NEW_LINE" | sed '/^$/d' | crontab -

echo "등록 완료:"
crontab -l | grep "$MARKER"
echo
echo "로그: $LOGFILE"
