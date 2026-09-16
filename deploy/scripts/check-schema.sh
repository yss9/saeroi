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
# 왜 컬럼 타입까지 보는가 (2026-09-08 실사고)
# ─────────────────────────────────────────────────────────────────────
# 처음 만들 때는 **없는 테이블·컬럼**만 찾았다. 컬럼 이름만 맞으면 OK 를 냈다.
# 그런데 `blockchain_transaction.tx_hash` 는 schema.sql·운영 DB 모두 CHAR(66) 인데
# 엔티티가 `@Column(length = 66)` 만 선언해 Hibernate 는 VARCHAR(66) 을 기대했다.
# `ddl-auto: validate` 가 기동 단계에서 걸러 컨테이너가 죽고 backend/master #10 이 롤백됐다.
#
# 그 배포 **직전에 이 스크립트를 돌렸고 "OK" 가 나왔다.** 컬럼 이름은 다 있었으니까.
# backend 테스트 629건도 전부 초록이었다 — 테스트 프로파일은 `create-drop` 이라
# 엔티티가 시키는 대로 테이블을 만들기 때문에 타입 불일치가 원리적으로 드러나지 않는다.
#
# 즉 preflight 가 목적("배포 전에 validate 실패를 미리 잡는다")을 절반만 하고 있었다.
# 그래서 이제 **컬럼 타입도 대조한다.**
#
# 무엇을 비교하고 무엇을 일부러 비교하지 않는가
# ─────────────────────────────────────────────────────────────────────
# 비교한다   : 없는 테이블 / 없는 컬럼 / 컬럼 **타입**
# 비교 안 한다: NULL 허용 여부, DEFAULT, AUTO_INCREMENT, 인덱스·제약, 컬럼 순서,
#              CHARACTER SET / COLLATE, 라이브에만 있는 여분 컬럼
#
# NULL·DEFAULT 를 뺀 것은 의도적이다. Hibernate `validate` 는 기본적으로
# **테이블·컬럼의 존재와 타입**만 확인하고 nullable/default 는 보지 않는다.
# 그런데 이 두 항목은 표기 방식이 워낙 다양해서(`NULL` 생략 = nullable,
# `DEFAULT CURRENT_TIMESTAMP` vs `CURRENT_TIMESTAMP`, BOOLEAN `TRUE` vs `1`)
# 넣는 순간 거짓 양성이 쏟아진다. **거짓 양성이 나오면 사람이 이 스크립트를
# 무시하게 되고, 그러면 진짜 사고를 또 놓친다** — 그게 제일 큰 위험이다.
# 그래서 "validate 를 실제로 실패시키는 것"만 좁게 본다.
#
# 사용법
# ─────────────────────────────────────────────────────────────────────
#   bash scripts/check-schema.sh                    # backend/develop 기준으로 대조
#   SCHEMA_REF=origin/backend/master bash ...       # 다른 기준으로 대조
#   SCHEMA_FILE=/tmp/schema.sql bash ...            # git ref 대신 로컬 파일을 기준으로
#   bash scripts/check-schema.sh --ddl              # 부족한 것의 DDL 을 출력만 한다
#   MYSQL_CONTAINER=cft-mysql MYSQL_ENV_FILE=.env.test bash ...   # test 스택 대조
#   SAFE_MISSING_TABLES_FILE=/tmp/t.txt bash ...    # 없는 테이블만 있고 컬럼/타입
#                                                    # 불일치가 전혀 없을 때만, 그
#                                                    # 테이블 이름들을 이 파일에 씀
#                                                    # (자동 적용 판단용 — 사람이
#                                                    # 보라는 원칙은 그대로 유지)
#
# 이 스크립트는 **읽기만 한다.** ALTER 나 CREATE 를 실행하지 않는다 —
# 무엇을 적용할지는 사람이 보고 결정해야 한다.
#
# 종료 코드: 0 = 일치, 1 = 부족한 테이블/컬럼 또는 타입 불일치 있음, 2 = 실행 조건 미비
set -uo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"

