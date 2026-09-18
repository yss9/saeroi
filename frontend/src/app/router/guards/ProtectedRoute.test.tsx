import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { ShopProvider } from '../../../features/shop/ShopContext.tsx'
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
        <ShopProvider>
          <Routes>
            <Route element={<ProtectedRoute />}>
              <Route path={path} element={<h1>보호된 본문</h1>} />
            </Route>
            <Route path="/login" element={<h1>로그인</h1>} />
          </Routes>
        </ShopProvider>
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
    expect(screen.getByRole('link', { name: /판매 거래/ })).toBeInTheDocument()
    // 구매 내역은 BRAND 전용이라 SUPPLIER 에게는 없다. 셸이 씌워졌는지는 위의 두 링크로
    // 이미 확인된다 — 없어야 할 링크가 여기 남아 있으면 셸 검사가 메뉴 정책과 엉킨다.
    expect(screen.queryByRole('link', { name: /구매 내역/ })).not.toBeInTheDocument()
  })

  it('같은 곳으로 가는 항목을 두 번 그리지 않는다', () => {
    // 사업자 메뉴와 계정 메뉴가 /company/account 를 함께 들고 있다 — 헤더 팝오버가
    // "내 홈" 후보로 쓰기 때문이고, 겹치는 것 자체는 의도한 것이다. 사이드바에서 그대로
    // 이어 붙이면 같은 to 가 두 번 들어가 React 가 key 중복을 경고하고 "회사 정보" 가
    // 나란히 두 번 보인다.
    setUser('SUPPLIER')
    renderAt('/inventory')

    const navigation = screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })
    const toAccount = within(navigation)
      .getAllByRole('link')
      .filter((link) => link.getAttribute('href') === '/company/account')
    expect(toAccount).toHaveLength(1)
  })

  // 소비자가 이 가드 안에서 닿는 곳은 제품 목록과 상세뿐이다. 거기에 사업자 사이드바를
  // 붙이면 옷을 고르는 화면에 회사 정보·보유 소재 같은 남의 업무 메뉴가 따라붙는다.
  it('소비자에게는 사업자 셸 대신 쇼핑 껍데기를 씌운다', () => {
    setUser('CONSUMER')
    renderAt('/business/products')

    expect(screen.getByRole('heading', { name: '보호된 본문' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: '사업자 대시보드 메뉴' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /판매 관리/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /지갑/ })).not.toBeInTheDocument()
    // 나갈 길은 남는다 — 머리말의 쇼핑 메뉴가 그 역할을 한다.
    const navigation = screen.getByRole('navigation', { name: '주요 메뉴' })
    expect(within(navigation).getByRole('link', { name: 'SHOP' })).toHaveAttribute('href', '/shop')
  })
})
