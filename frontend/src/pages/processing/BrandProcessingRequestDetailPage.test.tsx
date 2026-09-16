import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { BrandProcessingRequestDetailPage } from './BrandProcessingRequestDetailPage.tsx'

vi.mock('../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn() } }))

const detail = {
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
}

const quote = {
  quoteId: 31,
  requestId: 17,
  processorId: 8,
  processorName: '리뉴 가공소',
  price: 120_000,
  leadDays: 5,
  memo: '세척 비용이 포함된 견적입니다.',
  status: 'SUBMITTED',
  createdAt: '2026-09-09T03:00:00',
}

function renderPage(path = '/brand/processing/requests/17') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/brand/processing/requests/:requestId" element={<BrandProcessingRequestDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.setState({
    user: { id: 3, email: 'brand@test.com', name: '새로이 브랜드', role: 'BRAND', walletAddress: null },
    accessToken: 'token',
    isAuthenticated: true,
  })
  vi.mocked(apiClient.get).mockImplementation((url: string) => Promise.resolve({
    data: url === '/processing-jobs' ? [] : url.endsWith('/quotes') ? [quote] : detail,
  }))
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

test('Brand가 요청 상세와 접수된 견적을 함께 확인한다', async () => {
  renderPage()

  // 제목이 어느 요청인지 말한다 — 요청 번호는 UUID 라 제목이 될 수 없다.
  expect(await screen.findByRole('heading', { name: '폐데님 원단' })).toBeInTheDocument()
  expect(screen.getByText('MAT-005')).toBeInTheDocument()
  expect(screen.getByText('데님')).toBeInTheDocument()
  expect(screen.getByText('12,500 g')).toBeInTheDocument()
  expect(screen.getByText('세척')).toBeInTheDocument()
  expect(screen.getByText('재단')).toBeInTheDocument()
  expect(screen.getAllByText('리뉴 가공소').length).toBeGreaterThan(0)
  expect(screen.getAllByText('120,000원').length).toBeGreaterThan(0)
  expect(screen.getByText('예상 5일')).toBeInTheDocument()
  expect(screen.getByText('세척 비용이 포함된 견적입니다.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '요청 내역' })).toHaveAttribute('href', '/brand/processing/requests')
  expect(apiClient.get).toHaveBeenCalledWith('/v1/processing-requests/17', { signal: expect.any(AbortSignal) })
  expect(apiClient.get).toHaveBeenCalledWith('/v1/processing-requests/17/quotes', { signal: expect.any(AbortSignal) })
})

test('견적 선택은 확인 단계를 거친 뒤 Job을 생성한다', async () => {
  const user = userEvent.setup()
  vi.mocked(apiClient.post).mockResolvedValue({ data: {
    jobId: 91,
    jobCode: 'JOB-091',
    requestId: 17,
    quoteId: 31,
    brandId: 3,
    processorId: 8,
    fee: 120_000,
    paymentMethod: 'MOCK',
    paymentReference: null,
    paymentAmount: 0,
    status: 'CREATED',
    paymentExpiresAt: '2026-09-09T03:15:00',
    createdAt: '2026-09-09T03:00:00',
  } })
  renderPage()
  await screen.findByText('120,000원')

  await user.click(screen.getByRole('button', { name: '이 견적 선택' }))
  expect(apiClient.post).not.toHaveBeenCalled()
  expect(screen.getByText('견적을 확정하면 15분 안에 결제를 완료해야 합니다.')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: '견적 선택 확정' }))

  expect(apiClient.post).toHaveBeenCalledWith('/v1/quotes/31/select', { paymentMethod: 'MOCK' })
  expect((await screen.findAllByText('JOB-091')).length).toBeGreaterThan(0)
  expect(screen.getByText('견적 선택이 완료되었습니다.')).toBeInTheDocument()
  expect(screen.getByText('결제 금액').parentElement).toHaveTextContent('120,000원')
})

