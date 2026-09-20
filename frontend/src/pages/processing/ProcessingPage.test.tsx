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
      status: 'QUOTING',
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
    status: 'PROCESSING',
    inputMaterialId: 3,
    inputQuantity: 20_000,
    fee: 150_000,
    createdAt: '2026-09-08T03:00:00',
  },
]
async function openQuoteRequests() {
  const user = userEvent.setup()
  renderPage()
  await user.click(screen.getByRole('button', { name: '견적 요청' }))
  return user
}

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

test('견적 요청과 Job을 하나의 가공 업무 목록에 표시한다', async () => {
  renderPage()

  expect(await screen.findByRole('heading', { name: '가공 업무' })).toBeInTheDocument()
  const list = within(await screen.findByRole('table', { name: '가공 업무 목록' }))
  expect(list.getByText('PR-007')).toBeInTheDocument()
  expect(list.getByText('JOB-017')).toBeInTheDocument()
  expect(list.getByText('견적 요청')).toBeInTheDocument()
  expect(list.getByText('가공 작업')).toBeInTheDocument()
})

test('가공 작업 필터에서 Job 카드로 상세 페이지에 이동할 수 있다', async () => {
  const user = userEvent.setup()
  vi.mocked(apiClient.get).mockImplementation(async url => ({
    data: url === '/v1/companies/me'
      ? { companyName: '리뉴 텍스타일', roleApprovalStatus: 'APPROVED' }
      : url === '/processing-jobs'
      ? [{ jobId: 17, jobCode: 'JOB-017', status: 'PROCESSING', inputMaterialId: 3, inputQuantity: 20_000, fee: 150_000 }]
      : requests,
  }))
  renderPage()
  const viewFilter = within(screen.getByRole('navigation', { name: '가공 업무 필터' }))
  await user.click(viewFilter.getByRole('button', { name: '가공 작업' }))

  const list = within(await screen.findByRole('table', { name: '가공 업무 목록' }))
  expect(list.queryByText('PR-007')).not.toBeInTheDocument()
  expect(list.getByRole('link', { name: /작업 상세 및 처리/ })).toHaveAttribute('href', '/processing/jobs/17')
})

test('로그인한 Processor에게 지정된 가공 요청을 표시한다', async () => {
  await openQuoteRequests()

  expect(await screen.findByRole('heading', { name: '가공 업무' })).toBeInTheDocument()
  const viewFilter = within(screen.getByRole('navigation', { name: '가공 업무 필터' }))
  expect(viewFilter.getByRole('button', { name: '견적 요청' })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })).toBeInTheDocument()
  expect(screen.queryByRole('complementary', { name: 'Processor 메뉴' })).not.toBeInTheDocument()
  // 사이드바가 일의 종류로 묶여 있고 지금 화면이 속한 "가공" 묶음만 열려 있다.
  expect(screen.getByRole('link', { name: '업체 등록' })).toHaveAttribute('href', '/processor/profile')
  expect(screen.getByRole('link', { name: '회사 정보' })).toHaveAttribute('href', '/company/account')
  expect(screen.getByRole('link', { name: '쇼핑몰' })).toHaveAttribute('href', '/')
  expect(await screen.findByText('PR-007')).toBeInTheDocument()
  expect(screen.getByText(/폐데님/)).toBeInTheDocument()
  expect(screen.getByText('20,000 g')).toBeInTheDocument()
  expect(screen.getByText(/세척 · 해체/)).toBeInTheDocument()
  expect(screen.getByText(/새로이 스튜디오/)).toBeInTheDocument()
  expect(apiClient.get).toHaveBeenCalledWith('/v1/processing-requests', {
    params: { page: 0, size: 20 },
    signal: expect.any(AbortSignal),
  })
})

test('견적 요청 필터를 선택하면 요청만 표시한다', async () => {
  await openQuoteRequests()

  const list = within(await screen.findByRole('table', { name: '가공 업무 목록' }))
  expect(list.getByText('PR-007')).toBeInTheDocument()
  expect(list.queryByText('JOB-017')).not.toBeInTheDocument()
})

// 견적을 쓰는 일이 없어졌다 — 브랜드가 등록된 단가로 바로 요청하고 결제한다. 가공사가
// 맡을지 말지 정하는 자리는 결제된 작업(가공 작업 상세)이다.
test('요청 행을 펼치면 만료 정보와 다음 할 일을 알려준다', async () => {
  const user = await openQuoteRequests()
  await screen.findByText('PR-007')

  const detailButton = screen.getByRole('button', { name: 'PR-007 요청 상세 보기' })
  await user.click(detailButton)

  expect(detailButton).toHaveAttribute('aria-expanded', 'true')
  expect(screen.getByText('만료 일시')).toBeInTheDocument()
  expect(screen.getByText('DENIM')).toBeInTheDocument()
  expect(screen.getByText('결제가 끝나면 가공 작업에서 수락하거나 거절할 수 있습니다.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '견적 제출' })).not.toBeInTheDocument()
})

test('다음 페이지로 이동하면 해당 페이지를 조회한다', async () => {
  vi.mocked(apiClient.get).mockImplementation(async url => ({
    data: url === '/v1/companies/me'
      ? { companyName: '리뉴 텍스타일', roleApprovalStatus: 'APPROVED' }
      : url === '/processing-jobs' ? jobs : { ...requests, total: 21 },
  }))
  const user = await openQuoteRequests()
  await screen.findByText('PR-007')

  await user.click(screen.getByRole('button', { name: '다음 페이지' }))

  expect(apiClient.get).toHaveBeenLastCalledWith('/v1/processing-requests', {
    params: { page: 1, size: 20 },
    signal: expect.any(AbortSignal),
  })
})
