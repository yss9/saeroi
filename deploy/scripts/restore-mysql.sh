#!/usr/bin/env bash
# backup-mysql.sh 가 만든 덤프로 MySQL 을 복구한다.
#
# ⚠️ 대상 데이터베이스를 덤프 시점 상태로 되돌린다. 그 사이의 데이터는 사라진다.
#
# 사용법:
#   bash scripts/restore-mysql.sh ~/backups/mysql/circular_fashion-20260901-030000.sql.gz --yes
#   bash scripts/restore-mysql.sh <덤프> --dry-run   # 덤프 검증만 하고 끝
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="cf-mysql"
ENV_FILE="$DEPLOY_DIR/.env"

DUMP="${1:-}"
MODE="${2:-}"

[ -n "$DUMP" ] || { echo "사용법: restore-mysql.sh <덤프.sql.gz> [--yes|--dry-run]"; exit 1; }
[ -f "$DUMP" ] || { echo "FAIL: 덤프 파일 없음: $DUMP"; exit 1; }

echo "── 덤프 검증 ──"
gzip -t "$DUMP" || { echo "FAIL: gzip 무결성 검사 실패"; exit 1; }
gunzip -c "$DUMP" | tail -5 | grep -q "Dump completed" || { echo "FAIL: 'Dump completed' 마커 없음"; exit 1; }
echo "OK: $(du -h "$DUMP" | cut -f1), $(gunzip -c "$DUMP" | grep -c '^CREATE TABLE') 개 테이블"

[ "$MODE" = "--dry-run" ] && { echo "--dry-run 이므로 여기서 종료"; exit 0; }
[ "$MODE" = "--yes" ] || { echo; echo "실제로 복구하려면 --yes 를 붙이세요. 대상 DB 의 현재 데이터가 덮어씌워집니다."; exit 1; }

ROOT_PW="$(awk -F= '/^MYSQL_ROOT_PASSWORD=/{print $2}' "$ENV_FILE")"
[ -n "$ROOT_PW" ] || { echo "FAIL: MYSQL_ROOT_PASSWORD 가 비어 있음"; exit 1; }

# 복구 중 backend 가 쓰기를 계속하면 상태가 섞인다. 먼저 내린다.
echo "── backend 중지 ──"
. "$DEPLOY_DIR/scripts/compose.sh"
dc stop backend || true

echo "── 복구 실행 ──"
gunzip -c "$DUMP" | docker exec -i -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" mysql -uroot

echo "── backend 재기동 ──"
dc start backend
bash "$DEPLOY_DIR/scripts/health-check.sh" backend
echo "복구 완료."