SCHEMA_REF="${SCHEMA_REF:-origin/backend/develop}"
SCHEMA_PATH="src/main/resources/schema.sql"
CONTAINER="${MYSQL_CONTAINER:-cf-mysql}"
ENV_FILE="$DEPLOY_DIR/${MYSQL_ENV_FILE:-.env}"
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
#
# SCHEMA_FILE 은 "기준을 일부러 틀리게 만들어 이 스크립트가 잡는지" 를 시험할 때 쓴다.
# git ref 만 받으면 시험용 커밋을 원격에 밀어야 해서 검증 자체가 부담이 된다.
if [ -n "${SCHEMA_FILE:-}" ]; then
    [ -f "$SCHEMA_FILE" ] || { fail "SCHEMA_FILE 이 없다: $SCHEMA_FILE"; exit 2; }
    SCHEMA_SQL="$(cat "$SCHEMA_FILE")"
    SCHEMA_ORIGIN="$SCHEMA_FILE (SCHEMA_FILE)"
else
    # 2026-09-16 실사고: 이 fetch 가 실패해도 `|| true` 로 삼켜지면, 이후 git show 는
    # 로컬에 남아 있던 "예전" origin/backend/develop 커밋을 그대로 기준으로 삼는다.
    # 그 커밋이 아직 새 컬럼/ENUM 을 요구하지 않던 시점이면 이 스크립트가 "OK" 를
    # 내면서도 실제로는 방금 머지된 최신 커밋(Jenkins 가 실제로 빌드한 커밋)과 다른
    # 기준으로 검증한 것이 된다 — false OK. fetch 실패는 반드시 여기서 막는다.
    #
    # 2026-09-16 두 번째 실사고: "네트워크 blip" 으로 보고 재시도 로직만 넣었었는데,
    # 실은 Jenkins(cf-jenkins 컨테이너)에서 이 스크립트를 돌리면 100% 재현되는
    # 문제였다 — docker-compose.yml 이 /home/ubuntu/deploy 를 컨테이너에 **읽기전용
    # (:ro)** 으로 마운트해 두는데(Jenkins가 배포 설정을 못 건드리게 하려는 의도적
    # 경계), 이 저장소 자체의 .git 에 fetch 하면 .git/FETCH_HEAD 에 쓰기가 필요해
    # "Read-only file system" 으로 즉시 실패한다. 호스트에서 직접 돌리면 같은 명령이
    # 매번 성공해서 "역시 blip 이었나" 로 오판하기 쉬웠다.
    #
    # 그래서 이 저장소의 .git 을 건드리지 않는다. /tmp(양쪽 다 쓰기 가능)에 별도의
    # bare 저장소를 만들어 거기로만 fetch 한다 — :ro 마운트는 그대로 두고(그 경계가
    # 지켜야 할 안전장치다), 이 스크립트만 우회한다.
    ORIGIN_URL="$(git config --get remote.origin.url)"
    TMP_GIT="$(mktemp -d)"
    git init -q --bare "$TMP_GIT"
    FETCH_OK=0
    for attempt in 1 2 3; do
        if git --git-dir="$TMP_GIT" fetch -q "$ORIGIN_URL" "${SCHEMA_REF#origin/}" 2>/dev/null; then
            FETCH_OK=1
            break
        fi
        [ "$attempt" -lt 3 ] && sleep 2
    done
    if [ "$FETCH_OK" -ne 1 ]; then
        rm -rf "$TMP_GIT"
        fail "origin 에서 ${SCHEMA_REF#origin/} 를 3회 재시도해도 fetch 하지 못했다 — 네트워크/GitLab 확인 후 재시도할 것."
        fail "(fetch 실패를 무시하고 진행하면 오래된 로컬 캐시 기준으로 잘못된 OK 를 낼 수 있다 — 2026-09-16 실사고)"
        exit 2
    fi
    SCHEMA_SQL="$(git --git-dir="$TMP_GIT" show "FETCH_HEAD:$SCHEMA_PATH" 2>/dev/null)" || {
        rm -rf "$TMP_GIT"
        fail "$SCHEMA_REF:$SCHEMA_PATH 를 읽을 수 없다. 'git fetch origin' 후 다시 시도할 것"
        exit 2
    }
    SCHEMA_ORIGIN="$SCHEMA_REF"
    log "기준 커밋   : $(git --git-dir="$TMP_GIT" rev-parse FETCH_HEAD 2>/dev/null)"
    rm -rf "$TMP_GIT"
