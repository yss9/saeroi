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
    status: 'QUOTING',
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
 * 작업 상태가 상태 칸을 대신 말하기 때문이다(견적 수락 뒤로 요청 상태는 움직이지 않는다).
 * 그래서 mock 도 주소를 보고 답한다.
 */
function mockApi(
  requests: (url: string, config?: { params?: Record<string, unknown> }) => unknown,
  jobs: unknown[] = [],
  quotes: unknown[] = [],
) {
  vi.mocked(apiClient.get).mockImplementation((url, config) => {
    const path = String(url)
    if (path === '/v1/companies/me') {
      return Promise.resolve({ data: { companyName: '새로이 브랜드', roleApprovalStatus: 'APPROVED' } })
    }
    if (path.includes('/processing-jobs')) return Promise.resolve({ data: jobs })
    // 견적 대기 줄마다 "견적이 왔는가" 를 따로 묻는다(목록 응답에 견적 수가 없다).
    if (path.includes('/quotes')) return Promise.resolve({ data: quotes })
    return Promise.resolve({
      data: requests(path, config as { params?: Record<string, unknown> }),
    })
  })
}

/** 목록 조회만 골라 본다 — 견적·작업 조회가 뒤따라 나가 "마지막 호출" 이 목록이 아니다. */
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
  expect(screen.getAllByText('견적 대기').length).toBeGreaterThan(0)
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

// 견적을 수락하면 요청 상태는 SELECTED 에서 멈춘다 — 그 뒤의 일은 작업이 말한다.
// 목록이 요청 상태만 보여주면 가공이 끝났는데도 "견적 수락" 이라고 적혀 있다.
test('작업이 시작된 요청은 가공 진행 상태를 보여준다', async () => {
  mockApi(
    () => ({ ...response, items: [{ ...response.items[0], status: 'SELECTED' }] }),
    [{ jobId: 40, requestId: 17, status: 'RETURN_SHIPPED' }],
  )
  renderPage()

  // 현황 카드와 필터 버튼에도 같은 말이 있어 목록 안으로 좁힌다.
  const table = await screen.findByRole('table', { name: '내 가공 요청 목록' })
  expect(within(table).getByText('수령 확인 대기')).toBeInTheDocument()
  expect(within(table).queryByText('견적 수락')).not.toBeInTheDocument()
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
// 견적이 온 요청은 "내 차례" 다 — 고르라는 뜻이기 때문이다.
test('내 차례를 누르면 견적이 온 요청만 남는다', async () => {
  const user = userEvent.setup()
  mockApi(() => response, [], [{ quoteId: 1 }])
  renderPage()
  await screen.findByText('폐데님 원단')

  await user.click(screen.getByRole('button', { name: /내 차례/ }))

  const table = await screen.findByRole('table', { name: '내 가공 요청 목록' })
  expect(within(table).getByText('견적 1건 도착')).toBeInTheDocument()
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

// 견적을 기다리는 동안 상태는 계속 "견적 대기" 라, 가공사가 견적을 보냈는지 알려면 요청을
// 하나씩 열어 봐야 했다. 목록 응답에 견적 수가 없어 대기 건만 따로 물어 센다.
test('견적이 도착한 요청은 목록에서 그 사실을 말한다', async () => {
  mockApi(() => response, [], [{ quoteId: 1 }, { quoteId: 2 }])
  renderPage()

  const table = await screen.findByRole('table', { name: '내 가공 요청 목록' })
  expect(await within(table).findByText('견적 2건 도착')).toBeInTheDocument()
  expect(within(table).queryByText('견적 대기')).not.toBeInTheDocument()
})

// 견적이 오지 않은 요청은 기다리는 중이다 — 내 차례가 아니라 진행 중에 들어간다.
test('견적이 없으면 내 차례가 비고 진행 중에 남는다', async () => {
  const user = userEvent.setup()
  mockApi(() => response, [], [])
  renderPage()
  await screen.findByText('폐데님 원단')

  await user.click(screen.getByRole('button', { name: /내 차례/ }))
  expect(await screen.findByText('지금 손댈 요청이 없어요')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: /진행 중/ }))
  const table = await screen.findByRole('table', { name: '내 가공 요청 목록' })
  expect(within(table).getByText('견적 대기')).toBeInTheDocument()
})
