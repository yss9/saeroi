#!/usr/bin/env bash
# 배포 전 스키마 검증 (preflight).
#
# 왜 필요한가
# ─────────────────────────────────────────────────────────────────────
# backend 의 `application.yml` 은 `ddl-auto: validate` 이고 `spring.sql.init` 도 없다.
# 즉 **스키마를 자동으로 만들거나 고쳐주지 않는다.** 엔티티가 기대하는 컬럼이 DB 에
# 없으면 Hibernate 가 기동 단계에서 검증에 실패하고 컨테이너가 그대로 죽는다.
#
# 2026-09-07 실제로 이 상황이 있었다 — backend/develop 에 37 커밋이 쌓여 있는데
# EC2 의 DB 는 그보다 오래된 스키마였다. 그대로 배포하면 앱이 뜨지 않는다.
# 게다가 헬스체크가 실패해도 "왜" 는 컨테이너 로그를 봐야만 알 수 있다.
#
# 그래서 **배포 전에** 라이브 DB 와 backend 의 schema.sql 을 대조한다.
#
# 사용법
# ─────────────────────────────────────────────────────────────────────
#   bash scripts/check-schema.sh                    # backend/develop 기준으로 대조
#   SCHEMA_REF=origin/backend/master bash ...       # 다른 기준으로 대조
#   bash scripts/check-schema.sh --ddl              # 부족한 것의 DDL 을 출력만 한다
#
# 이 스크립트는 **읽기만 한다.** ALTER 나 CREATE 를 실행하지 않는다 —
# 무엇을 적용할지는 사람이 보고 결정해야 한다.
#
# 종료 코드: 0 = 일치, 1 = 부족한 테이블/컬럼 있음, 2 = 실행 조건 미비
set -uo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"

SCHEMA_REF="${SCHEMA_REF:-origin/backend/develop}"
SCHEMA_PATH="src/main/resources/schema.sql"
CONTAINER="${MYSQL_CONTAINER:-cf-mysql}"
ENV_FILE="$DEPLOY_DIR/.env"
EMIT_DDL=0
[ "${1:-}" = "--ddl" ] && EMIT_DDL=1

log() { printf '%s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; }

# ── 실행 조건 ────────────────────────────────────────────────────────
[ -f "$ENV_FILE" ] || { fail ".env 가 없다: $ENV_FILE"; exit 2; }
# 첫 '=' 뒤 전체를 값으로 본다. awk -F= '{print $2}' 는 값 안의 '=' 에서 잘리는데,
# base64 로 만든 비밀번호는 보통 '=' 로 끝난다 (openssl rand -base64). 그러면 잘린
# 비밀번호로 접속해 인증에 실패하고, 원인은 "DB 접속 실패" 로만 보인다.
env_value() { sed -n "s/^$1=//p" "$ENV_FILE" | head -1; }
DB_NAME="$(env_value MYSQL_DATABASE)"
ROOT_PW="$(env_value MYSQL_ROOT_PASSWORD)"
[ -n "$DB_NAME" ] && [ -n "$ROOT_PW" ] || { fail "MYSQL_DATABASE / MYSQL_ROOT_PASSWORD 가 비어 있음"; exit 2; }
docker ps --format '{{.Names}}' | grep -qx "$CONTAINER" || { fail "$CONTAINER 컨테이너가 실행 중이 아니다"; exit 2; }

# backend 브랜치의 schema.sql 을 가져온다. deploy 체크아웃에는 그 파일이 없으므로 fetch 가 필요하다.
git fetch -q origin "${SCHEMA_REF#origin/}" 2>/dev/null || true
SCHEMA_SQL="$(git show "$SCHEMA_REF:$SCHEMA_PATH" 2>/dev/null)" || {
    fail "$SCHEMA_REF:$SCHEMA_PATH 를 읽을 수 없다. 'git fetch origin' 후 다시 시도할 것"
    exit 2
}

# 비밀번호는 MYSQL_PWD 로 넘긴다. -p<암호> 는 컨테이너 프로세스 목록에 그대로 노출된다.
#
# stderr 를 버리지 않는다. 처음에는 2>/dev/null 로 삼켰는데, 인증 실패든 DB 부재든
# 전부 "빈 출력" 이 되어 원인을 알 수 없었다. 에러는 파일에 받아 두고 실패 시 보여준다.
MYSQL_ERR="$(mktemp)"
trap 'rm -f "$MYSQL_ERR"' EXIT
mysql_q() {
    docker exec -e MYSQL_PWD="$ROOT_PW" -i "$CONTAINER" \
        mysql -uroot -N -B -D "$DB_NAME" -e "$1" 2>"$MYSQL_ERR"
}

# ── 기대 스키마 추출 ────────────────────────────────────────────────
# schema.sql 을 파싱한다. 정규식으로 SQL 을 다루는 것은 원래 위험하지만, 이 파일은
# 사람이 일정한 형식으로 유지하는 단일 파일이라 실용적인 선택이다.
# 형식이 깨지면 "기대 컬럼 0개" 로 드러나므로 조용히 틀리지는 않는다.
expected_tables() {
    printf '%s\n' "$SCHEMA_SQL" | grep -oE '^CREATE TABLE [a-z_]+' | awk '{print $3}'
}

# 한 테이블의 컬럼 이름만 뽑는다. 제약·인덱스 줄(CONSTRAINT/INDEX/PRIMARY/UNIQUE/KEY/CHECK)은 건너뛴다.
expected_columns() {
    printf '%s\n' "$SCHEMA_SQL" \
        | awk -v t="$1" '
            $0 ~ "^CREATE TABLE "t" \\(" { inside=1; next }
            inside && /^\) ENGINE=/ { inside=0 }
            inside {
                line=$0
                sub(/^[ \t]+/, "", line)
                if (line ~ /^(CONSTRAINT|INDEX|PRIMARY|UNIQUE|KEY|CHECK|FOREIGN)/) next
                if (line ~ /^[a-z_]+[ \t]/) { split(line, a, /[ \t]/); print a[1] }
            }'
}

