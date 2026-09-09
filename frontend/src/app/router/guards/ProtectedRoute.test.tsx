import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { useAuthStore } from '../../../stores/authStore.ts'
import type { UserRole } from '../../../types/role.types.ts'
import { ProtectedRoute } from './ProtectedRoute.tsx'

/**
 * <b>보호 라우트가 셸을 씌우는가.</b>
 *
 * 메뉴는 원래 화면마다 넣는 구조였고, 그래서 실제로 두 곳
 * ({@code CompanyAccountPage} · {@code ProcessorProfilePage})에만 붙어 있었다. 나머지
 * 14개 보호 화면 — 보유 소재 · 마켓플레이스 · 거래 목록 · 판매 관리 등 — 에는 메뉴가
 * 없어서, 한 번 대시보드를 벗어나면 뒤로 가기나 주소 직접 입력 말고는 돌아갈 길이 없었다.
 *
 * 새 화면이 생길 때마다 빠뜨리는 구조였으므로 게이트 한 곳에서 씌우기로 했다.
 * 이 테스트가 그 결정을 고정한다 — 누가 다시 화면별로 옮기면 여기서 깨진다.
 */
function setUser(role: UserRole) {
  useAuthStore.setState({
    user: { id: 7, name: '새로이 기업', email: 'company@saeroi.test', role, walletAddress: null },
    accessToken: 'test-token',
    isAuthenticated: true,
  })
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path={path} element={<h1>보호된 본문</h1>} />
          </Route>
          <Route path="/login" element={<h1>로그인</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: [] } as AxiosResponse)
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  vi.restoreAllMocks()
})

describe('ProtectedRoute', () => {
  it('로그인하지 않았으면 본문 대신 로그인으로 보낸다', () => {
    renderAt('/inventory')

    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '보호된 본문' })).not.toBeInTheDocument()
  })

  it('보유 소재처럼 대시보드 밖 화면에도 메뉴를 씌운다', () => {
    setUser('SUPPLIER')
    renderAt('/inventory')

    expect(screen.getByRole('heading', { name: '보호된 본문' })).toBeInTheDocument()
    // 셸이 씌워졌다는 증거. 이 링크가 없으면 사용자는 이 화면에서 나갈 방법이 없다.
    expect(screen.getByRole('link', { name: /판매 관리/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /거래 내역/ })).toBeInTheDocument()
  })

  it('같은 곳으로 가는 항목을 두 번 그리지 않는다', () => {
    // 사업자 메뉴와 계정 메뉴가 /company/account 를 함께 들고 있다 — 헤더 팝오버가
    // "내 홈" 후보로 쓰기 때문이고, 겹치는 것 자체는 의도한 것이다. 다만 사이드바에서
    // 그대로 이어 붙이면 같은 to 가 두 번 들어가 React 가 key 중복을 경고하고
    // "회사 정보"·"회사 대시보드" 가 나란히 보인다.
    setUser('SUPPLIER')
    renderAt('/inventory')

    const navigation = screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })
    const toAccount = within(navigation)
      .getAllByRole('link')
      .filter((link) => link.getAttribute('href') === '/company/account')
    expect(toAccount).toHaveLength(1)
  })

  it('소비자에게는 사업자 메뉴가 아니라 마이페이지만 보인다', () => {
    // 메뉴를 합쳐 놓았으므로 역할 필터가 실제로 도는지 확인해야 한다 —
    // 안 돌면 소비자가 판매 관리 링크를 보게 된다.
    setUser('CONSUMER')
    renderAt('/mypage')

    expect(screen.getByRole('link', { name: /마이페이지/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /판매 관리/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /소재 등록/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /지갑/ })).not.toBeInTheDocument()
  })
})
