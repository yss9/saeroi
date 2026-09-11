import react from '@vitejs/plugin-react'
import { defineConfig, type UserConfig } from 'vite'
import type { InlineConfig } from 'vitest'

const config = {
  // 서브패스(/test/)에 배포하는 test 스택을 위한 옵션. 값이 없으면 기본값 '/'이라
  // 운영 빌드는 영향 없다. Jenkinsfile의 test 배포 스테이지가 build-arg → 환경변수로
  // `VITE_BASE_PATH=/test/`를 넘긴다. Vite는 이 값으로 `import.meta.env.BASE_URL`을
  // 자동으로 채우므로(별도 커스텀 env 불필요), router(../app/router/index.tsx)가
  // 그 값을 그대로 `basename`으로 쓴다.
  base: process.env.VITE_BASE_PATH || '/',
  plugins: [react()],

  server: {
    proxy: {
      // 개발 중 `/api` 요청을 로컬 백엔드로 넘긴다.
      //
      // 왜 프록시인가 — apiClient 의 baseURL 이 '/api' 라 그냥 두면 요청이
      // vite 서버(:5173)로 가서 404 가 난다. 그렇다고 VITE_API_BASE_URL 을
      // http://localhost:8080 으로 바꾸면 오리진이 달라져 CORS 에 막힌다
      // (백엔드에 CORS 설정이 없다).
      //
      // 프록시를 쓰면 브라우저 입장에서는 같은 오리진이라 CORS 가 아예 발생하지 않고,
      // **운영 구조와도 같아진다** — nginx 가 같은 오리진의 /api/ 를 backend 로 넘긴다.
      // 즉 개발과 운영에서 프론트 코드가 동일하게 동작한다.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },

  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
} satisfies UserConfig & { test: InlineConfig }

// https://vite.dev/config/
export default defineConfig(config)
