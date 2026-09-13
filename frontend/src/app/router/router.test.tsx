import { cleanup, render, screen } from '@testing-library/react'
import { createMemoryRouter, MemoryRouter, RouterProvider, useRoutes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/authStore.ts'
import type { UserRole } from '../../types/role.types.ts'
import { AppProviders } from '../providers/AppProviders.tsx'
import { routes } from './routes.tsx'

/**
 * 가드 리다이렉트를 확인하는 테스트는 데이터 라우터를 쓸 수 없다 —
 * createMemoryRouter 는 이동할 때 내부적으로 Request 를 만들고, jsdom 의
 * AbortSignal 이 undici 와 맞지 않아 실패한다 (RoleGuard.test.tsx 주석 참고).
 * 대신 같은 `routes` 배열을 useRoutes 로 컴포넌트 라우터에 올려 실제 정의를 검증한다.
 */
function AppRoutes() {
  return useRoutes(routes)
}

// LoginPage 처럼 useMutation 을 쓰는 화면이 리다이렉트 대상이라 Provider 가 필요하다.
function renderRoute(path: string) {
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AppProviders>,
  )
}

function signInAs(role: UserRole) {
  useAuthStore
    .getState()
    .signIn(
      { id: 1, email: 'a@b.c', name: '테스터', role, walletAddress: null },
      'test-token',
      'refresh-token',
    )
}

beforeEach(() => {
  localStorage.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  localStorage.clear()
})

describe('application router', () => {
  it('renders the SAEROI landing page at the root route', () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/'] })
    render(<RouterProvider router={router} />)

    expect(screen.getByRole('heading', { name: '지속 가능한 가치를 만드는 브랜드' })).toBeInTheDocument()
  })

  // 라우트 정의에 실제로 가드가 걸려 있는지 확인한다. RoleGuard 단위 테스트는
  // 가드 자체만 보므로, routes.tsx 에서 중첩을 빠뜨려도 잡지 못한다.
  it('로그인한 다른 Role 이 Role 전용 경로에 들어가면 권한 없음 화면을 보여 준다', () => {
    signInAs('CONSUMER')
    renderRoute('/admin')

    expect(screen.getByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument()
  })

  // 미인증은 권한 없음과 다르게 로그인으로 보낸다. 둘이 합쳐지면
  // 로그인한 사용자가 로그인 화면을 다시 보는 상태로 되돌아간다.
  it('로그인하지 않고 Role 전용 경로에 들어가면 로그인 화면으로 보낸다', () => {
    renderRoute('/admin')

    expect(screen.queryByRole('heading', { name: '접근 권한이 없습니다' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
  })

  // /403 자체가 가드에 막히면 사용자가 갈 곳이 없어진다.
  it('권한 없음 화면은 로그인 없이도 열린다', () => {
    renderRoute('/403')

    expect(screen.getByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument()
  })

  it('Brand가 자신의 가공 요청 내역 경로에 접근한다', () => {
    signInAs('BRAND')
    renderRoute('/brand/processing/requests')

    expect(screen.getByRole('heading', { name: '가공 요청 내역' })).toBeInTheDocument()
  })

  it.each(['/brand/processing', '/brand/processing/requests', '/brand/production'])(
    '%s에서 공통 Header와 Sidebar를 한 번만 렌더링한다',
    (path) => {
      signInAs('BRAND')
      const { container } = renderRoute(path)

      expect(container.querySelectorAll('.company-dashboard__header')).toHaveLength(1)
      expect(container.querySelectorAll('.company-dashboard__sidebar')).toHaveLength(1)
    },
  )
})
