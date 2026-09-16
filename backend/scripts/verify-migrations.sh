#!/usr/bin/env bash
# 마이그레이션 파일이 schema.sql 이 선언한 목표 상태를 실제로 재현하는지 병합 전에 검증한다.
#
# 왜 필요한가
# ─────────────────────────────────────────────────────────────────────
# schema.sql 은 "목표 상태" 이고 db/migrations/*.sql 은 "그 목표로 가는 diff" 다.
# 이 둘은 사람이 손으로 같이 맞춰 쓰는 별개의 파일이라, 마이그레이션 작성자가 실수하면
# (컬럼 하나를 빠뜨리거나 ENUM 값 일부를 빠뜨리는 등) schema.sql 과 실제로 마이그레이션이
# 만들어내는 결과가 어긋난다. 이 어긋남은 이제까지 배포 시점에 Hibernate `validate` 가
# 깨지고 나서야(=크래시루프) 드러났다 (deploy/master:docs/SCHEMA_INCIDENTS.md 2026-09-16 참고,
# 그날 이 마이그레이션의 status ENUM 이 CANCELED 값을 빠뜨린 채 병합됐었다).
#
# 무엇을 하는가
# ─────────────────────────────────────────────────────────────────────
# 1) "베이스라인" 커밋(이 브랜치가 갈라져 나온 지점)의 schema.sql 을 구한다.
# 2) 베이스라인 이후 새로 추가된 db/migrations/*.sql 파일 목록을 구한다(수정된 기존
#    파일은 다루지 않는다 — 이 저장소의 마이그레이션 파일은 날짜가 박힌 채 추가만 되고
#    수정되지 않는 관례다).
# 3) 임시 MySQL 컨테이너에 DB 두 개를 만든다.
#      - expected : 지금(HEAD) schema.sql 을 그대로 적용한 것 — 항상 "의도한 정답"이다.
#      - actual   : 베이스라인 schema.sql 을 적용한 뒤, 새 마이그레이션 파일들을
#                    파일명 순서대로 적용한 것 — 실제 배포가 밟게 될 경로와 같다.
# 4) 두 DB 의 테이블·컬럼·타입을 information_schema 로 대조한다. 다르면 실패.
#    (NULL 허용/DEFAULT/인덱스는 보지 않는다 — deploy/master 의 check-schema.sh 와
#    같은 범위. Hibernate validate 가 실제로 보는 것만 좁게 잡는다는 원칙을 맞춘다.)
# 5) 마이그레이션 파일 추가 없이 schema.sql 만 바뀌었다면 그것도 실패로 본다 — 그 변경을
#    실제 DB 에 적용할 경로가 전혀 없다는 뜻이기 때문이다.
#
# 새 테이블만 만드는 마이그레이션은 베이스라인에 해당 테이블이 없으므로 당연히 스킵된다
# (그 테이블은 expected/actual 양쪽에 동일하게 존재하게 된다).
#
# 사용법: bash scripts/verify-migrations.sh
#   BASE_REF 환경변수로 베이스라인 브랜치를 강제할 수 있다 (기본은 아래 자동판단).
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

SCHEMA_PATH="src/main/resources/schema.sql"
MIGRATIONS_DIR="src/main/resources/db/migrations"
CONTAINER="verify-migrations-mysql-$$"
ROOT_PW="verify-$$-$(date +%s 2>/dev/null || echo static)"

log()  { printf '%s\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; }

cleanup() { docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; }
trap cleanup EXIT

# ── 1) 베이스라인 정하기 ─────────────────────────────────────────────
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo HEAD)"
if [ -n "${BASE_REF:-}" ]; then
    : # 사용자 지정
elif [ "$BRANCH" = "backend/develop" ]; then
    BASE_REF="origin/backend/master"
else
    BASE_REF="origin/backend/develop"
fi
# Jenkins 워크스페이스에서는 이 fetch 에 자격증명이 없다 — Jenkins 의 GitLab 인증은
# 자신의 체크아웃 스텝에만 한시적으로 걸리고, 이후 shell 스텝의 임의 git 명령까지
# 이어지지 않는다("fatal: could not read Username ..."). 대신 멀티브랜치 체크아웃이
# 이미 전체 브랜치 refspec(+refs/heads/*:refs/remotes/origin/*)을 이 워크스페이스에
# 받아와 있으므로, fetch 는 신선도를 높이는 최선 노력일 뿐이고 실패해도 로컬에 이미
# 있는 ref 로 계속 진행할 수 있다. 로컬에도 ref 가 없을 때만 진짜로 막는다.
if ! git fetch -q origin "${BASE_REF#origin/}" 2>/dev/null; then
    log "경고: origin fetch 실패(자격증명 없음 등) — 워크스페이스에 이미 있는 로컬 $BASE_REF 로 진행 시도."
fi
git rev-parse -q --verify "$BASE_REF" >/dev/null 2>&1 || {
    fail "$BASE_REF 를 fetch 도 못 하고 로컬에서도 찾지 못했다."
    exit 2
}

MERGE_BASE="$(git merge-base HEAD "$BASE_REF" 2>/dev/null)" || {
    fail "$BASE_REF 와의 merge-base 를 찾지 못했다"; exit 2
}
log "베이스라인 : $BASE_REF (merge-base $MERGE_BASE)"

