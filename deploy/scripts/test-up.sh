#!/usr/bin/env bash
# test 스택 전체 기동. 운영 docker-compose.yml 과는 완전히 별도 프로젝트다.
#
# 전제: 운영 nginx(circular-fashion_web 네트워크)가 이미 떠 있어야 한다 — frontend/backend
# 가 external 네트워크로 그 네트워크에 조인하므로, 네트워크 자체가 없으면 실패한다.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
. "$DEPLOY_DIR/scripts/compose-test.sh"

if [ ! -f "$DEPLOY_DIR/.env.test" ]; then
    echo "FAIL: .env.test 가 없습니다. .env.test.example 을 복사해 실값을 채우세요."
    exit 1
fi

if ! docker network inspect circular-fashion_web >/dev/null 2>&1; then
    echo "FAIL: circular-fashion_web 네트워크가 없습니다. 운영 스택(docker-compose.yml)이 먼저 떠 있어야 합니다."
    exit 1
fi

dc_test up -d
echo "test 스택 기동 완료. 상태 확인: . scripts/compose-test.sh; dc_test ps"
