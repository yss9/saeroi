import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { useAuthStore } from '../../../stores/authStore.ts'
import type { UserRole } from '../../../types/role.types.ts'
import { companyKeys } from '../api/companyKeys.ts'
import type { CompanyProfile } from '../api/getCompanyProfile.ts'
import { CompanyDashboardLayout } from './CompanyDashboardLayout.tsx'

const approvedProfile: CompanyProfile = {
  userId: 7,
  companyName: '새로이 기업',
  businessNumber: '123-45-67890',
  representativeName: '김대표',
  companyPhone: '010-1234-5678',
  zipCode: '06236',
  address: '서울시 강남구',
  addressDetail: null,
  introduction: null,
  logoUrl: null,
  role: 'BRAND',
  status: 'ACTIVE',
  roleApprovalStatus: 'APPROVED',
}

function setBusinessUser(role: UserRole) {
  useAuthStore.setState({
    user: {
      id: 7,
      name: '새로이 기업',
      email: 'company@saeroi.test',
      role,
      walletAddress: null,
    },
    accessToken: 'test-token',
    isAuthenticated: true,
  })
}

function renderLayout(path = '/company/account', profile: CompanyProfile = approvedProfile) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  queryClient.setQueryData(companyKeys.me(), profile)
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <CompanyDashboardLayout><h1>회사 정보 본문</h1></CompanyDashboardLayout>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** 사이드바에 붙은 묶음 이름들. 이름 자리가 곧 접기 버튼이다. */
function groupLabels(nav: HTMLElement): string[] {
  return [...nav.querySelectorAll('.company-dashboard__nav-group-label')].map(
    (node) => node.textContent?.trim() ?? '',
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: [] } as AxiosResponse)
  vi.spyOn(apiClient, 'post').mockResolvedValue({ data: null } as AxiosResponse)
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  vi.restoreAllMocks()
})

