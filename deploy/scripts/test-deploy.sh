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
#      test 스택에 한해서 "없는 테이블만 있고, 없는 컬럼·타입 불일치는 전혀 없는" 경우는
#      db/migrations/*.sql 에서 해당 테이블을 만드는 파일을 자동 적용한다. 단, 그 파일이
#      CREATE TABLE 외에 ALTER TABLE 을 하나라도 포함하면(기존 테이블 변경 — 데이터에
#      영향 가능) 자동 적용하지 않고 사람에게 넘긴다. 운영(deploy-service.sh)에는 이
#      자동 적용을 넣지 않는다 — check-schema.sh 자체 원칙("사람이 보고 결정")은 운영에서
#      그대로 유지한다.
if [ "$SERVICE" = "backend" ]; then
    SAFE_TABLES_FILE="$(mktemp)"
    trap 'rm -f "$SAFE_TABLES_FILE"' EXIT
    if ! MYSQL_CONTAINER=cft-mysql MYSQL_ENV_FILE=.env.test SAFE_MISSING_TABLES_FILE="$SAFE_TABLES_FILE" \
            bash scripts/check-schema.sh; then
        if [ -s "$SAFE_TABLES_FILE" ]; then
            echo "INFO: 없는 테이블만 발견됨 — 대응 마이그레이션 자동 적용을 시도합니다."
            MIGRATIONS_DIR="src/main/resources/db/migrations"
            APPLY_FILES=()
            AUTO_OK=1
            while IFS= read -r table; do
                [ -n "$table" ] || continue
                MATCH=""
                for mf in $(git ls-tree -r origin/backend/develop --name-only -- "$MIGRATIONS_DIR" 2>/dev/null); do
                    if git show "origin/backend/develop:$mf" 2>/dev/null \
                            | grep -qiE "^CREATE TABLE( IF NOT EXISTS)? \`?${table}\`? "; then
                        MATCH="$mf"
                        break
                    fi
                done
                if [ -z "$MATCH" ]; then
                    echo "FAIL: 테이블 $table 을 만드는 마이그레이션 파일을 찾지 못했습니다 — 수동 확인 필요."
                    AUTO_OK=0
                    break
                fi
                if [ "$(git show "origin/backend/develop:$MATCH" | grep -c '^ALTER TABLE')" -gt 0 ]; then
                    echo "FAIL: $MATCH 는 테이블 $table 외에 기존 테이블 ALTER 도 포함합니다 — 자동 적용은 CREATE 전용 파일만 허용, 수동 확인 필요."
                    AUTO_OK=0
                    break
                fi
                case " ${APPLY_FILES[*]:-} " in
                    *" $MATCH "*) ;;
                    *) APPLY_FILES+=("$MATCH") ;;
                esac
            done < "$SAFE_TABLES_FILE"

            if [ "$AUTO_OK" -eq 1 ] && [ ${#APPLY_FILES[@]} -gt 0 ]; then
                DB_NAME="$(sed -n 's/^MYSQL_DATABASE=//p' .env.test | head -1)"
                ROOT_PW="$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' .env.test | head -1)"
                mkdir -p "$HOME/backups/mysql"
                STAMP="$(date '+%Y%m%d-%H%M%S')"
                BACKUP_TARGET="$HOME/backups/mysql/${DB_NAME}-test-${STAMP}-before-auto-migration.sql.gz"
                docker exec -e MYSQL_PWD="$ROOT_PW" cft-mysql \
                    mysqldump -uroot --single-transaction --routines --triggers "$DB_NAME" \
                    | gzip > "$BACKUP_TARGET"
                echo "INFO: 자동 적용 전 백업: $BACKUP_TARGET"
                for mf in "${APPLY_FILES[@]}"; do
                    echo "INFO: 적용 중: $mf"
                    git show "origin/backend/develop:$mf" \
                        | docker exec -e MYSQL_PWD="$ROOT_PW" -i cft-mysql mysql -uroot "$DB_NAME"
                done
                if ! MYSQL_CONTAINER=cft-mysql MYSQL_ENV_FILE=.env.test bash scripts/check-schema.sh; then
                    echo "FAIL: 자동 적용 후에도 스키마가 맞지 않습니다 — 수동 확인 필요."
                    exit 1
                fi
                echo "INFO: 자동 적용 완료, 스키마 preflight 통과."
            else
                echo "FAIL: 일부 테이블은 자동 적용할 수 없습니다. 수동으로 적용한 뒤 다시 배포하세요."
                echo "      (DDL 초안: MYSQL_CONTAINER=cft-mysql MYSQL_ENV_FILE=.env.test bash scripts/check-schema.sh --ddl)"
                exit 1
            fi
        else
            echo "FAIL: test DB 스키마가 배포하려는 backend(origin/backend/develop) 기준과 다릅니다."
            echo "      없는 컬럼·타입 불일치가 섞여 있어 자동 적용하지 않습니다 — 기존 데이터에 영향 줄 수 있습니다."
            echo "      위 내용을 cft-mysql 에 먼저 수동 적용한 뒤 다시 배포하세요."
            echo "      (DDL 초안: MYSQL_CONTAINER=cft-mysql MYSQL_ENV_FILE=.env.test bash scripts/check-schema.sh --ddl)"
            exit 1
        fi
    fi
    rm -f "$SAFE_TABLES_FILE"
    trap - EXIT
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
