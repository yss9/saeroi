#!/usr/bin/env bash
# 최신 circular-fashion/backend:latest 이미지로 backend 컨테이너 재기동
set -euo pipefail
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"

docker compose up -d backend
bash scripts/health-check.sh backend
