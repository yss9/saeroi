# circular-fashion/ai:latest
# 초안이다 — 아직 소스가 없어 그대로는 빌드되지 않는다.
# requirements.txt 와 app/main.py 를 추가하면 그대로 동작한다.
# (파이프라인은 requirements.txt 가 없으면 Docker Build 스테이지를 아예 건너뛴다)

# ── 1st stage: 의존성 설치 ────────────────────────────────
FROM python:3.12-slim AS build
WORKDIR /install

RUN apt-get update \
 && apt-get install -y --no-install-recommends build-essential \
 && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN pip install --no-cache-dir --prefix=/install/deps -r requirements.txt

# ── 2nd stage: 런타임 ─────────────────────────────────────
FROM python:3.12-slim
WORKDIR /app

# rapidocr 가 opencv 를 쓰는데, slim 이미지에는 그 공유 라이브러리가 없어
# import 단계에서 libGL.so.1 을 못 찾고 죽는다. 런타임 전용이라 build 스테이지가 아닌
# 여기에 넣는다.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
 && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Seoul \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

COPY --from=build /install/deps /usr/local
COPY app/ app/

# 컨테이너를 root 로 돌리지 않는다 (backend 와 동일한 정책, uid 10001 통일).
# 8000 은 비특권 포트라 비root 로도 바인딩에 문제가 없다.
# 나중에 모델 파일을 볼륨으로 마운트하면 그 볼륨 소유권도 uid 10001 로 맞출 것.
RUN groupadd -r -g 10001 app && useradd -r -u 10001 -g app -d /app -s /usr/sbin/nologin app  && chown -R app:app /app
USER app

# backend 가 AI_SERVER_BASE_URL=http://ai:8000 으로 호출한다 — 포트를 바꾸지 말 것
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
