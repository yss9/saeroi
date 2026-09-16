#!/usr/bin/env bash
# Jenkinsfile Test 스테이지가 python:3.12-slim 컨테이너 안에서 실행한다.
set -eu

python -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
if [ -f requirements.txt ]; then pip install -r requirements.txt; else pip install .; fi

if [ "${HAS_TESTS:-false}" = "true" ]; then
    pip install pytest
    python -m pytest -q
else
    echo "SKIP: tests 디렉터리가 없어 테스트를 건너뜁니다."
fi
