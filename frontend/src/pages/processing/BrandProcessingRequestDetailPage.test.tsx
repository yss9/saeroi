import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { BrandProcessingRequestDetailPage } from './BrandProcessingRequestDetailPage.tsx'

vi.mock('../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn() } }))

// 백엔드가 견적 단계를 들어냈다. 요청은 만들어지는 순간 작업(processing_job)과 결제
// 기한을 함께 얻고, GET /processing-requests/{id}/quotes 는 사라졌다. 그래서 이 화면이
// 읽을 것은 요청과 작업 둘뿐이다.
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
  status: 'REQUESTED',
  createdAt: '2026-09-09T01:00:00',
  expiresAt: '2026-09-09T01:15:00',
}

const jobBase = {
  jobId: 91,
  jobCode: 'JOB-091',
  requestId: 17,
  brandId: 3,
  processorId: 8,
  fee: 120_000,
  paymentMethod: 'KAKAO_PAY',
  paymentReference: null,
  paymentAmount: 0,
  paymentExpiresAt: '2026-09-09T01:15:00',
  createdAt: '2026-09-09T01:00:00',
  inputMaterialId: 5,
  inputQuantity: 12_500,
  lossQuantity: 0,
  resultRevision: 0,
  carrier: null,
  trackingNumber: null,
  returnCarrier: null,
  returnTrackingNumber: null,
  returnShippingEvidenceHash: null,
  rejectionReason: null,
  shippedAt: null,
  receivedAt: null,
  startedAt: null,
  resultSubmittedAt: null,
  rejectedAt: null,
  returnShippedAt: null,
  completedAt: null,
  outputs: [],
}
const createdJob = { ...jobBase, status: 'CREATED' }

/**
 * 견적 엔드포인트는 **거절한다**. 화면이 그것을 아직 부르고 있다면 테스트가 바로 드러낸다
 * — 배포에서는 이 404 하나가 상세 전체를 에러로 덮어 결제까지 가렸다.
 */
function mockBackend(jobs: unknown[] = []) {
  vi.mocked(apiClient.get).mockImplementation((url: string) => {
    if (url === '/processing-jobs') return Promise.resolve({ data: jobs })
    if (url.endsWith('/quotes')) return Promise.reject(new Error('요청한 리소스를 찾을 수 없습니다.'))
    return Promise.resolve({ data: detail })
  })
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
  mockBackend()
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

test('Brand가 요청 상세를 확인하고, 사라진 견적 API 는 부르지 않는다', async () => {
  renderPage()

  // 제목이 어느 요청인지 말한다 — 요청 번호는 UUID 라 제목이 될 수 없다.
  expect(await screen.findByRole('heading', { name: '폐데님 원단' })).toBeInTheDocument()
  expect(screen.getByText('MAT-005')).toBeInTheDocument()
  expect(screen.getByText('데님')).toBeInTheDocument()
  expect(screen.getByText('12,500 g')).toBeInTheDocument()
  expect(screen.getByText('세척')).toBeInTheDocument()
  expect(screen.getByText('재단')).toBeInTheDocument()
  expect(screen.getByText('리뉴 가공소')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '요청 내역' })).toHaveAttribute('href', '/brand/processing/requests')
  expect(screen.queryByText('상세 정보를 불러오지 못했어요')).not.toBeInTheDocument()

  expect(apiClient.get).toHaveBeenCalledWith('/v1/processing-requests/17', { signal: expect.any(AbortSignal) })
  expect(apiClient.get).not.toHaveBeenCalledWith('/v1/processing-requests/17/quotes', expect.anything())
})

test('작업이 아직 없으면 가공 조건 칸을 두지 않는다', async () => {
  renderPage()

  await screen.findByRole('heading', { name: '폐데님 원단' })
  expect(screen.queryByRole('heading', { name: '가공 조건' })).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '가공비 결제' })).not.toBeInTheDocument()
})

