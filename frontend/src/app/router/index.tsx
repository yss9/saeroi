import { createBrowserRouter } from 'react-router-dom'
import { routes } from './routes.tsx'

// BASE_URL은 vite.config.ts의 base를 그대로 반영한다 (운영은 '/', test 스택은 '/test/').
// 이게 없으면 test 스택(/test/)에서 내부 링크 이동 시 basename 없는 절대경로로 튀어 깨진다.
export const router = createBrowserRouter(routes, { basename: import.meta.env.BASE_URL })
export { routes }
