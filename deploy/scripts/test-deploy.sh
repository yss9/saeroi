#!/usr/bin/env bash
# test 스택 서비스 단위 배포. 각 파트 Jenkinsfile 의 <파트>/develop 스테이지가
# 이미지를 :test 태그로 빌드한 뒤 SSH 로 이 스크립트를 호출한다.
#
# 사용법: bash scripts/test-deploy.sh <backend|frontend|ai>
#
# scripts/deploy-service.sh(운영)와 같은 패턴이다 — 이미지 존재 확인 → --no-deps 배포 →
# 헬스체크 → 롤백. 차이는 태그(:test)와 컨테이너 접두사(cft-)뿐이다.
# **반드시 --no-deps** — 한 파트만 develop 에 병합해도 다른 파트 서비스(mysql/minio 포함)가
# 재기동되면 안 된다. deploy-service.sh 의 "이미지 정리는 여기서 하지 않는다" 원칙도 그대로
# 따른다 — prune 은 scripts/prune-images.sh 크론에만 맡긴다.
set -euo pipefail

SERVICE="${1:?사용법: test-deploy.sh <backend|frontend|ai>}"
IMAGE="circular-fashion/${SERVICE}:test"
CONTAINER="cft-${SERVICE}"

# docker-compose.test.yml 의 서비스 키는 backend/frontend 가 아니라 test-backend/
# test-frontend 다 — 운영과 같은 circular-fashion_web 네트워크에 붙을 때 Compose 가
# 서비스 키를 네트워크 별칭으로 자동 등록하는데, "backend"/"frontend" 를 그대로 쓰면
# 운영 cf-backend/cf-frontend 와 별칭이 충돌해 nginx 재시작 시 운영 트래픽이 test 로
# 샐 수 있다. ai/mysql/minio 는 internal 전용이라 충돌이 없어 그대로 둔다.
case "$SERVICE" in
    backend)  COMPOSE_SVC="test-backend" ;;
    frontend) COMPOSE_SVC="test-frontend" ;;
    *)        COMPOSE_SVC="$SERVICE" ;;
esac

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
. "$DEPLOY_DIR/scripts/compose-test.sh"

# 1) 배포할 이미지가 로컬에 실제로 있는지 먼저 본다.
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "FAIL: $IMAGE 이미지가 없습니다. Jenkinsfile 의 :test 빌드 스테이지를 확인하세요."
    exit 1
fi

# 2) 롤백 대상 확보 — 지금 떠 있는 test 컨테이너의 이미지 ID.
#    태그가 아니라 "circular-fashion/${SERVICE}:" 로 시작하는 태그가 하나라도 있으면
#    롤백 대상으로 삼는다(운영 deploy-service.sh 와 동일한 판정). 정확히 ":test" 태그만
#    찾으면 안 된다 — Jenkinsfile 이 새 이미지를 빌드하며 ":test" 를 새 이미지로 옮기고 나면
#    지금 떠 있는(이전) 이미지는 그 태그를 이미 잃은 뒤이기 때문이다. Jenkinsfile 이 같이
#    붙이는 ":test-$BUILD_NUMBER" 처럼 서비스별 다른 태그가 하나라도 남아있으면 잡아낸다.
ROLLBACK_ID=""
PREVIOUS_ID="$(docker inspect --format '{{.Image}}' "$CONTAINER" 2>/dev/null || true)"
if [ -n "$PREVIOUS_ID" ]; then
    PREVIOUS_TAGS="$(docker inspect --format '{{join .RepoTags ","}}' "$PREVIOUS_ID" 2>/dev/null || true)"
    case ",$PREVIOUS_TAGS," in
        *",circular-fashion/${SERVICE}:"*) ROLLBACK_ID="$PREVIOUS_ID" ;;
    esac
fi
if [ -z "$ROLLBACK_ID" ]; then
    echo "INFO: 롤백 대상 없음 (현재 컨테이너가 ${SERVICE} 이미지가 아님 — 첫 배포로 간주)."
fi

