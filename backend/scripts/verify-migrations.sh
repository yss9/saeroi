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
# 워킹트리가 아니라 HEAD 에서 읽는다.
#
# 이유가 둘이다.
#
# 1) 이 게이트가 보는 것은 커밋된 상태다. 마이그레이션 파일은 이미 HEAD 에서 읽고 있고
#    (git ls-tree HEAD, git show HEAD:$mf), schema.sql 만 워킹트리를 보면 한 대조 안에
#    커밋된 것과 커밋 안 된 것이 섞인다. 커밋 전에 돌리면 오히려 거짓 경보가 난다 —
#    schema.sql 과 마이그레이션을 함께 써 두고 아직 커밋하지 않은 상태에서, 마이그레이션은
#    HEAD 에 없으니 "새 마이그레이션 없음" 이 되고 schema.sql 만 바뀐 것으로 보인다.
#
# 2) cat 은 체크아웃된 줄바꿈을 그대로 읽는다. core.autocrlf 가 켜진 Windows 워킹트리는
#    CRLF 라, 내용이 한 글자도 다르지 않아도 git show 가 주는 LF 본문과 문자열이 달라져
#    "schema.sql 이 바뀌었는데 마이그레이션이 없다" 로 항상 실패했다. 그래서 Windows 에서는
#    이 스크립트를 로컬로 돌려 볼 수 없었다 — 푸시 전에 잡을 수 있던 고장을 CI 에서야
#    발견하게 만든 원인이다.
#
# Jenkins 는 리눅스 체크아웃이라 두 값이 원래 같다. CI 동작은 바뀌지 않는다.
HEAD_SCHEMA="$(git show "HEAD:$SCHEMA_PATH" 2>/dev/null)" || {
    fail "HEAD:$SCHEMA_PATH 를 읽을 수 없다"; exit 2
}

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

# 모든 mysql 호출을 TCP 로 한다. 이유는 바로 아래 대기 루프 주석에 있다.
#
# -h localhost 는 유닉스에서 여전히 소켓으로 붙으므로 127.0.0.1 과 --protocol=TCP 를
# 둘 다 적는다. 포트를 publish 하지 않아도 된다 — docker exec 이라 컨테이너가 자기
# 루프백으로 붙는 것이고, 그 접속은 역방향 해석으로 root@localhost 에 매칭된다
# (공식 이미지는 root@'%' 도 함께 만든다).
mysql_exec() {
    docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
        mysql --protocol=TCP -h 127.0.0.1 -uroot "$@"
}

# 표준입력으로 SQL 을 흘려 넣는 경로. docker exec 에 -i 가 필요해 위와 나눈다.
mysql_pipe() {
    docker exec -i -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
        mysql --protocol=TCP -h 127.0.0.1 -uroot "$@"
}

dump_container_state() {
    fail "컨테이너 상태: $(docker inspect \
        -f '{{.State.Status}} (exit {{.State.ExitCode}})' "$CONTAINER" 2>/dev/null \
        || echo '조회 실패')"
    fail "컨테이너 로그 마지막 30줄:"
    docker logs --tail 30 "$CONTAINER" 2>&1 | sed 's/^/  /' >&2
}

# ── 기동 대기: 소켓이 아니라 TCP 로 묻는다 ────────────────────────────
#
# mysql:8.0 이미지는 초기화를 이렇게 한다.
#
#   1) docker_temp_server_start : mysqld --skip-networking --socket=... 로 임시 서버
#   2) docker_setup_db          : root 계정·비밀번호·MYSQL_DATABASE 생성
#   3) docker_temp_server_stop  : 임시 서버 종료
#   4) exec mysqld              : 진짜 서버 기동 (여기서 처음 TCP 를 연다)
#
# 예전에는 이 루프가 -h 없이, 즉 유닉스 소켓으로 SELECT 1 을 던졌다. 그러면 2) 시점부터
# 임시 서버가 응답해 루프가 일찍 빠져나오고, 3) 이 서버를 내리는 사이에 다음 명령이
# 들어가 "ERROR 2002 ... mysqld.sock (2)" 로 죽는다. 초기화 시퀀스의 타이밍이 일정해서
# 이 경합은 거의 항상 같은 자리에서 터졌다.
#
# 임시 서버는 --skip-networking 이라 TCP 를 열지 않는다. 그래서 TCP 로 물으면 4) 의
# 진짜 서버가 뜰 때까지 루프가 기다린다.
#
# 통과한 뒤의 명령에 재시도를 넣지 않는다. 진짜 서버는 컨테이너의 PID 1 이라 살아 있는
# 한 재시작하지 않으므로, 그 뒤의 연결 실패는 일시적인 것이 아니라 컨테이너가 죽었다는
# 뜻이다 — 재시도로 덮으면 진짜 고장을 늦게 알게 된다.
log "MySQL 기동 대기 (TCP)..."
WAIT_SECONDS=120
LAST_ERROR=""
for i in $(seq 1 "$WAIT_SECONDS"); do
    # 컨테이너가 죽었으면 더 기다릴 이유가 없다. 이 확인이 없으면 설정 오류로 즉시
    # 종료한 컨테이너를 상대로 2분을 버리고, 그 뒤에도 이유를 알 수 없다.
    if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" != "true" ]; then
        fail "MySQL 컨테이너가 기동 중에 종료됐다."
        dump_container_state
        exit 2
    fi
    # stderr 만 담는다(2>&1 >/dev/null 순서가 중요하다). 전부 버리면 타임아웃 때
    # 무엇이 막았는지 로그에 아무것도 남지 않는다 — 이 스크립트가 한 번 그렇게 실패했다.
    if LAST_ERROR="$(mysql_exec -e 'SELECT 1' 2>&1 >/dev/null)"; then
        break
    fi
    if [ "$i" -eq "$WAIT_SECONDS" ]; then
        fail "MySQL 이 ${WAIT_SECONDS}초 안에 TCP 로 응답하지 않았다."
        fail "마지막 오류: ${LAST_ERROR:-(없음)}"
        dump_container_state
        exit 2
    fi
    sleep 1
done

mysql_exec -e 'CREATE DATABASE actual' >/dev/null

# ── 4) expected: 지금 schema.sql 그대로 ───────────────────────────────
echo "$HEAD_SCHEMA" | mysql_pipe expected

# ── 5) actual: 베이스라인 schema.sql + 새 마이그레이션 순서대로 ────────
echo "$BASELINE_SCHEMA" | mysql_pipe actual
while IFS= read -r mf; do
    [ -n "$mf" ] || continue
    log "적용 중: $mf"
    if ! git show "HEAD:$mf" | mysql_pipe actual; then
        fail "$mf 적용 중 SQL 오류 — 위 mysql 에러 참고."
        exit 1
    fi
done <<< "$(echo "$NEW_FILES" | sort)"

# ── 6) 두 DB 대조 (테이블·컬럼·타입만 — NULL/DEFAULT/인덱스는 안 본다) ──
DIFF="$(mysql_exec -N -e "
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
