#!/usr/bin/env bash
# 최신 circular-fashion/backend:latest 이미지로 backend 컨테이너를 재기동한다.
# Jenkins의 backend 파이프라인이 backend/master 빌드에서 호출한다 (컨테이너 안에서 실행됨).
#
# 실제 로직은 scripts/deploy-service.sh 에 있다. 이 파일은 파트 Jenkinsfile 이 부르는
# 진입점이라 이름을 유지해야 한다 — 지우면 해당 파트의 Deploy 스테이지가 깨진다.
set -euo pipefail
exec bash "$(dirname "${BASH_SOURCE[0]}")/deploy-service.sh" backend
