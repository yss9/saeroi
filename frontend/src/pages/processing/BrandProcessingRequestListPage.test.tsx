import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
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
) {
  vi.mocked(apiClient.get).mockImplementation((url, config) =>
    String(url).includes('/processing-jobs')
      ? Promise.resolve({ data: jobs })
      : Promise.resolve({ data: requests(String(url), config as { params?: Record<string, unknown> }) }),
  )
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
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

test('상태 필터를 바꾸면 첫 페이지부터 다시 조회한다', async () => {
  const user = userEvent.setup()
  renderPage()
  await screen.findByText('폐데님 원단')

  await user.click(screen.getByRole('button', { name: '견적 수락' }))

  expect(apiClient.get).toHaveBeenLastCalledWith('/v1/processing-requests', {
    params: { page: 0, size: 20, status: 'SELECTED' },
    signal: expect.any(AbortSignal),
  })
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

  expect(apiClient.get).toHaveBeenLastCalledWith('/v1/processing-requests', {
    params: { page: 1, size: 20 },
    signal: expect.any(AbortSignal),
  })
})

// 아래 필터를 누르면 목록만 걸러져야 한다. 예전에는 위의 "전체 요청" 까지 걸러진 개수로
// 바뀌어, 견적 대기를 누르면 전체가 0 으로 떨어지는 것처럼 보였다.
test('상태 필터를 눌러도 위의 요청 현황은 그대로다', async () => {
  const user = userEvent.setup()
  // 현황은 상태별로 따로 묻는다. 목록 응답이 무엇이든 그 숫자를 쓰지 않는다.
  mockApi(
    (_url, config) => {
      const params = (config?.params ?? {}) as { status?: string; size?: number }
      if (params.size === 1) {
        return {
          ...response,
          items: [],
          total: params.status === 'QUOTING' ? 4 : 5,
        }
      }
      // 목록: 필터를 걸면 서버가 걸러진 total 을 돌려준다.
      return { ...response, total: params.status ? 4 : 9 }
    },
    // 가공 완료·수령 완료는 요청이 모르는 값이라 작업 목록에서 센다.
    [
      { jobId: 40, requestId: 17, status: 'RETURN_SHIPPED' },
      { jobId: 41, requestId: 18, status: 'CONFIRMED' },
    ],
  )

  renderPage()
  const summary = await screen.findByRole('region', { name: '요청 현황' })
  // 견적 대기 4 · 견적 수락 5 − (가공 완료 1 + 수령 완료 1) = 3
  expect(await within(summary).findByText('4')).toBeInTheDocument()
  expect(within(summary).getByText('3')).toBeInTheDocument()
  expect(within(summary).getAllByText('1')).toHaveLength(2)

  await user.click(screen.getByRole('button', { name: '견적 대기' }))

  expect(within(summary).getByText('4')).toBeInTheDocument()
  expect(within(summary).getByText('3')).toBeInTheDocument()
  expect(within(summary).getAllByText('1')).toHaveLength(2)
})
