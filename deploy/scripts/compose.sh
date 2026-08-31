#!/usr/bin/env bash
# compose 호출의 단일 경로. 다른 스크립트는 `docker compose`를 직접 부르지 말고
# 이 파일을 source 한 뒤 dc() 를 쓴다.
#
#   . "$(dirname "${BASH_SOURCE[0]}")/compose.sh"
#   dc up -d --no-deps backend
#
# 존재 이유: EC2는 아직 실제 이미지가 없는 서비스를 docker-compose.placeholder.yml 로 대체해
# 띄우고 있다. override 를 빠뜨리고 `docker compose up` 을 하면 없는 이미지를 pull 하려다
# 실패하거나, placeholder 로 떠 있던 다른 서비스까지 원래 정의로 되돌리려 든다.
# 파일이 없으면(= 모든 파트가 졸업하면) 자동으로 base 구성만 쓰므로 나중에 지우기만 하면 된다.

CF_DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CF_COMPOSE_FILES=(-f "$CF_DEPLOY_DIR/docker-compose.yml")
if [ -f "$CF_DEPLOY_DIR/docker-compose.placeholder.yml" ]; then
    CF_COMPOSE_FILES+=(-f "$CF_DEPLOY_DIR/docker-compose.placeholder.yml")
fi

dc() {
    docker compose "${CF_COMPOSE_FILES[@]}" "$@"
}
