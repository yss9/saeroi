import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type UserConfig } from 'vite'
import type { InlineConfig } from 'vitest'

/**
 * `.env` 파일의 값을 **설정 파일에서도** 읽는다.
 *
 * Vite 는 `.env` 를 클라이언트 번들(`import.meta.env`)에만 넣어 주고, 설정 파일이 도는
 * Node 쪽 `process.env` 에는 넣지 않는다 — 그래서 `process.env.VITE_DEV_API_TARGET` 은
 * 언제나 undefined 였고 프록시가 조용히 기본값(로컬 백엔드)으로 갔다.
 *
 * 접두사를 비워 두면 `.env` 의 모든 값과 실제 환경변수를 함께 받는다. Jenkins 가 넘기는
 * `VITE_BASE_PATH` 도 같은 자리에서 읽힌다.
 */
const config = ({ mode }: { mode: string }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    // 서브패스(/test/)에 배포하는 test 스택을 위한 옵션. 값이 없으면 기본값 '/'이라
    // 운영 빌드는 영향 없다. Jenkinsfile의 test 배포 스테이지가 build-arg → 환경변수로
    // `VITE_BASE_PATH=/test/`를 넘긴다. Vite는 이 값으로 `import.meta.env.BASE_URL`을
    // 자동으로 채우므로(별도 커스텀 env 불필요), router(../app/router/index.tsx)가
    // 그 값을 그대로 `basename`으로 쓴다.
    base: env.VITE_BASE_PATH || '/',
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
        //
        // `VITE_DEV_API_TARGET` 으로 대상을 갈아 끼울 수 있다. 로컬 백엔드를 띄우지 않고
        // 배포된 test 스택에 붙여 볼 때 쓴다 — 그 서버는 `/test/api` 아래에 있으므로
        // 경로 앞에 그 접두사를 붙여 준다.
        //
        //   .env.local
        //   VITE_DEV_API_TARGET=https://j15e104.p.ssafy.io
        //   VITE_DEV_API_PREFIX=/test
        //
        // 기본값은 그대로 로컬 백엔드다. 값을 비워 두면 팀원들의 개발 환경이 바뀌지 않는다.
        '/api': {
          target: env.VITE_DEV_API_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path: string) =>
            env.VITE_DEV_API_PREFIX ? `${env.VITE_DEV_API_PREFIX}${path}` : path,
          //
          // ⚠️ **Origin 을 대상 주소로 바꿔 보낸다.**
          //
          // 크롬은 같은 오리진이어도 POST 에는 `Origin` 헤더를 붙인다. 프록시가 그것을
          // 그대로 넘기면 배포된 서버는 `http://localhost:5173` 을 허용 목록에 없는
          // 오리진으로 보고 403 "Invalid CORS request" 로 거절한다 — 로그인이 그렇게 막힌다.
          //
          // `changeOrigin` 은 Host 만 바꾸고 Origin 은 건드리지 않아서 이 설정이 따로 필요하다.
          // 로컬 백엔드(기본값)에는 CORS 설정 자체가 없어 영향이 없다.
          ...(env.VITE_DEV_API_TARGET
            ? { headers: { Origin: env.VITE_DEV_API_TARGET } }
            : {}),
        },
      },
    },

    test: {
      // jsdom → happy-dom: 환경 준비/해제 비용이 실제 테스트 실행보다 커서(로컬 실측
      // jsdom 297.58s vs tests 166.19s, 누적) 전체 스위트가 느렸다. happy-dom 은 더
      // 가벼운 엔진이라 같은 스위트가 약 20% 빨라진다(118.99s → 94.49s). `isolate: false`
      // (파일 간 환경 재사용)도 시도했지만 86개 파일이 상태 공유로 깨져 폐기했다 — 절대
      // 다시 켜지 말 것.
      environment: 'happy-dom',
      setupFiles: ['./src/test/setup.ts'],
    },
  } satisfies UserConfig & { test: InlineConfig }
}

// https://vite.dev/config/
export default defineConfig(config)
