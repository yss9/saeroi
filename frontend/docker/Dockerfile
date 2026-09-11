# circular-fashion/frontend:latest
# VITE_* 는 빌드 시점에 번들로 인라인되므로 반드시 --build-arg 로 주입해야 한다.
# (Jenkins frontend 파이프라인이 EC2 .env 의 VITE_ 줄을 읽어 자동 전달)
# 값이 바뀌면 재빌드해야 반영된다 — 컨트랙트 주소 변경 시 특히 주의.

# ── 1st stage: Vite 빌드 ──────────────────────────────────
# Vite 8 / TS 6 / eslint 10 조합은 Node 20.19+ 또는 22.12+ 를 요구한다
FROM node:22-alpine AS build
WORKDIR /app

# 의존성 레이어를 소스와 분리해 캐시 적중률을 높인다
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# .env.example 의 VITE_* 키와 1:1 대응. 키를 추가하면 여기에도 ARG 를 추가할 것.
# ARG 는 npm run build 보다 앞, npm ci 보다 뒤에 두어야 값이 바뀌어도 의존성 캐시가 유지된다.
#
# ⚠️ 이 목록이 .env 와 어긋나면 조용히 깨진다.
# Jenkins 는 .env 의 ^VITE_ 줄을 전부 --build-arg 로 넘기지만, 여기에 ARG 선언이 없는 키는
# Docker 가 "One or more build-args were not consumed" 경고만 내고 값을 버린다. 빌드는
# 성공하므로 번들에는 빈 문자열이 들어간다 — "값을 안 채웠을 때"와 증상이 완전히 같아서
# 구분이 안 된다.
# 이 어긋남은 Jenkinsfile 의 Docker Build 스테이지가 빌드 직전에 검사해 막는다
# (여기서는 검사할 수 없다 — 선언되지 않은 build-arg 는 빌드 안에서 보이지도 않고,
#  .env 는 빌드 컨텍스트에 들어오지 않는다).
ARG VITE_API_BASE_URL
ARG VITE_BESU_RPC_URL
ARG VITE_BESU_CHAIN_ID
ARG VITE_BESU_EXPLORER_URL
ARG VITE_MATERIAL_CONTRACT_ADDRESS
ARG VITE_MARKETPLACE_CONTRACT_ADDRESS
ARG VITE_PROCESSING_CONTRACT_ADDRESS
ARG VITE_PRODUCTION_CONTRACT_ADDRESS
# .env 의 VITE_* 자동 목록에는 없다 — test 배포 스테이지가 이 값만 별도로 넘긴다
# (운영 빌드는 안 넘기므로 빈 값 → vite.config.ts 기본값 '/' 그대로 유지된다).
ARG VITE_BASE_PATH

COPY . .
RUN npm run build

# ── 2nd stage: 정적 서빙 ──────────────────────────────────
FROM nginx:1.27-alpine
# SPA fallback(react-router)은 이 이미지 내부 nginx 가 처리한다.
# EC2 앞단 nginx 는 / → frontend 프록시만 담당한다.
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