# schema.sql 에서 한 테이블의 CREATE 문 전체를 그대로 뽑는다 (--ddl 출력용)
create_statement() {
    printf '%s\n' "$SCHEMA_SQL" \
        | awk -v t="$1" '
            $0 ~ "^CREATE TABLE "t" \\(" { inside=1 }
            inside { print }
            inside && /^\) ENGINE=/ { exit }'
}

# ── 대조 ────────────────────────────────────────────────────────────
# 종료 코드로 "접속 실패" 와 "테이블 0개" 를 구분한다. 빈 출력만 보고 판단하면
# 테이블이 하나도 없는 새 DB 를 접속 실패로 오진한다 — 실제로 그렇게 오진했다.
LIVE_TABLES="$(mysql_q 'SHOW TABLES;')"
MYSQL_RC=$?
if [ $MYSQL_RC -ne 0 ]; then
    fail "MySQL 질의 실패 (exit $MYSQL_RC). mysql 이 돌려준 원문:"
    sed 's/^/  /' "$MYSQL_ERR" >&2
    {
        printf '\n힌트\n'
        printf '  - Access denied    → .env 의 MYSQL_ROOT_PASSWORD 확인\n'
        printf '  - Unknown database → .env 의 MYSQL_DATABASE 확인 (현재: %s)\n' "$DB_NAME"
    } >&2
    exit 2
fi
if [ -z "$LIVE_TABLES" ]; then
    log "라이브 테이블: 0 개 — DB 가 비어 있다 (접속은 성공했다)."
    log ""
    log "지금 배포된 backend 는 엔티티가 없던 초기 버전이라 validate 가 통과했을 수 있다."
    log "현재 $SCHEMA_REF 는 엔티티가 15개이므로 schema.sql 전체를 적용해야 한다."
    log ""
    log "적용 순서:"
    log "  1) bash scripts/backup-mysql.sh                       # 먼저 백업"
    log "  2) git show $SCHEMA_REF:$SCHEMA_PATH \\"
    log "       | docker exec -i -e MYSQL_PWD=\"\$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' .env)\" \\"
    log "         $CONTAINER mysql -uroot $DB_NAME"
    log "  3) bash scripts/check-schema.sh                       # OK 확인"
    exit 1
fi

MISSING_TABLES=()
MISSING_COLUMNS=()

for table in $(expected_tables); do
    if ! printf '%s\n' "$LIVE_TABLES" | grep -qx "$table"; then
        MISSING_TABLES+=("$table")
        continue
    fi
    live_cols="$(mysql_q "SHOW COLUMNS FROM \`$table\`;" | awk '{print $1}')"
    for col in $(expected_columns "$table"); do
        printf '%s\n' "$live_cols" | grep -qx "$col" || MISSING_COLUMNS+=("$table.$col")
    done
done

# ── 보고 ────────────────────────────────────────────────────────────
log "기준       : $SCHEMA_REF"
log "대상 DB    : $DB_NAME ($CONTAINER)"
log "기대 테이블 : $(expected_tables | wc -l) 개"
log "라이브 테이블: $(printf '%s\n' "$LIVE_TABLES" | wc -l) 개"
log ""

if [ ${#MISSING_TABLES[@]} -eq 0 ] && [ ${#MISSING_COLUMNS[@]} -eq 0 ]; then
    log "OK: 부족한 테이블·컬럼 없음. 배포해도 validate 가 통과한다."
    exit 0
fi

if [ ${#MISSING_TABLES[@]} -gt 0 ]; then
    log "없는 테이블 ${#MISSING_TABLES[@]} 개:"
    printf '  %s\n' "${MISSING_TABLES[@]}"
    log ""
fi
if [ ${#MISSING_COLUMNS[@]} -gt 0 ]; then
    log "없는 컬럼 ${#MISSING_COLUMNS[@]} 개:"
    printf '  %s\n' "${MISSING_COLUMNS[@]}"
    log ""
fi

if [ $EMIT_DDL -eq 1 ]; then
    log "── 적용할 DDL (검토 후 직접 실행할 것) ─────────────────────────"
    log ""
    for table in "${MISSING_TABLES[@]:-}"; do
        [ -n "$table" ] || continue
        create_statement "$table"
        log ""
    done
    if [ ${#MISSING_COLUMNS[@]} -gt 0 ]; then
        log "-- 없는 컬럼은 자동 생성하지 않는다. 아래 항목의 정의를 schema.sql 에서"
        log "-- 찾아 ALTER TABLE ... ADD COLUMN 을 직접 작성할 것."
        log "-- 컬럼 위치(AFTER)·기본값·생성 컬럼(GENERATED) 여부에 따라 문장이 달라지고,"
        log "-- 기존 행이 있으면 NOT NULL 컬럼은 DEFAULT 없이 추가할 수 없다."
        printf '  -- %s\n' "${MISSING_COLUMNS[@]}"
    fi
else
    log "DDL 을 보려면: bash scripts/check-schema.sh --ddl"
fi

log ""
log "⚠️  이 상태로 backend 를 배포하면 Hibernate validate 가 실패해 컨테이너가 죽는다."
log "    DDL 을 먼저 적용하고 이 스크립트를 다시 돌려 OK 를 확인한 뒤 배포할 것."
exit 1
