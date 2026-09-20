import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { companyKeys } from '../../features/company/api/companyKeys.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { CompanyDashboardLayout } from '../../features/company/components/CompanyDashboardLayout.tsx'
import { BrandProcessingRequestListPage } from './BrandProcessingRequestListPage.tsx'

vi.mock('../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn() } }))

const response = {
  items: [{
    requestId: 17,
    requestCode: 'PR-017',
    brandId: 3,
    brandName: '새로이 브랜드',
    processorId: 8,
    processorName: '리뉴 가공소',
    materialId: 5,
    materialCode: 'MAT-005',
    materialName: '폐데님 원단',
    materialType: 'DENIM',
    quantity: 12_500,
    processTypes: ['CLEANING', 'CUTTING'],
    status: 'REQUESTED',
    createdAt: '2026-09-09T01:00:00',
    expiresAt: '2026-09-16T01:00:00',
  }],
  total: 1,
  page: 0,
  size: 20,
}

/**
 * 목록 화면은 두 곳에 묻는다 — 요청 목록과 **내 가공 작업**.
 *
 * 작업 상태가 상태 칸을 대신 말하기 때문이다(가공사가 수락한 뒤로 요청 상태는 움직이지 않는다).
 * 그래서 mock 도 주소를 보고 답한다.
 */
function mockApi(
  requests: (url: string, config?: { params?: Record<string, unknown> }) => unknown,
  jobs: unknown[] = [],
) {
  vi.mocked(apiClient.get).mockImplementation((url, config) => {
    const path = String(url)
    if (path === '/v1/companies/me') {
      return Promise.resolve({ data: { companyName: '새로이 브랜드', roleApprovalStatus: 'APPROVED' } })
    }
    if (path.includes('/processing-jobs')) return Promise.resolve({ data: jobs })
    return Promise.resolve({
      data: requests(path, config as { params?: Record<string, unknown> }),
    })
  })
}

/** 목록 조회만 골라 본다 — 작업 조회가 뒤따라 나가 "마지막 호출" 이 목록이 아니다. */
function lastRequestListCall() {
  return vi
    .mocked(apiClient.get)
    .mock.calls.filter(([url]) => String(url) === '/v1/processing-requests')
    .at(-1)
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  queryClient.setQueryData(companyKeys.me(), { companyName: '새로이 브랜드', roleApprovalStatus: 'APPROVED' })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/brand/processing/requests']}>
        <CompanyDashboardLayout>
          <BrandProcessingRequestListPage />
        </CompanyDashboardLayout>
        <output aria-label="현재 경로">{<LocationPath />}</output>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function LocationPath() {
  return useLocation().pathname
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.setState({
    user: { id: 3, email: 'brand@test.com', name: '새로이 브랜드', role: 'BRAND', walletAddress: null },
    accessToken: 'token',
    isAuthenticated: true,
  })
  mockApi(() => response)
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

test('Brand가 자신이 보낸 가공 요청과 지정 가공사를 확인한다', async () => {
  renderPage()

  expect(await screen.findByText('폐데님 원단')).toBeInTheDocument()
  expect(screen.getByText('리뉴 가공소')).toBeInTheDocument()
  expect(screen.getByText('12,500 g')).toBeInTheDocument()
  // 가공 종류는 소재와 다른 칸에서 낱말로 끊어 보여준다.
  expect(screen.getByText('세척')).toBeInTheDocument()
  expect(screen.getByText('재단')).toBeInTheDocument()
  expect(screen.getAllByText('결제 대기').length).toBeGreaterThan(0)
  expect(screen.getByRole('link', { name: '요청 내역' })).toHaveClass('company-dashboard__nav-link--active')
  expect(screen.getByRole('link', { name: '가공 요청' })).not.toHaveClass('company-dashboard__nav-link--active')
  expect(screen.getByRole('link', { name: '새 가공 신청' })).toHaveAttribute('href', '/brand/processing')
  // 상세로 가는 칸은 화살표 하나다. 글자가 없으니 이름은 aria-label 이 맡는다.
  expect(screen.getByRole('link', { name: '폐데님 원단 요청 상세 보기' })).toHaveAttribute(
    'href',
    '/brand/processing/requests/17',
  )
  expect(apiClient.get).toHaveBeenCalledWith('/v1/processing-requests', {
    params: { page: 0, size: 20 },
    signal: expect.any(AbortSignal),
  })
})

// 가공사가 수락하면 요청 상태는 ACCEPTED 에서 멈춘다 — 그 뒤의 일은 작업이 말한다.
// 목록이 요청 상태만 보여주면 가공이 끝났는데도 "가공사 수락" 이라고 적혀 있다.
test('작업이 시작된 요청은 가공 진행 상태를 보여준다', async () => {
  mockApi(
    () => ({ ...response, items: [{ ...response.items[0], status: 'ACCEPTED' }] }),
    [{ jobId: 40, requestId: 17, status: 'RETURN_SHIPPED' }],
  )
  renderPage()

  // 필터 버튼에도 같은 말이 있어 목록 안으로 좁힌다.
  const table = await screen.findByRole('table', { name: '내 가공 요청 목록' })
  expect(within(table).getByText('수령 확인 대기')).toBeInTheDocument()
  expect(within(table).queryByText('가공사 수락')).not.toBeInTheDocument()
})

test('가공 요청 행의 빈 영역을 클릭하면 상세로 이동한다', async () => {
  const user = userEvent.setup()
  renderPage()

  const requestRow = (await screen.findByText('폐데님 원단')).closest('article')
  if (requestRow === null) throw new Error('가공 요청 행을 찾을 수 없습니다.')
  await user.click(requestRow)

  expect(screen.getByLabelText('현재 경로')).toHaveTextContent('/brand/processing/requests/17')
})

// 묶음(내 차례·진행 중…)은 서버가 모르는 축이라 목록은 늘 전체를 받아 와 화면에서 거른다.
// 결제가 남은 요청은 브랜드 차례다 — 결제해야 가공사에게 넘어간다.
test('내 차례를 누르면 결제가 남은 요청이 남는다', async () => {
  const user = userEvent.setup()
  mockApi(() => response, [{ jobId: 40, requestId: 17, status: 'CREATED' }])
  renderPage()
  await screen.findByText('폐데님 원단')

  await user.click(screen.getByRole('button', { name: /내 차례/ }))

  const table = await screen.findByRole('table', { name: '내 가공 요청 목록' })
  expect(within(table).getByText('결제 대기')).toBeInTheDocument()
})

// 결제한 뒤에는 가공사가 답할 차례다 — 브랜드가 할 일이 없다.
test('가공사 수락을 기다리는 요청은 진행 중에 있다', async () => {
  const user = userEvent.setup()
  mockApi(() => response, [{ jobId: 40, requestId: 17, status: 'PAID' }])
  renderPage()
  await screen.findByText('폐데님 원단')

  await user.click(screen.getByRole('button', { name: /진행 중/ }))

  const table = await screen.findByRole('table', { name: '내 가공 요청 목록' })
  expect(within(table).getByText('수락 대기')).toBeInTheDocument()
})

test('요청이 없으면 새 신청으로 이동할 수 있는 빈 상태를 표시한다', async () => {
  mockApi(() => ({ items: [], total: 0, page: 0, size: 20 }))
  renderPage()

  expect(await screen.findByText('아직 생성한 가공 요청이 없어요')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '첫 가공 신청하기' })).toHaveAttribute('href', '/brand/processing')
})

test('다음 페이지로 이동하면 같은 필터로 후속 목록을 조회한다', async () => {
  mockApi(() => ({ ...response, total: 41 }))
  const user = userEvent.setup()
  renderPage()
  await screen.findByText('폐데님 원단')

  await user.click(screen.getByRole('button', { name: '다음 페이지' }))

  expect(lastRequestListCall()).toEqual([
    '/v1/processing-requests',
    { params: { page: 1, size: 20 }, signal: expect.any(AbortSignal) },
  ])
})