# 2.5) backend 는 스키마 preflight — ddl-auto: validate 라 새 마이그레이션이 DB 에
#      먼저 적용돼 있지 않으면 Hibernate 가 기동 즉시 죽는다(2026-09-14 user_notification
#      실사고, scripts/check-schema.sh 상단 주석의 2026-09-07 운영 사고와 같은 패턴).
#      배포 전에 잡아서, 마이그레이션 미적용 상태로 backend 를 죽이는 대신 배포 자체를 막는다.
#
#      2026-09-16 이전에는 여기서 "CREATE TABLE 만 있는 안전한 파일"만 자동 적용하고
#      ALTER 가 섞이면 사람에게 넘겼다 — 그래서 컬럼 추가·타입 변경이 섞인 마이그레이션이
#      머지될 때마다(오늘 case) 사람이 SSH 로 들어가 손으로 DDL 을 적용해야 했다.
#
#      이제 backend Jenkinsfile 에 병합 전 CI 게이트(scripts/verify-migrations.sh, backend
#      레포 소유)가 생겨서, backend/develop 에 있는 마이그레이션 파일은 이미 "적용하면
#      schema.sql 목표 상태와 정확히 같아진다"는 것이 병합 시점에 검증되어 있다. 그래서
#      ALTER 여부와 무관하게 test 스택에 한해 전부 자동 적용한다. 무엇을 이미 적용했는지는
#      cft-mysql 안의 deploy_migrations_applied 테이블로 직접 추적한다(Flyway 는 도입하지
#      않기로 했다 — 2026-09-07). 운영(deploy-service.sh)에는 자동 적용을 넣지 않는다 —
#      check-schema.sh 자체 원칙("사람이 보고 결정")은 운영에서 그대로 유지한다.
if [ "$SERVICE" = "backend" ]; then
    DB_NAME="$(sed -n 's/^MYSQL_DATABASE=//p' .env.test | head -1)"
    ROOT_PW="$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' .env.test | head -1)"
    [ -n "$DB_NAME" ] && [ -n "$ROOT_PW" ] || { echo "FAIL: .env.test 에 MYSQL_DATABASE/MYSQL_ROOT_PASSWORD 가 없다"; exit 1; }

    docker exec -e MYSQL_PWD="$ROOT_PW" cft-mysql mysql -uroot "$DB_NAME" -e \
        "CREATE TABLE IF NOT EXISTS deploy_migrations_applied (
            filename VARCHAR(255) PRIMARY KEY,
            applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )"

    # 2026-09-17 실사고: 아래 `git ls-tree`/`git show` 가 여기 checkout(~/deploy)의
    # **로컬에 캐시된** origin/backend/develop ref 를 그대로 읽었다. check-schema.sh
    # 는 :ro 마운트 문제(위 2026-09-16 실사고 주석) 때문에 이미 임시 bare repo 로
    # 매번 새로 fetch 하도록 고쳐져 있었는데, 이 블록은 그 처리가 없어 최신 스키마
    # (fresh fetch 결과)와 마이그레이션 파일 목록(stale 캐시)이 서로 다른 시점을
    # 봤다 — 방금 머지된 마이그레이션 파일이 안 보여 "적용 안 된 파일이 없다"는
    # 오탐 FAIL 이 났다. check-schema.sh 와 같은 임시 bare repo fetch 를 그대로 쓴다.
    #
    # 2026-09-19 실사고: 이 NEW_FILES 계산은 예전엔 "스키마 불일치일 때만" 돌았다.
    # 그런데 material_type enum 재정렬 마이그레이션(2026-09-18)처럼 컬럼 구조는
    # 그대로 두고 데이터(UPDATE)만 옮기는 파일은 schema.sql 대조에 애초에 안 걸린다
    # — check-schema.sh 는 테이블/컬럼/타입/NULL 허용만 보지 값은 보지 않는다. 그래서
    # 스키마는 "일치"로 나오고 게이트가 열리지 않아 그 마이그레이션이 조용히 스킵됐고,
    # 남아있던 구 enum 값을 JPA 가 읽는 순간 IllegalArgumentException 으로 500 이 났다
    # (cft-mysql, material/processing_output/processor_lead_time/processor_capability
    # 네 테이블). 그래서 이제 NEW_FILES 계산을 스키마 대조와 무관하게 항상 먼저 하고,
    # "스키마 불일치" 또는 "미적용 마이그레이션 파일 존재" 둘 중 하나만 참이어도 게이트를 연다.
    ORIGIN_URL="$(git config --get remote.origin.url)"
    MIG_TMP_GIT="$(mktemp -d)"
    git init -q --bare "$MIG_TMP_GIT"
    MIG_FETCH_OK=0
    for attempt in 1 2 3; do
        if git --git-dir="$MIG_TMP_GIT" fetch -q "$ORIGIN_URL" backend/develop 2>/dev/null; then
            MIG_FETCH_OK=1
            break
        fi
        [ "$attempt" -lt 3 ] && sleep 2
    done
    if [ "$MIG_FETCH_OK" -ne 1 ]; then
        rm -rf "$MIG_TMP_GIT"
        echo "FAIL: origin 에서 backend/develop 를 3회 재시도해도 fetch 하지 못했다 — 네트워크/GitLab 확인 후 재시도할 것."
        exit 1
    fi
    trap 'rm -rf "$MIG_TMP_GIT"' EXIT

    MIGRATIONS_DIR="src/main/resources/db/migrations"
    APPLIED=" $(docker exec -e MYSQL_PWD="$ROOT_PW" cft-mysql mysql -uroot -N "$DB_NAME" \
        -e 'SELECT filename FROM deploy_migrations_applied' | tr '\n' ' ') "
    NEW_FILES=()
    for mf in $(git --git-dir="$MIG_TMP_GIT" ls-tree -r FETCH_HEAD --name-only -- "$MIGRATIONS_DIR" 2>/dev/null | sort); do
        base="$(basename "$mf")"
        case "$APPLIED" in
            *" $base "*) ;;
            *) NEW_FILES+=("$mf") ;;
        esac
    done

    SCHEMA_OK=1
    MYSQL_CONTAINER=cft-mysql MYSQL_ENV_FILE=.env.test bash scripts/check-schema.sh || SCHEMA_OK=0

    if [ "$SCHEMA_OK" -eq 0 ] || [ ${#NEW_FILES[@]} -gt 0 ]; then
        if [ "$SCHEMA_OK" -eq 0 ]; then
            echo "INFO: 스키마 불일치 발견 — 아직 적용 안 된 마이그레이션을 자동 적용합니다."
        else
            echo "INFO: 스키마는 일치하지만 미적용 마이그레이션 파일이 있습니다 — 자동 적용합니다."
            echo "      (데이터만 옮기는 UPDATE 전용 마이그레이션은 schema.sql 대조로 못 잡는다)"
        fi
        echo "      (병합 전 CI 게이트가 이미 검증했으므로 test 환경에 한해 ALTER 포함 전부 적용)"

        if [ ${#NEW_FILES[@]} -eq 0 ]; then
            echo "FAIL: 적용 안 된 마이그레이션 파일이 없는데도 스키마가 다릅니다."
            echo "      schema.sql 을 마이그레이션 없이 손으로 고쳤거나, DB 가 기록 밖에서"
            echo "      바뀐 것으로 보입니다 — 수동 확인 필요."
            echo "      (DDL 초안: MYSQL_CONTAINER=cft-mysql MYSQL_ENV_FILE=.env.test bash scripts/check-schema.sh --ddl)"
            exit 1
        fi

        # 백업은 **호스트의** ~/backups/mysql 에 남겨야 한다 (backup-mysql.sh 와 같은 자리).
        # 2026-09-18 발견: 이 스크립트는 Jenkins(cf-jenkins) 컨테이너 안에서 실행되는데, 그 안의
        # $HOME(/root) 은 어떤 볼륨에도 물려 있지 않다 — 자동 적용 전 백업 5개가 전부 컨테이너
        # 파일시스템(/root/backups)에만 쌓여 있었고 컨테이너를 재생성하면 통째로 사라진다.
        # ~/deploy 는 :ro 마운트라 거기에도 쓸 수 없다. docker.sock 은 있으므로, 호스트 경로를
        # bind 한 일회성 컨테이너로 파이프를 넘겨 호스트에 직접 쓴다 (경로는 데몬=호스트 기준으로
        # 해석된다). 쓰는 주체는 EC2 사용자(ubuntu, uid 1000)로 맞춘다 — root 로 쓰면 사람이 여는
        # 것도, 보관 정리도 Permission denied 가 된다(2026-09-18 probe 로 확인). 디렉터리는
        # backup-mysql.sh 가 이미 만들어 두므로 컨테이너 경로에서는 mkdir 하지 않는다.
        # 호스트에서 직접 돌릴 때는 그냥 파일로 쓴다.
        BACKUP_HOST_DIR="${TEST_MYSQL_BACKUP_DIR:-/home/ubuntu/backups/mysql}"
        STAMP="$(date '+%Y%m%d-%H%M%S')"
        BACKUP_NAME="${DB_NAME}-test-${STAMP}-before-auto-migration.sql.gz"
        BACKUP_TARGET="$BACKUP_HOST_DIR/$BACKUP_NAME"
        if [ -f /.dockerenv ]; then
            docker exec -e MYSQL_PWD="$ROOT_PW" cft-mysql \
                mysqldump -uroot --single-transaction --routines --triggers "$DB_NAME" \
                | gzip \
                | docker run --rm -i --user "${TEST_MYSQL_BACKUP_OWNER:-1000:1000}" \
                    -v "$BACKUP_HOST_DIR:/out" alpine:3.20 \
                    sh -c "cat > /out/$BACKUP_NAME && chmod 600 /out/$BACKUP_NAME"
        else
            mkdir -p "$BACKUP_HOST_DIR"
            docker exec -e MYSQL_PWD="$ROOT_PW" cft-mysql \
                mysqldump -uroot --single-transaction --routines --triggers "$DB_NAME" \
                | gzip > "$BACKUP_TARGET"
            chmod 600 "$BACKUP_TARGET"
        fi
        echo "INFO: 자동 적용 전 백업: $BACKUP_TARGET"

        for mf in "${NEW_FILES[@]}"; do
            base="$(basename "$mf")"
            echo "INFO: 적용 중: $mf"
            if ! git --git-dir="$MIG_TMP_GIT" show "FETCH_HEAD:$mf" \
                    | docker exec -e MYSQL_PWD="$ROOT_PW" -i cft-mysql mysql -uroot "$DB_NAME"; then
                echo "FAIL: $mf 적용 중 오류 — 백업($BACKUP_TARGET)으로 복구 후 수동 확인 필요."
                exit 1
            fi
            docker exec -e MYSQL_PWD="$ROOT_PW" cft-mysql mysql -uroot "$DB_NAME" \
                -e "INSERT INTO deploy_migrations_applied (filename) VALUES ('$base')"
        done

        if ! MYSQL_CONTAINER=cft-mysql MYSQL_ENV_FILE=.env.test bash scripts/check-schema.sh; then
            echo "FAIL: 자동 적용 후에도 스키마가 맞지 않습니다 — CI 게이트를 우회한 커밋(직접"
            echo "      push 등)일 수 있습니다. 백업($BACKUP_TARGET)으로 복구 후 수동 확인 필요."
            exit 1
        fi
        echo "INFO: 자동 적용 완료 (${#NEW_FILES[@]}건), 스키마 preflight 통과."
    fi
fi

# 3) 해당 서비스만 재기동 (--no-deps: mysql/minio 등은 건드리지 않는다)
dc_test up -d --no-deps "$COMPOSE_SVC"

# 4) 실제로 :test 이미지가 떴는지 확인.
ACTUAL="$(docker inspect --format '{{.Config.Image}}' "$CONTAINER" 2>/dev/null || echo unknown)"
if [ "$ACTUAL" != "$IMAGE" ]; then
    echo "FAIL: $SERVICE(test) 컨테이너가 $IMAGE 가 아닌 다른 이미지로 실행 중입니다 (현재: $ACTUAL)."
    exit 1
fi

# 5) 헬스체크. scripts/health-check.sh 를 /test/ 베이스 + test compose 모드로 재사용한다.
#    HEALTHCHECK_COMPOSE=test 를 주면 health-check.sh 내부의 컨테이너 상태 확인이
#    운영이 아니라 cft-* 컨테이너를 본다 (이전엔 항상 운영 컨테이너를 봐서, 운영 쪽
#    상태와 무관하게 test 배포가 실패/롤백될 수 있는 버그가 있었다).
if ! HEALTHCHECK_COMPOSE=test HEALTHCHECK_WEB_BASE="https://j15e104.p.ssafy.io/test" bash scripts/health-check.sh "$SERVICE"; then
    if [ -n "$ROLLBACK_ID" ]; then
        echo "ROLLBACK: 헬스체크 실패 — $IMAGE 를 이전 이미지($ROLLBACK_ID)로 되돌립니다."
        docker tag "$ROLLBACK_ID" "$IMAGE"
        dc_test up -d --no-deps "$COMPOSE_SVC"
        if HEALTHCHECK_COMPOSE=test HEALTHCHECK_WEB_BASE="https://j15e104.p.ssafy.io/test" bash scripts/health-check.sh "$SERVICE"; then
            echo "ROLLBACK 완료: 이전 버전으로 복구되었습니다. 이번 빌드는 배포되지 않았습니다."
        else
            echo "ROLLBACK 실패: 이전 이미지로도 헬스체크가 통과하지 않습니다. 수동 확인이 필요합니다."
        fi
    else
        echo "FAIL: 헬스체크 실패. 되돌릴 이전 이미지가 없어 현재 상태를 유지합니다."
    fi
    exit 1
fi
