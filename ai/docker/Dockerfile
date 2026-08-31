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

ENV TZ=Asia/Seoul \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

COPY --from=build /install/deps /usr/local
COPY app/ app/

# backend 가 AI_SERVER_BASE_URL=http://ai:8000 으로 호출한다 — 포트를 바꾸지 말 것
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