test('요청이 만든 작업에서 가공 조건과 결제 진입점을 보여준다', async () => {
  mockBackend([createdJob])
  renderPage()

  expect(await screen.findByRole('heading', { name: '가공 조건' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '가공비 결제' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '결제 진행' })).toBeInTheDocument()
  // 결제 금액은 결제 칸이 말한다 — 같은 값을 가공 조건에 한 번 더 적지 않는다.
  expect(screen.getByText('결제 금액').parentElement).toHaveTextContent('120,000원')
  expect(screen.queryByText('가공 비용')).not.toBeInTheDocument()
})

test('생성된 Job에서 결제를 준비하고 Mock 승인까지 진행한다', async () => {
  const user = userEvent.setup()
  mockBackend([createdJob])
  vi.mocked(apiClient.post).mockImplementation((url: string) => {
    if (url.endsWith('/payment')) {
      return Promise.resolve({ data: {
        jobId: 91, tid: 'MOCK-TID-91', redirectUrl: '', amount: 120_000, live: false,
        paymentExpiresAt: '2026-09-09T01:15:00',
      } })
    }
    return Promise.resolve({ data: { ...createdJob, status: 'PAID', paymentAmount: 120_000, paymentReference: 'MOCK-TID-91' } })
  })
  renderPage()

  await user.click(await screen.findByRole('button', { name: '결제 진행' }))

  expect(apiClient.post).toHaveBeenCalledWith('/processing-jobs/91/payment')
  expect(await screen.findByText('테스트 결제 환경입니다. 실제 금액은 청구되지 않습니다.')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: '결제 완료로 처리 (시연)' }))

  expect(apiClient.post).toHaveBeenCalledWith('/processing-jobs/91/payment/confirm', { pgToken: 'MOCK-PG-TOKEN' })
  expect(await screen.findByText('가공비 결제가 완료되었습니다.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '소재 발송하기' })).toHaveAttribute('href', '/processing/jobs/91')
})

test('결제 실패 후 다시 들어와도 기존 미결제 Job에서 결제를 재시도한다', async () => {
  const user = userEvent.setup()
  mockBackend([createdJob])
  vi.mocked(apiClient.post).mockResolvedValue({ data: {
    jobId: 91, tid: 'MOCK-TID-RETRY', redirectUrl: '', amount: 120_000, live: false,
    paymentExpiresAt: '2026-09-09T01:15:00',
  } })

  renderPage()

  await user.click(await screen.findByRole('button', { name: '결제 진행' }))

  expect(apiClient.post).toHaveBeenCalledWith('/processing-jobs/91/payment')
  expect(await screen.findByText('테스트 결제 환경입니다. 실제 금액은 청구되지 않습니다.')).toBeInTheDocument()
})

test('결제 완료 Job을 찾으면 요청 상세는 발송을 가공 진행 화면으로 안내한다', async () => {
  mockBackend([{ ...jobBase, status: 'PAID', paymentAmount: 120_000 }])

  renderPage()

  // 소재 발송은 온체인 작업 등록 · 수락 · 발송 서명을 거치는 여러 단계라 요청 상세에서
  // 바로 처리할 수 없다 — 그 단계가 있는 작업 상세로 보낸다.
  expect(await screen.findByRole('link', { name: '소재 발송하기' })).toHaveAttribute('href', '/processing/jobs/91')
  expect(screen.queryByRole('heading', { name: '소재 발송' })).not.toBeInTheDocument()
  expect(apiClient.patch).not.toHaveBeenCalled()
  // 결제 칸이 사라진 뒤에는 가공 조건이 금액을 남기는 유일한 자리다.
  expect(screen.getByText('가공 비용').parentElement).toHaveTextContent('120,000원')
})

test('결제 기한 안내는 결제 전에만 둔다', async () => {
  mockBackend([createdJob])
  renderPage()

  expect(await screen.findByText(/까지 결제하지 않으면/)).toBeInTheDocument()
})

test('올바르지 않은 요청 번호는 API를 호출하지 않고 안내한다', () => {
  renderPage('/brand/processing/requests/not-a-number')

  expect(screen.getByRole('alert')).toHaveTextContent('올바르지 않은 요청 번호입니다.')
  expect(apiClient.get).not.toHaveBeenCalled()
})
