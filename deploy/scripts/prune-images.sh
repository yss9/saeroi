#!/usr/bin/env bash
# 떠도는 Docker 이미지·빌드 캐시 정리 (EC2 디스크 가득참 예방).
#
# 배포 경로에서 떼어낸 것이다 — 이유는 deploy-service.sh 6) 주석에 있다.
# 요약: 네 파트가 하나의 Docker 데몬을 공유하고, prune 이 부르는 containerd
# content GC 가 다른 파트의 진행 중인 빌드 ingest 를 지워 버렸다.
#
# 그래서 이 스크립트는 두 겹으로 막는다.
#
#   1) 나이 필터 — 방금 만들어진 것은 건드리지 않는다. 지금 돌고 있는 빌드의
#      산출물은 늘 이 필터에 걸러진다.
#   2) flock — 이 스크립트끼리 겹치지 않게 한다. 배포와 겹치는 것을 막지는 못하지만
#      (배포는 이제 prune 을 부르지 않는다) 크론이 밀렸을 때 두 개가 동시에 도는 것은 막는다.
#
# ⚠️ `prune -a` 는 쓰지 않는다. 잠시 내려둔 서비스의 베이스 이미지까지 지워
#    재다운로드를 유발한다. 태그 없는(dangling) 것만 지운다.
set -euo pipefail

# 배포 직후 롤백 대상으로 이전 이미지를 남겨 둬야 한다. 24h 면 하루 안에 낸 배포는
# 전부 되돌릴 수 있다.
AGE="${PRUNE_MIN_AGE:-24h}"
LOCKFILE="/tmp/circular-fashion-prune.lock"

exec 9>"$LOCKFILE"
if ! flock -n 9; then
    echo "$(date '+%F %T') SKIP: 다른 prune 이 돌고 있다"
    exit 0
fi

echo "$(date '+%F %T') prune 시작 (until=$AGE)"

before="$(docker system df --format '{{.Type}} {{.Size}}' 2>/dev/null || true)"

# 태그 없는 이미지
docker image prune -f --filter "until=$AGE" || true
# 빌드 캐시는 따로 관리된다. 이게 실제로 가장 크다 (2026-09-07 기준 2.2GB).
docker builder prune -f --filter "until=$AGE" || true

after="$(docker system df --format '{{.Type}} {{.Size}}' 2>/dev/null || true)"

echo "--- 이전 ---"
echo "$before"
echo "--- 이후 ---"
echo "$after"
echo "$(date '+%F %T') prune 완료"
