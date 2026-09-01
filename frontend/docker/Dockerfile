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
ARG VITE_API_BASE_URL
ARG VITE_BESU_RPC_URL
ARG VITE_BESU_CHAIN_ID
ARG VITE_MATERIAL_TOKEN_ADDRESS
ARG VITE_MARKETPLACE_ADDRESS
ARG VITE_ESCROW_ADDRESS

COPY . .
RUN npm run build

# ── 2nd stage: 정적 서빙 ──────────────────────────────────
FROM nginx:1.27-alpine
# SPA fallback(react-router)은 이 이미지 내부 nginx 가 처리한다.
# EC2 앞단 nginx 는 / → frontend 프록시만 담당한다.
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