fi

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

# ── 타입 정규화 (양쪽에 똑같이 적용) ────────────────────────────────
# **기준 쪽과 라이브 쪽에 반드시 같은 함수를 쓴다.** 한쪽만 정규화하면 정규화 규칙
# 자체가 불일치로 보고돼 거짓 양성이 된다. 그래서 awk 함수 하나를 문자열로 두고
# 두 awk 프로그램 앞에 똑같이 붙인다.
#
# 실제 운영 DB(MySQL 8.0.46) 출력을 보고 맞춘 규칙:
#   - 정수 표시 폭    : MySQL 8.0.19+ 는 표시 폭을 버린다. INT(11) 선언도 라이브는 `int`
#                       → `int(11)` `bigint(20)` 의 폭을 떼어낸다
#   - tinyint(1) 예외 : BOOLEAN 은 `tinyint(1)` 로 저장된다. 이건 폭을 남겨야 하고,
#                       대신 선언 쪽 BOOLEAN/BOOL 을 `tinyint(1)` 로 바꿔 준다
#   - 동의어          : INTEGER→int, NUMERIC→decimal, DEC(→decimal(
#   - 대소문자        : 전부 소문자 (CHAR(66) vs char(66))
# 괄호 인자 안의 공백(`DECIMAL(5, 2)`, 여러 줄 ENUM)은 파싱 단계에서 이미 제거된다.
CANON_AWK='
function canon(ty) {
    ty = tolower(ty)
    if (ty == "boolean" || ty == "bool") ty = "tinyint(1)"
    sub(/^integer/, "int", ty)
    sub(/^numeric/, "decimal", ty)
    sub(/^dec\(/, "decimal(", ty)
    if (ty ~ /^(int|bigint|smallint|mediumint)\(/) sub(/\([0-9]+\)/, "", ty)
    if (ty ~ /^tinyint\(/ && ty !~ /^tinyint\(1\)/) sub(/\([0-9]+\)/, "", ty)
    gsub(/[ \t]+/, " ", ty)
    gsub(/^ | $/, "", ty)
    return ty
}
'

# 한 테이블의 `컬럼이름<TAB>정규화된타입` 을 뽑는다.
#
# 왜 줄 단위가 아니라 문자 단위로 파싱하는가
# ────────────────────────────────────────────────────────────────────
# 예전에는 "들여쓰기된 줄의 첫 단어 = 컬럼" 으로 봤다. 컬럼 **이름**만 필요할 때는
# 그럭저럭 맞았지만 타입까지 보려면 깨진다. schema.sql 에는 실제로 이런 게 있다:
#
#   1) 여러 줄에 걸친 ENUM — evidence_file.reference_type, processor_capability.process_type
#        process_type ENUM(
#            'CLEANING',
#            ...
#        ) NOT NULL,
#      줄 단위로 보면 타입이 "ENUM(" 에서 끊기고, 'CLEANING', 같은 줄이 컬럼으로 보인다.
#   2) 여러 줄에 걸친 CONSTRAINT ... UNIQUE ( ... ) — 안쪽 줄이 컬럼처럼 생겼다.
#   3) 백틱 컬럼 — consumer_address.`alias` (예전 정규식은 이걸 아예 놓쳤다)
#   4) 테이블 본문 중간의 `--` 주석 — material.root_origin 위쪽
#
# 그래서 CREATE TABLE 본문을 한 줄로 이어 붙인 뒤 **괄호 깊이 0 의 콤마**로 쪼갠다.
# 괄호와 따옴표를 세면서 쪼개므로 위 4가지가 모두 자연히 처리된다.
#
# 타입은 `타입이름 [ ( ... ) ] { unsigned | signed | zerofill }` 까지만 떼어낸다.
# NOT NULL / DEFAULT / AUTO_INCREMENT / GENERATED ALWAYS AS / COMMENT 는 거기서 끊긴다.
# CHARACTER SET·COLLATE 는 읽고 버린다 — 라이브의 COLUMN_TYPE 에도 안 나오므로
# 남겨 두면 그것만으로 불일치가 되어 버린다 (거짓 양성).
schema_column_defs() {
    printf '%s\n' "$SCHEMA_SQL" \
        | awk -v t="$1" "$CANON_AWK"'
            # item = 괄호 깊이 0 의 콤마로 쪼갠 한 조각. 컬럼이면 이름<TAB>타입을 출력한다.
            function emit(item,  i, c, n, name, ty, inq, depth, w, rest) {
                gsub(/^[ \t]+|[ \t]+$/, "", item)
                if (item == "") return
                # 제약·인덱스 조각은 컬럼이 아니다
                if (toupper(item) ~ /^(CONSTRAINT|INDEX|KEY|PRIMARY|UNIQUE|CHECK|FOREIGN|FULLTEXT|SPATIAL)[ \t(]/) return
                n = length(item)
                # 1) 컬럼 이름 — 백틱은 벗긴다
                i = 1
                while (i <= n && substr(item, i, 1) ~ /[ \t]/) i++
                name = ""
                if (substr(item, i, 1) == "`") {
                    i++
                    while (i <= n && substr(item, i, 1) != "`") { name = name substr(item, i, 1); i++ }
                    i++
                } else {
                    while (i <= n && substr(item, i, 1) !~ /[ \t]/) { name = name substr(item, i, 1); i++ }
                }
                if (name == "") return
                # 2) 타입 이름
                while (i <= n && substr(item, i, 1) ~ /[ \t]/) i++
                ty = ""
                while (i <= n && substr(item, i, 1) ~ /[A-Za-z0-9_]/) { ty = ty substr(item, i, 1); i++ }
                if (ty == "") return
                # 3) 괄호 인자 — 따옴표 안의 공백은 지키고, 밖의 공백은 버린다.
                #    DECIMAL(5, 2) → decimal(5,2), ENUM(\n 'A',\n 'B'\n) → enum('a','b')
                while (i <= n && substr(item, i, 1) ~ /[ \t]/) i++
                if (substr(item, i, 1) == "(") {
                    depth = 0; inq = 0
                    while (i <= n) {
                        c = substr(item, i, 1)
                        if (c == "\047") inq = !inq
                        if (!inq) {
                            if (c == "(") depth++
                            else if (c == ")") depth--
                            if (c ~ /[ \t]/) { i++; continue }
                        }
                        ty = ty c
                        i++
                        if (!inq && depth == 0) break
                    }
                }
                # 4) 타입에 붙는 수식어만 더 읽는다. 나머지 단어에서 멈춘다.
                rest = substr(item, i)
                while (match(rest, /^[ \t]*[A-Za-z_]+/)) {
                    w = substr(rest, RSTART, RLENGTH)
                    gsub(/^[ \t]+/, "", w)
                    if (tolower(w) == "unsigned" || tolower(w) == "signed" || tolower(w) == "zerofill") {
                        ty = ty " " tolower(w)
                        rest = substr(rest, RSTART + RLENGTH)
                    } else if (tolower(w) == "character" || tolower(w) == "collate" || tolower(w) == "set" || tolower(w) == "binary") {
                        # CHARACTER SET x / COLLATE y — 읽고 버린다 (한 단어씩 소비)
                        rest = substr(rest, RSTART + RLENGTH)
                        if (tolower(w) != "character" && tolower(w) != "set") {
                            sub(/^[ \t]*[A-Za-z0-9_]+/, "", rest)
                        }
                    } else break
                }
                printf "%s\t%s\n", name, canon(ty)
            }
            $0 ~ "^CREATE TABLE `?"t"`? \\(" { inside=1; next }
            inside && /^\) *ENGINE=/ { inside=0; done=1; next }
            inside && !done {
                line = $0
                sub(/[ \t]*--.*$/, "", line)              # 본문 중간 주석 제거
                gsub(/^[ \t]+|[ \t]+$/, "", line)
                if (line == "") next
                body = body " " line
            }
            END {
                n = length(body); depth = 0; inq = 0; cur = ""
                for (i = 1; i <= n; i++) {
                    c = substr(body, i, 1)
                    if (c == "\047") inq = !inq
                    if (!inq) {
                        if (c == "(") depth++
                        else if (c == ")") depth--
                        else if (c == "," && depth == 0) { emit(cur); cur = ""; continue }
                    }
                    cur = cur c
                }
                emit(cur)
            }'
}

# 컬럼 이름만 (기존 호출부 호환)
expected_columns() { schema_column_defs "$1" | cut -f1; }

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
        printf '  - Access denied    → %s 의 MYSQL_ROOT_PASSWORD 확인\n' "$ENV_FILE"
        printf '  - Unknown database → %s 의 MYSQL_DATABASE 확인 (현재: %s)\n' "$ENV_FILE" "$DB_NAME"
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
    log "  1) bash scripts/backup-mysql.sh                       # 먼저 백업 (운영 기준 — test 는 직접 mysqldump)"
    log "  2) git show $SCHEMA_REF:$SCHEMA_PATH \\"
    log "       | docker exec -i -e MYSQL_PWD=\"\$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' $ENV_FILE)\" \\"
    log "         $CONTAINER mysql -uroot $DB_NAME"
    log "  3) MYSQL_CONTAINER=$CONTAINER MYSQL_ENV_FILE=${MYSQL_ENV_FILE:-.env} bash scripts/check-schema.sh   # OK 확인"
    exit 1
fi

# 라이브 컬럼을 한 번에 받는다. 테이블마다 SHOW COLUMNS 를 도는 것보다 왕복이 적고,
# information_schema.COLUMNS 의 COLUMN_TYPE 은 `char(66)` `enum('A','B')` `decimal(5,2)`
# 처럼 schema.sql 과 같은 MySQL 문법으로 나오므로 방언 변환이 필요 없다.
LIVE_COLUMNS="$(mysql_q "
    SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     ORDER BY TABLE_NAME, ORDINAL_POSITION;")"
MYSQL_RC=$?
if [ $MYSQL_RC -ne 0 ]; then
    fail "information_schema 질의 실패 (exit $MYSQL_RC). mysql 이 돌려준 원문:"
    sed 's/^/  /' "$MYSQL_ERR" >&2
    exit 2
fi

# 라이브 타입을 `테이블.컬럼 → 정규화된타입` 연관배열로 한 번만 만든다.
# 컬럼마다 질의하거나 awk 를 새로 띄우면 컬럼 200여 개에 프로세스 200여 개가 된다.
declare -A LIVE_TYPE=()
while IFS=$'\t' read -r ltab lcol ltype; do
    [ -n "${lcol:-}" ] || continue
    LIVE_TYPE["$ltab.$lcol"]="$ltype"
done < <(printf '%s\n' "$LIVE_COLUMNS" | awk -F'\t' "$CANON_AWK"'
    NF >= 3 { printf "%s\t%s\t%s\n", $1, $2, canon($3) }')

MISSING_TABLES=()
MISSING_COLUMNS=()
TYPE_MISMATCHES=()

for table in $(expected_tables); do
    if ! printf '%s\n' "$LIVE_TABLES" | grep -qx "$table"; then
        MISSING_TABLES+=("$table")
        continue
    fi
    while IFS=$'\t' read -r col want; do
        [ -n "${col:-}" ] || continue
        got="${LIVE_TYPE[$table.$col]:-}"
        if [ -z "$got" ]; then
            MISSING_COLUMNS+=("$table.$col")
        elif [ "$got" != "$want" ]; then
            # 표기 형식: 테이블.컬럼<TAB>기대<TAB>실제 — 보고에서 열로 맞춰 찍는다
            TYPE_MISMATCHES+=("$table.$col"$'\t'"$want"$'\t'"$got")
        fi
    done < <(schema_column_defs "$table")
done

# ── 보고 ────────────────────────────────────────────────────────────
log "기준       : $SCHEMA_ORIGIN"
log "대상 DB    : $DB_NAME ($CONTAINER)"
log "기대 테이블 : $(expected_tables | wc -l) 개"
log "라이브 테이블: $(printf '%s\n' "$LIVE_TABLES" | wc -l) 개"
log "대조한 컬럼 : ${#LIVE_TYPE[@]} 개 (라이브 기준)"
log ""

if [ ${#MISSING_TABLES[@]} -eq 0 ] && [ ${#MISSING_COLUMNS[@]} -eq 0 ] && [ ${#TYPE_MISMATCHES[@]} -eq 0 ]; then
    log "OK: 부족한 테이블·컬럼 없음, 컬럼 타입도 일치. 배포해도 validate 가 통과한다."
    log "    (NULL 허용·DEFAULT·인덱스는 대조하지 않는다 — 스크립트 상단 주석 참고)"
    exit 0
fi

# ── SAFE_MISSING_TABLES_FILE ─────────────────────────────────────────
# 호출자(예: test-deploy.sh)가 "없는 테이블만 있고, 없는 컬럼·타입 불일치는 전혀
# 없는" 안전한 경우를 기계적으로 판별해 자동 적용을 시도할 수 있도록, 그 조건을
# 만족할 때만 없는 테이블 이름을 파일로 남긴다. 없는 컬럼 추가나 타입 변경은
# 기존 행의 데이터를 건드릴 수 있어(위 2026-09-08 사고) 여기 포함하지 않는다 —
# 새 테이블 CREATE 만 "기존 데이터에 영향 없음"이 기계적으로 보장되는 경우다.
if [ -n "${SAFE_MISSING_TABLES_FILE:-}" ]; then
    rm -f "$SAFE_MISSING_TABLES_FILE"
    if [ ${#MISSING_TABLES[@]} -gt 0 ] && [ ${#MISSING_COLUMNS[@]} -eq 0 ] && [ ${#TYPE_MISMATCHES[@]} -eq 0 ]; then
        printf '%s\n' "${MISSING_TABLES[@]}" > "$SAFE_MISSING_TABLES_FILE"
    fi
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
if [ ${#TYPE_MISMATCHES[@]} -gt 0 ]; then
    log "타입이 다른 컬럼 ${#TYPE_MISMATCHES[@]} 개 (기준 → 라이브):"
    printf '%s\n' "${TYPE_MISMATCHES[@]}" \
        | awk -F'\t' '{ printf "  %-48s %s  →  %s\n", $1, $2, $3 }'
    log ""
    log "  이런 불일치가 2026-09-08 롤백의 원인이었다 (tx_hash: char(66) vs varchar(66))."
    log "  엔티티 쪽이 틀렸을 수도 있다 — 예: @Column(length=66) 만 쓰면 Hibernate 는"
    log "  varchar 를 기대한다. CHAR 를 원하면 columnDefinition 이나 @Column(columnDefinition=\"CHAR(66)\")"
    log "  이 필요하다. **DB 를 고칠 것인지 엔티티를 고칠 것인지 먼저 결정할 것.**"
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
        log ""
    fi
    if [ ${#TYPE_MISMATCHES[@]} -gt 0 ]; then
        log "-- 타입 변경은 **주석으로만** 낸다. MODIFY COLUMN 은 기존 데이터를 조용히 자르거나"
        log "-- (varchar(500)→varchar(100)) 값을 바꿔 버릴 수 있고, NULL·DEFAULT·CHARSET 을"
        log "-- 다시 적어 주지 않으면 그것들이 날아간다. 아래는 타입 부분만 보여 주는 초안이다."
        printf '%s\n' "${TYPE_MISMATCHES[@]}" \
            | awk -F'\t' '{
                split($1, p, ".")
                printf "  -- ALTER TABLE %s MODIFY COLUMN %s %s;  -- 현재: %s\n", p[1], p[2], toupper($2), $3
              }'
    fi
else
    log "DDL 을 보려면: bash scripts/check-schema.sh --ddl"
fi

log ""
log "⚠️  이 상태로 backend 를 배포하면 Hibernate validate 가 실패해 컨테이너가 죽는다."
log "    DDL 을 먼저 적용하고 이 스크립트를 다시 돌려 OK 를 확인한 뒤 배포할 것."
exit 1
