import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { queryClient } from '../../services/queryClient.ts'

export interface AppProvidersProps {
  children: ReactNode
}

/**
 * 앱 전역 Provider 를 한 곳에 모은다.
 *
 * App.tsx 에 Provider 를 직접 쌓지 않는 이유 —
 * Provider 는 계속 늘어난다(Query, ErrorBoundary, Theme, i18n, Toast …).
 * App.tsx 에 쌓으면 라우팅 코드가 6겹 들여쓰기 안에 파묻히고,
 * 테스트에서 같은 조합을 다시 만들기도 어려워진다.
 *
 * 앞으로 추가할 것:
 *   TODO: ErrorBoundary — 렌더 중 예외가 흰 화면으로 끝나지 않게 한다
 *   TODO: Toast Provider — 트랜잭션 결과 알림
 *
 * Zustand 는 Provider 가 필요 없다(모듈 스코프 스토어). 그래서 여기에 없다.
 */
export function AppProviders({ children }: AppProvidersProps) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
