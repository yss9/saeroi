import { RouterProvider } from 'react-router-dom'
import { AppProviders } from './app/providers/AppProviders.tsx'
import { router } from './app/router/index.tsx'

/**
 * 앱 진입 컴포넌트.
 *
 * Provider 구성은 AppProviders, 경로 정의는 app/router 가 담당한다.
 * 이 파일은 둘을 잇기만 하며 앞으로도 이 상태를 유지한다.
 */
export default function App() {
  return (
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  )
}
