#!/usr/bin/env bash
# test 스택 종료. 기본은 볼륨 보존 — 재기동할 때 DB/스토리지 데이터를 다시 쌓지 않아도 된다.
# 완전 초기화가 필요하면 TEST_DOWN_VOLUMES=1 로 호출한다.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
. "$DEPLOY_DIR/scripts/compose-test.sh"

if [ "${TEST_DOWN_VOLUMES:-0}" = "1" ]; then
    echo "WARN: 볼륨까지 삭제합니다 (test 전용 데이터 — 운영과 무관)."
    dc_test down -v
else
    dc_test down
fi