BASELINE_SCHEMA="$(git show "$MERGE_BASE:$SCHEMA_PATH" 2>/dev/null)" || {
    fail "$MERGE_BASE:$SCHEMA_PATH 를 읽을 수 없다"; exit 2
}
HEAD_SCHEMA="$(cat "$SCHEMA_PATH")"

# ── 2) 새로 추가된 마이그레이션 파일 ──────────────────────────────────
BASELINE_FILES="$(git ls-tree -r "$MERGE_BASE" --name-only -- "$MIGRATIONS_DIR" 2>/dev/null || true)"
HEAD_FILES="$(git ls-tree -r HEAD --name-only -- "$MIGRATIONS_DIR" 2>/dev/null || true)"
NEW_FILES="$(comm -13 <(echo "$BASELINE_FILES" | sort) <(echo "$HEAD_FILES" | sort))"

if [ -z "$NEW_FILES" ]; then
    if [ "$BASELINE_SCHEMA" = "$HEAD_SCHEMA" ]; then
        log "OK: 새 마이그레이션 없음, schema.sql 도 변경 없음 — 검증할 것이 없다."
        exit 0
    else
        fail "schema.sql 이 바뀌었는데 새 마이그레이션 파일이 없다."
        fail "이 변경을 실제 DB(운영/test)에 적용할 경로가 없다는 뜻이다."
        fail "$MIGRATIONS_DIR 아래에 대응 마이그레이션을 추가할 것."
        exit 1
    fi
fi
log "새 마이그레이션:"
echo "$NEW_FILES" | sed 's/^/  /'

# ── 3) 임시 MySQL 기동 ────────────────────────────────────────────────
docker run -d --name "$CONTAINER" \
    -e MYSQL_ROOT_PASSWORD="$ROOT_PW" \
    -e MYSQL_DATABASE=expected \
    mysql:8.0 --default-authentication-plugin=mysql_native_password >/dev/null

log "MySQL 기동 대기..."
for i in $(seq 1 60); do
    if docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" mysql -uroot -e 'SELECT 1' >/dev/null 2>&1; then
        break
    fi
    [ "$i" -eq 60 ] && { fail "MySQL 이 60초 안에 뜨지 않았다"; exit 2; }
    sleep 1
done

docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" mysql -uroot -e 'CREATE DATABASE actual' >/dev/null

# ── 4) expected: 지금 schema.sql 그대로 ───────────────────────────────
echo "$HEAD_SCHEMA" | docker exec -i -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" mysql -uroot expected

# ── 5) actual: 베이스라인 schema.sql + 새 마이그레이션 순서대로 ────────
echo "$BASELINE_SCHEMA" | docker exec -i -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" mysql -uroot actual
while IFS= read -r mf; do
    [ -n "$mf" ] || continue
    log "적용 중: $mf"
    if ! git show "HEAD:$mf" | docker exec -i -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" mysql -uroot actual; then
        fail "$mf 적용 중 SQL 오류 — 위 mysql 에러 참고."
        exit 1
    fi
done <<< "$(echo "$NEW_FILES" | sort)"

# ── 6) 두 DB 대조 (테이블·컬럼·타입만 — NULL/DEFAULT/인덱스는 안 본다) ──
DIFF="$(docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" mysql -uroot -N -e "
SELECT CONCAT(
    COALESCE(e.TABLE_NAME, a.TABLE_NAME), '.', COALESCE(e.COLUMN_NAME, a.COLUMN_NAME),
    '  expected=[', COALESCE(e.COLUMN_TYPE, '(없음)'), ']',
    '  actual=[',   COALESCE(a.COLUMN_TYPE, '(없음)'), ']'
)
FROM (
    SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.columns WHERE TABLE_SCHEMA='expected'
) e
LEFT JOIN (
    SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.columns WHERE TABLE_SCHEMA='actual'
) a ON e.TABLE_NAME=a.TABLE_NAME AND e.COLUMN_NAME=a.COLUMN_NAME
WHERE a.COLUMN_NAME IS NULL OR e.COLUMN_TYPE <> a.COLUMN_TYPE
UNION ALL
SELECT CONCAT(a.TABLE_NAME, '.', a.COLUMN_NAME, '  expected=[(없음)]  actual=[', a.COLUMN_TYPE, ']')
FROM (
    SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.columns WHERE TABLE_SCHEMA='actual'
) a
LEFT JOIN (
    SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.columns WHERE TABLE_SCHEMA='expected'
) e ON a.TABLE_NAME=e.TABLE_NAME AND a.COLUMN_NAME=e.COLUMN_NAME
WHERE e.COLUMN_NAME IS NULL
")"

if [ -n "$DIFF" ]; then
    fail "새 마이그레이션을 적용한 결과가 schema.sql 의 목표 상태와 다르다:"
    echo "$DIFF" | sed 's/^/  /' >&2
    fail "마이그레이션 파일(또는 schema.sql)을 고쳐서 둘이 정확히 일치하게 만들 것."
    exit 1
fi

log "OK: 새 마이그레이션을 적용한 결과가 schema.sql 목표 상태와 정확히 일치한다."
