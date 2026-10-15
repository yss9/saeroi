#!/usr/bin/env bash
# compose 호출의 단일 경로. 다른 스크립트는 `docker compose`를 직접 부르지 말고
# 이 파일을 source 한 뒤 dc() 를 쓴다.
#
#   . "$(dirname "${BASH_SOURCE[0]}")/compose.sh"
#   dc up -d --no-deps backend
#
# 존재 이유: EC2는 실제 이미지가 없는 서비스를 docker-compose.placeholder.yml 로 대체해 띄웠다.
# 2026-09-01 에 backend/frontend/ai 가 모두 졸업해 그 파일은 삭제됐고, 지금은 base 구성만 쓴다.
# 이 함수를 남겨두는 이유는 compose 호출 경로를 하나로 유지하기 위해서다 — 나중에 다시
# override 가 필요해지면 파일만 만들면 자동으로 합성된다.

CF_DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CF_COMPOSE_FILES=(-f "$CF_DEPLOY_DIR/docker-compose.yml")
if [ -f "$CF_DEPLOY_DIR/docker-compose.placeholder.yml" ]; then
    CF_COMPOSE_FILES+=(-f "$CF_DEPLOY_DIR/docker-compose.placeholder.yml")
fi

dc() {
    docker compose "${CF_COMPOSE_FILES[@]}" "$@"
}