test('생성된 Job에서 결제를 준비하고 Mock 승인까지 진행한다', async () => {
  const user = userEvent.setup()
  const job = {
    jobId: 91, jobCode: 'JOB-091', requestId: 17, quoteId: 31, brandId: 3, processorId: 8,
    fee: 120_000, paymentMethod: 'MOCK', paymentReference: null, paymentAmount: 0,
    status: 'CREATED', paymentExpiresAt: '2026-09-09T03:15:00', createdAt: '2026-09-09T03:00:00',
  }
  vi.mocked(apiClient.post).mockImplementation((url: string) => {
    if (url.endsWith('/select')) return Promise.resolve({ data: job })
    if (url.endsWith('/payment')) return Promise.resolve({ data: {
      jobId: 91, tid: 'MOCK-TID-91', redirectUrl: '', amount: 120_000, live: false,
      paymentExpiresAt: '2026-09-09T03:15:00',
    } })
    return Promise.resolve({ data: { ...job, status: 'PAID', paymentReference: 'MOCK-TID-91' } })
  })
  renderPage()
  await screen.findByText('120,000원')
  await user.click(screen.getByRole('button', { name: '이 견적 선택' }))
  await user.click(screen.getByRole('button', { name: '견적 선택 확정' }))
  await screen.findAllByText('JOB-091')

  await user.click(screen.getByRole('button', { name: '결제 진행' }))

  expect(apiClient.post).toHaveBeenCalledWith('/processing-jobs/91/payment')
  expect(await screen.findByText('테스트 결제 환경입니다. 실제 금액은 청구되지 않습니다.')).toBeInTheDocument()
  expect(screen.getAllByText('120,000원').length).toBeGreaterThan(0)

  await user.click(screen.getByRole('button', { name: '결제 완료로 처리 (시연)' }))

  expect(apiClient.post).toHaveBeenCalledWith('/processing-jobs/91/payment/confirm', { pgToken: 'MOCK-PG-TOKEN' })
  expect(await screen.findByText('가공비 결제가 완료되었습니다.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '소재 발송하기' })).toHaveAttribute('href', '/processing/jobs/91')
})

test('결제 완료 Job을 찾아 요청 상세에서 소재를 발송한다', async () => {
  const user = userEvent.setup()
  const paidJob = {
    jobId: 91, jobCode: 'JOB-091', requestId: 17, quoteId: 31, brandId: 3, processorId: 8,
    fee: 120_000, paymentAmount: 120_000, status: 'PAID', paymentExpiresAt: '2026-09-09T03:15:00',
    createdAt: '2026-09-09T03:00:00', inputMaterialId: 5, inputQuantity: 12_500, lossQuantity: 0,
    resultRevision: 0, carrier: null, trackingNumber: null, rejectionReason: null, shippedAt: null,
    receivedAt: null, startedAt: null, resultSubmittedAt: null, rejectedAt: null, completedAt: null, outputs: [],
  }
  vi.mocked(apiClient.get).mockImplementation((url: string) => Promise.resolve({
    data: url === '/processing-jobs' ? [paidJob] : url.endsWith('/quotes') ? [{ ...quote, status: 'SELECTED' }] : { ...detail, status: 'SELECTED' },
  }))
  vi.mocked(apiClient.patch).mockResolvedValue({ data: { ...paidJob, status: 'SHIPPED', carrier: '새로이택배', trackingNumber: '1234567890' } })

  renderPage()

  expect(await screen.findByRole('heading', { name: '소재 발송' })).toBeInTheDocument()
  await user.type(screen.getByLabelText('택배사(선택)'), '새로이택배')
  await user.type(screen.getByLabelText('운송장 번호(선택)'), '1234567890')
  await user.click(screen.getByRole('button', { name: '발송 처리' }))

  expect(apiClient.patch).toHaveBeenCalledWith('/processing-jobs/91/ship', {
    carrier: '새로이택배',
    trackingNumber: '1234567890',
  })
})

test('접수된 견적이 없으면 대기 상태를 안내한다', async () => {
  vi.mocked(apiClient.get).mockImplementation((url: string) => Promise.resolve({
    data: url === '/processing-jobs' ? [] : url.endsWith('/quotes') ? [] : detail,
  }))
  renderPage()

  expect(await screen.findByText('아직 접수된 견적이 없어요')).toBeInTheDocument()
  expect(screen.getByText('지정한 가공사가 견적을 제출하면 이곳에서 확인할 수 있습니다.')).toBeInTheDocument()
})

test('올바르지 않은 요청 번호는 API를 호출하지 않고 안내한다', () => {
  renderPage('/brand/processing/requests/not-a-number')

  expect(screen.getByRole('alert')).toHaveTextContent('올바르지 않은 요청 번호입니다.')
  expect(apiClient.get).not.toHaveBeenCalled()
})
