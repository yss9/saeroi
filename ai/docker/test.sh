#!/usr/bin/env bash
# Jenkinsfile Test 스테이지가 python:3.12-slim 컨테이너 안에서 실행한다.
set -eu

python -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
if [ -f requirements.txt ]; then pip install -r requirements.txt; else pip install .; fi

# ── cv2 를 headless 하나로 정리한다 ──────────────────────
#
# rapidocr-onnxruntime 이 **GUI 판 opencv-python** 을 의존성으로 끌고 온다.
# requirements.txt 는 headless 를 적어 두지만 둘 다 깔리고, 같은 `cv2` 자리를 놓고
# GUI 판이 이기면 import 단계에서 이렇게 죽는다.
#
#     ImportError: libxcb.so.1: cannot open shared object file
#
# 런타임 이미지(docker/Dockerfile)는 libgl1·libglib2.0-0 을 넣어 이 문제를 피하지만,
# 테스트는 맨 python:3.12-slim 에서 돌고 CI 는 비root 라 apt-get 을 쓸 수 없다.
# 그래서 라이브러리를 더하는 대신 **GUI 판을 걷어낸다** — OCR 은 창을 띄우지 않으므로
# headless 로 충분하다.
#
# 두 패키지가 같은 디렉터리를 공유해 한쪽만 지우면 남은 쪽도 깨진다. 둘 다 지우고
# headless 만 다시 넣는다(--no-deps: 다시 GUI 판을 끌어오지 않도록).
HEADLESS_VERSION="$(sed -n 's/^opencv-python-headless==//p' requirements.txt)"
if [ -n "$HEADLESS_VERSION" ]; then
    pip uninstall -y opencv-python opencv-python-headless
    pip install --no-deps "opencv-python-headless==${HEADLESS_VERSION}"
fi

if [ "${HAS_TESTS:-false}" = "true" ]; then
    pip install pytest
    python -m pytest -q
else
    echo "SKIP: tests 디렉터리가 없어 테스트를 건너뜁니다."
fi
