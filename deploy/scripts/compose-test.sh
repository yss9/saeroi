#!/usr/bin/env bash
# test 스택 compose 호출의 단일 경로. 다른 스크립트는 `docker compose`를 직접 부르지 말고
# 이 파일을 source 한 뒤 dc_test() 를 쓴다.
#
#   . "$(dirname "${BASH_SOURCE[0]}")/compose-test.sh"
#   dc_test up -d --no-deps backend
#
# --env-file 을 명시하는 이유: docker compose 는 기본으로 작업 디렉터리의 `.env` 를 읽는데
# 그건 운영 자격증명이다(scripts/compose.sh 가 쓰는 것과 같은 파일). test 스택은 반드시
# `.env.test` 를 읽어야 한다. -f 로 docker-compose.test.yml 하나만 지정하므로 운영
# docker-compose.yml 과 합성되지 않고 완전히 별도 프로젝트(circular-fashion-test,
# 파일 안의 `name:`)로 뜬다.

CF_DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

dc_test() {
    docker compose \
        -f "$CF_DEPLOY_DIR/docker-compose.test.yml" \
        --env-file "$CF_DEPLOY_DIR/.env.test" \
        "$@"
}
