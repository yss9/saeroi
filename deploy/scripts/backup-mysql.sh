#!/usr/bin/env bash
# MySQL 논리 백업 (mysqldump). cron 이 매일 호출한다.
#
# 왜 필요한가: docker volume 은 "컨테이너 삭제"로부터만 데이터를 지켜준다.
# EBS 손상, 인스턴스 종료, 실수로 인한 DROP/DELETE 에는 아무 대응이 되지 않는다.
# 이 스크립트가 만드는 덤프가 그 경우의 유일한 복구 수단이다.
#
# 사용법:
#   bash scripts/backup-mysql.sh              # 백업 1회 실행
#   MYSQL_BACKUP_RETENTION_DAYS=30 bash ...   # 보관 기간 변경
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${MYSQL_BACKUP_DIR:-$HOME/backups/mysql}"
RETENTION_DAYS="${MYSQL_BACKUP_RETENTION_DAYS:-14}"
CONTAINER="cf-mysql"
ENV_FILE="$DEPLOY_DIR/.env"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

[ -f "$ENV_FILE" ] || { log "FAIL: $ENV_FILE 없음"; exit 1; }

# .env 를 통째로 source 하지 않는다 — 필요한 두 값만 뽑는다.
DB_NAME="$(awk -F= '/^MYSQL_DATABASE=/{print $2}' "$ENV_FILE")"
ROOT_PW="$(awk -F= '/^MYSQL_ROOT_PASSWORD=/{print $2}' "$ENV_FILE")"
[ -n "$DB_NAME" ] && [ -n "$ROOT_PW" ] || { log "FAIL: MYSQL_DATABASE / MYSQL_ROOT_PASSWORD 가 비어 있음"; exit 1; }

docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true \
    || { log "FAIL: $CONTAINER 가 실행 중이 아님"; exit 1; }

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

STAMP="$(date '+%Y%m%d-%H%M%S')"
TARGET="$BACKUP_DIR/${DB_NAME}-${STAMP}.sql.gz"
TMP="${TARGET}.part"

log "백업 시작 → $TARGET"

# 비밀번호는 MYSQL_PWD 로 넘긴다. -p<암호> 는 컨테이너 프로세스 목록(ps)에 그대로 노출된다.
# --single-transaction: InnoDB 를 락 없이 일관된 스냅샷으로 뜬다 (서비스 중단 없음).
if ! docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
        mysqldump -uroot \
            --single-transaction --quick \
            --routines --triggers --events \
            --default-character-set=utf8mb4 \
            --databases "$DB_NAME" 2>"$TMP.err" | gzip -c > "$TMP"; then
    log "FAIL: mysqldump 실패 — $(tail -3 "$TMP.err" 2>/dev/null)"
    rm -f "$TMP" "$TMP.err"
    exit 1
fi

# 검증: 덤프가 잘려 있으면 백업이 아니라 착각이다.
#   1) gzip 무결성  2) 최소 크기  3) mysqldump 가 끝까지 갔다는 마커
if ! gzip -t "$TMP" 2>/dev/null; then
    log "FAIL: gzip 무결성 검사 실패"; rm -f "$TMP" "$TMP.err"; exit 1
fi
if [ "$(stat -c %s "$TMP")" -lt 1024 ]; then
    log "FAIL: 덤프가 비정상적으로 작음 ($(stat -c %s "$TMP") bytes)"; rm -f "$TMP" "$TMP.err"; exit 1
fi
if ! gunzip -c "$TMP" | tail -5 | grep -q "Dump completed"; then
    log "FAIL: 'Dump completed' 마커 없음 — 덤프가 중간에 끊겼다"; rm -f "$TMP" "$TMP.err"; exit 1
fi

rm -f "$TMP.err"
mv "$TMP" "$TARGET"
chmod 600 "$TARGET"
log "백업 완료 ($(du -h "$TARGET" | cut -f1))"

# 보관 기간 지난 백업 정리
DELETED="$(find "$BACKUP_DIR" -maxdepth 1 -name "${DB_NAME}-*.sql.gz" -mtime "+$RETENTION_DAYS" -print -delete | wc -l)"
[ "$DELETED" -gt 0 ] && log "오래된 백업 $DELETED 개 삭제 (보관 ${RETENTION_DAYS}일)"

log "현재 보관 중: $(find "$BACKUP_DIR" -maxdepth 1 -name "${DB_NAME}-*.sql.gz" | wc -l) 개, 총 $(du -sh "$BACKUP_DIR" | cut -f1)"
