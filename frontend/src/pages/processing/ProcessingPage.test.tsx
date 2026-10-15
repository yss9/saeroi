// 가공사의 일 목록 (`/processor/jobs`).
//
// **견적 요청 칸이 없어졌다.** 견적을 쓰는 단계가 사라져 결제 전 요청에는 가공사가 손댈
// 데가 없다 — 맡을지 말지는 결제가 끝난 작업에서 정한다. 그래서 이 화면은 작업만 싣고,
// 요청은 작업 줄에 브랜드·소재·공정 이름을 적는 데만 쓴다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { companyKeys } from '../../features/company/api/companyKeys.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { CompanyDashboardLayout } from '../../features/company/components/CompanyDashboardLayout.tsx'
import { ProcessingPage } from './ProcessingPage.tsx'

vi.mock('../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))

const requests = {
  items: [
    {
      requestId: 7,
      requestCode: 'PR-007',
      brandId: 1,
      brandName: '새로이 스튜디오',
      processorId: 2,
      processorName: '리뉴 텍스타일',
      materialId: 3,
      materialCode: 'MAT-003',
      materialName: '폐데님',
      materialType: 'DENIM',
      quantity: 20_000,
      processTypes: ['CLEANING', 'DISASSEMBLY'],
      status: 'REQUESTED',
      createdAt: '2026-09-07T03:00:00',
      expiresAt: '2026-09-14T03:00:00',
    },
  ],
  total: 1,
  page: 0,
  size: 20,
}

const jobs = [
  {
    jobId: 17,
    jobCode: 'JOB-017',
    requestId: 7,
    status: 'PAID',
    inputMaterialId: 3,
    inputQuantity: 20_000,
    fee: 150_000,
    createdAt: '2026-09-08T03:00:00',
  },
]

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  queryClient.setQueryData(companyKeys.me(), { companyName: '리뉴 텍스타일', roleApprovalStatus: 'APPROVED' })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/processor/jobs']}>
        <CompanyDashboardLayout>
          <ProcessingPage />
        </CompanyDashboardLayout>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.setState({
    user: { id: 2, email: 'processor@test.com', name: '리뉴 텍스타일', role: 'PROCESSOR', walletAddress: null },
    accessToken: 'token',
    isAuthenticated: true,
  })
  vi.mocked(apiClient.get).mockImplementation(async url => ({
    data: url === '/v1/companies/me'
      ? { companyName: '리뉴 텍스타일', roleApprovalStatus: 'APPROVED' }
      : url === '/processing-jobs' ? jobs : requests,
  }))
  vi.mocked(apiClient.post).mockResolvedValue({ data: {} })
})

afterEach(cleanup)

test('맡은 가공 작업을 어느 브랜드의 무슨 소재인지와 함께 보여준다', async () => {
  renderPage()

  expect(await screen.findByRole('heading', { name: '가공 작업' })).toBeInTheDocument()
  const list = within(await screen.findByRole('table', { name: '가공 작업 목록' }))
  expect(list.getByText('JOB-017')).toBeInTheDocument()
  // 이름은 요청에서 온다 — 작업 응답에는 소재 번호밖에 없다.
  expect(list.getByText(/새로이 스튜디오/)).toBeInTheDocument()
  expect(list.getByText(/폐데님/)).toBeInTheDocument()
  expect(list.getByText(/세척 · 해체/)).toBeInTheDocument()
  expect(list.getByText('20,000 g')).toBeInTheDocument()
  expect(list.getByText('수락 대기')).toBeInTheDocument()
  expect(apiClient.get).toHaveBeenCalledWith('/v1/processing-requests', {
    params: { page: 0, size: 20 },
    signal: expect.any(AbortSignal),
  })
})

// 결제 전 요청은 가공사가 할 일이 없다. 줄로 남겨 두면 "견적 작성 전" 이라고 적힌,
// 눌러도 아무것도 못 하는 줄만 목록을 채운다.
test('결제 전 요청과 견적 요청 필터는 목록에 없다', async () => {
  renderPage()

  await screen.findByRole('table', { name: '가공 작업 목록' })
  expect(screen.queryByText('PR-007')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '견적 요청' })).not.toBeInTheDocument()
  expect(screen.queryByRole('navigation', { name: '가공 업무 필터' })).not.toBeInTheDocument()
})

test('작업 줄을 누르면 작업 상세로 이동한다', async () => {
  const user = userEvent.setup()
  renderPage()

  const row = await screen.findByRole('link', { name: /작업 상세 및 처리/ })
  expect(row).toHaveAttribute('href', '/processing/jobs/17')
  await user.click(row)
})

// 요청 조회가 실패해도 작업은 보여야 한다 — 이름이 빠질 뿐, 할 일은 그대로 남아 있다.
test('요청 이름을 못 불러와도 작업 줄은 그린다', async () => {
  vi.mocked(apiClient.get).mockImplementation(async url => {
    if (url === '/v1/companies/me') return { data: { companyName: '리뉴 텍스타일', roleApprovalStatus: 'APPROVED' } }
    if (url === '/processing-jobs') return { data: jobs }
    throw new Error('요청 목록을 불러오지 못했습니다.')
  })
  renderPage()

  const list = within(await screen.findByRole('table', { name: '가공 작업 목록' }))
  expect(list.getByText('투입 소재 #3')).toBeInTheDocument()
  expect(list.getByText('상세에서 작업 조건 확인')).toBeInTheDocument()
})

test('맡은 작업이 없으면 빈 상태를 알린다', async () => {
  vi.mocked(apiClient.get).mockImplementation(async url => ({
    data: url === '/v1/companies/me'
      ? { companyName: '리뉴 텍스타일', roleApprovalStatus: 'APPROVED' }
      : url === '/processing-jobs' ? [] : requests,
  }))
  renderPage()

  expect(await screen.findByText('맡은 가공 작업이 없어요')).toBeInTheDocument()
})

test('가공사 사이드바에서 업체 등록과 회사 정보로 갈 수 있다', async () => {
  renderPage()

  await screen.findByRole('table', { name: '가공 작업 목록' })
  expect(screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })).toBeInTheDocument()
  expect(screen.queryByRole('complementary', { name: 'Processor 메뉴' })).not.toBeInTheDocument()
  // 사이드바가 일의 종류로 묶여 있고 지금 화면이 속한 "가공" 묶음만 열려 있다.
  expect(screen.getByRole('link', { name: '업체 등록' })).toHaveAttribute('href', '/processor/profile')
  expect(screen.getByRole('link', { name: '회사 정보' })).toHaveAttribute('href', '/company/account')
  expect(screen.getByRole('link', { name: '쇼핑몰' })).toHaveAttribute('href', '/')
})