describe('CompanyDashboardLayout', () => {
  it('승인 대기 사업자의 업무 화면을 먼저 렌더링하지 않고 회사 정보로 보낸다', async () => {
    setBusinessUser('BRAND')
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
    queryClient.setQueryData(companyKeys.me(), { ...approvedProfile, roleApprovalStatus: 'PENDING' })

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/brand/processing']}>
          <Routes>
            <Route path="/brand/processing" element={<CompanyDashboardLayout><h1>제한된 업무 화면</h1></CompanyDashboardLayout>} />
            <Route path="/company/account" element={<CompanyDashboardLayout><h1>회사 정보 본문</h1></CompanyDashboardLayout>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.queryByRole('heading', { name: '제한된 업무 화면' })).not.toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '회사 정보 본문' })).toBeInTheDocument()
    expect(screen.getByText('사업자 가입 승인 대기 중입니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /알림/ })).not.toBeInTheDocument()
  })

  // 소비자에게 이 사이드바는 거의 계정 메뉴다. 마이페이지가 목록 끝에 붙어 있으면
  // 자기 계정으로 돌아가는 길이 숨는다.
  // data-role 은 역할 테마를 고르는 유일한 고리다(company-dashboard.css). 이게 빠지면
  // 화면이 깨지지 않고 조용히 기본 테마로 돌아가므로 눈으로는 못 잡는다.
  it.each([['SUPPLIER'], ['PROCESSOR'], ['BRAND']] as const)(
    '%s 셸에 역할 테마를 고를 data-role 을 남긴다',
    (role) => {
      setBusinessUser(role)
      const { container } = renderLayout()

      expect(container.querySelector('.company-dashboard')).toHaveAttribute('data-role', role)
    },
  )

  it('소비자에게는 사업자 역할 테마를 씌우지 않는다', () => {
    setBusinessUser('CONSUMER')
    const { container } = renderLayout()

    expect(container.querySelector('.company-dashboard')).toHaveAttribute('data-role', 'CONSUMER')
  })

  it('계정 메뉴를 사이드바 맨 위에 둔다', () => {
    setBusinessUser('CONSUMER')
    renderLayout()

    const nav = screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })
    const labels = within(nav)
      .getAllByRole('link')
      .map((link) => link.textContent?.trim())

    expect(labels[0]).toBe('마이페이지')
  })

  it('관리자 사이드바에서 세 관리자 기능으로 바로 이동한다', () => {
    setBusinessUser('ADMIN')
    renderLayout('/admin/company-role-applications')

    const nav = screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })
    expect(within(nav).queryByRole('link', { name: '관리자 페이지' })).not.toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: '사업자 가입 승인' })).toHaveAttribute(
      'href',
      '/admin/company-role-applications',
    )
    expect(within(nav).getByRole('link', { name: '예측시장 개설' })).toHaveAttribute(
      'href',
      '/admin/prediction-markets',
    )
    expect(within(nav).getByRole('link', { name: '블록체인 검증' })).toHaveAttribute(
      'href',
      '/admin/blockchain',
    )
  })

  it('renders role-specific navigation without duplicating other roles', () => {
    setBusinessUser('SUPPLIER')
    renderLayout()

    // 회사 정보는 묶음 밖이라 언제나 보인다.
    expect(screen.getByRole('link', { name: '회사 정보' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '회사 대시보드' })).not.toBeInTheDocument()

    expect(screen.getByRole('link', { name: '소재 등록' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '판매 관리' })).toBeInTheDocument()
    // 라우트(/supplier/trades)는 있는데 예전 사이드바에는 빠져 있던 항목.
    // 이제 메뉴가 routes.tsx 정의에서 파생되므로 자동으로 따라온다.
    expect(screen.getByRole('link', { name: '판매 거래' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '가공 요청' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '제품 등록' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '회사 정보 본문' })).toBeInTheDocument()
  })

  it('PROCESSOR 에게는 가공 작업 메뉴가 보이고 공급자 메뉴가 보이지 않는다', () => {
    setBusinessUser('PROCESSOR')
    renderLayout()

    expect(screen.getByRole('link', { name: '가공 작업' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '소재 등록' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '판매 관리' })).not.toBeInTheDocument()
  })

  // 역할이 아직 없을 때 공통 메뉴만 그려 두면 역할이 도착하는 순간 메뉴가 번쩍 늘어난다.
  it('역할 판정 중에는 메뉴 링크를 그리지 않고 aria-busy 로 알린다', () => {
    useAuthStore.setState({ user: null, accessToken: 'test-token', isAuthenticated: true })
    renderLayout()

    const nav = screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })
    expect(nav).toHaveAttribute('aria-busy', 'true')
    expect(nav.querySelectorAll('a')).toHaveLength(0)
  })

  it('signs out and clears the authenticated session', async () => {
    const user = userEvent.setup()
    setBusinessUser('BRAND')
    renderLayout()

    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    await waitFor(() => expect(useAuthStore.getState().isAuthenticated).toBe(false))
    expect(useAuthStore.getState().user).toBeNull()
    expect(apiClient.post).toHaveBeenCalledWith('/v1/auth/logout', undefined, {
      skipAuth: true,
      skipTokenRefresh: true,
    })
  })

  // 소재 검색 화면 오른쪽 아래에만 떠 있어서, 다른 화면에서는 어시스턴트를 부를 길이 없었다.
  it('브랜드 머리말의 쇼핑몰 바로 왼쪽에 새르파 AI 버튼을 둔다', () => {
    setBusinessUser('BRAND')
    renderLayout()

    const shop = screen.getByRole('link', { name: '쇼핑몰' })
    const agent = screen.getByRole('button', { name: '새르파 AI' })
    expect(agent.nextElementSibling).toBe(shop)
  })

  it.each([['SUPPLIER'], ['PROCESSOR'], ['CONSUMER']] as const)(
    '%s 머리말에는 새르파 AI 버튼이 없다',
    (role) => {
      setBusinessUser(role)
      renderLayout()

      expect(screen.queryByRole('button', { name: /새르파 AI/ })).not.toBeInTheDocument()
    },
  )

  it('승인 전 브랜드에게는 새르파 AI 버튼을 보이지 않는다', () => {
    setBusinessUser('BRAND')
    renderLayout('/company/account', { ...approvedProfile, roleApprovalStatus: 'PENDING' })

    expect(screen.queryByRole('button', { name: /새르파 AI/ })).not.toBeInTheDocument()
  })

  it('shows the persistent wallet status beside the business account shortcut', async () => {
    const user = userEvent.setup()
    setBusinessUser('BRAND')
    renderLayout()

    expect(screen.getByRole('link', { name: '마이페이지' })).toHaveAttribute('href', '/company/account')
    const walletButton = await screen.findByRole('button', { name: '지갑 연결 안 됨' })

    await user.click(walletButton)

    expect(screen.getByRole('region', { name: '지갑 연결 상태' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '연결하러 가기' })).toHaveAttribute(
      'href',
      '/company/account#company-wallet',
    )
  })

  it('does not expose the wallet address in the registered header popover', async () => {
    const address = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [{
        id: 1,
        walletAddress: address,
        chainId: 1337,
        active: true,
        verified: true,
        verifiedAt: null,
        createdAt: '2026-09-09T00:00:00',
        deactivatedAt: null,
      }],
    } as AxiosResponse)
    const user = userEvent.setup()
    setBusinessUser('SUPPLIER')
    renderLayout()

    await user.click(await screen.findByRole('button', { name: '지갑 연결됨' }))

    expect(screen.getByRole('link', { name: '지갑 관리' })).toBeInTheDocument()
    expect(screen.queryByText(address)).not.toBeInTheDocument()
    expect(screen.queryByText(/0xd8dA/)).not.toBeInTheDocument()
  })

  it('Brand 메뉴에서 가공 요청과 요청 내역을 구분해 이동한다', () => {
    setBusinessUser('BRAND')
    renderLayout()

    expect(screen.getByRole('link', { name: '가공 요청' })).toHaveAttribute('href', '/brand/processing')
    expect(screen.getByRole('link', { name: '요청 내역' })).toHaveAttribute('href', '/brand/processing/requests')
  })

  // 브랜드가 하는 일은 크게 셋이다 — 소재를 사고, 가공을 맡기고, 제품을 판다.
  // 11줄짜리 평평한 목록으로는 그 구조가 드러나지 않아 매번 전체를 훑어야 했다.
  it('브랜드 사이드바를 소재·가공·제품 세 묶음으로 보여준다', () => {
    setBusinessUser('BRAND')
    renderLayout()

    const nav = screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })
    expect(groupLabels(nav)).toEqual(['소재', '가공', '제품'])
    // 회사 정보는 묶지 않는다. 항목이 하나뿐이라 이름을 얹을 이유가 없다.
    expect(within(nav).getByRole('link', { name: '회사 정보' })).toBeInTheDocument()
  })

  // 처음부터 접혀 있으면 어떤 항목이 있는지 모르는 사용자가 묶음 이름만 보고 짐작해야 한다.
  it('처음에는 모든 묶음이 펼쳐져 있다', () => {
    setBusinessUser('BRAND')
    renderLayout()

    expect(screen.getByRole('link', { name: '보유 소재' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '가공 요청' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '내 제품' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '예측·선호 분석' })).toHaveAttribute('href', '/brand/predictions')
  })

  // 이 파일에서 가장 중요한 테스트다. 예전 아코디언은 다른 묶음을 열면 보던 묶음이
  // 저절로 닫혀, 방금 자리로 돌아가려면 매번 한 번 더 눌러야 했다.
  it('한 묶음을 접어도 다른 묶음은 건드리지 않는다', async () => {
    const user = userEvent.setup()
    setBusinessUser('BRAND')
    renderLayout()

    await user.click(screen.getByRole('button', { name: '소재' }))

    expect(screen.queryByRole('link', { name: '보유 소재' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '가공 요청' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '내 제품' })).toBeInTheDocument()

    // 같은 이름을 다시 누르면 되돌아온다.
    await user.click(screen.getByRole('button', { name: '소재' }))
    expect(screen.getByRole('link', { name: '보유 소재' })).toBeInTheDocument()
  })

  // 새로고침마다 다시 접게 하면 접어 둔 뜻이 사라진다.
  it('접어 둔 묶음을 다시 열어도 기억한다', async () => {
    const user = userEvent.setup()
    setBusinessUser('BRAND')
    const first = renderLayout()

    await user.click(screen.getByRole('button', { name: '가공' }))
    first.unmount()

    renderLayout()
    expect(screen.queryByRole('link', { name: '가공 요청' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '보유 소재' })).toBeInTheDocument()
  })

  // 항목이 하나도 없는 묶음을 남기면 눌러도 아무것도 없는 이름이 사이드바에 남는다.
  it('그 역할에게 항목이 없는 묶음은 그리지 않는다', () => {
    setBusinessUser('SUPPLIER')
    renderLayout()

    expect(groupLabels(screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' }))).toEqual([
      '소재',
    ])
  })

  it('소비자에게는 사업자 알림 센터를 표시하지 않는다', () => {
    setBusinessUser('CONSUMER')
    renderLayout()

    expect(screen.queryByRole('button', { name: /알림/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '새 알림 없음' })).not.toBeInTheDocument()
  })
})
