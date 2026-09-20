// 가공 요청 상세 (Brand).
//
// **견적 단계가 없어졌다.** 브랜드가 검색 결과에서 가격과 소요일을 보고 바로 고르므로,
// 요청이 만들어지는 순간 작업과 금액이 함께 정해진다. 그래서 이 화면이 하는 일은 셋이다.
//
//   1) 무엇을 누구에게 맡겼고 얼마인가
//   2) 아직 결제하지 않았으면 여기서 결제한다
//   3) 결제한 뒤의 진행과 수령 확인은 가공 진행 칸이 맡는다

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
  status: 'REQUESTED',
  createdAt: '2026-09-09T01:00:00',
  expiresAt: '2026-09-09T01:15:00',
}

/** 요청과 함께 만들어진 작업. 결제 전에는 `CREATED` 다. */
const job = {
  jobId: 91, jobCode: 'JOB-091', requestId: 17, brandId: 3, processorId: 8,
  fee: 120_000, paymentMethod: 'MOCK', paymentReference: null, paymentAmount: 0,
  status: 'CREATED', paymentExpiresAt: '2026-09-09T01:15:00', createdAt: '2026-09-09T01:00:00',
  inputMaterialId: 5, inputQuantity: 12_500, lossQuantity: 0, resultRevision: 0,
  carrier: null, trackingNumber: null, returnCarrier: null, returnTrackingNumber: null,
  returnShippingEvidenceHash: null, rejectionReason: null, shippedAt: null, receivedAt: null,
  startedAt: null, resultSubmittedAt: null, rejectedAt: null, returnShippedAt: null,
  completedAt: null, acceptExpiresAt: null, acceptedAt: null, declinedAt: null,
  declineReason: null, refundedAt: null, outputs: [],
}

function mockGet(jobs: unknown[]) {
  vi.mocked(apiClient.get).mockImplementation((url: string) =>
    Promise.resolve({ data: url === '/processing-jobs' ? jobs : detail }),
  )
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
  mockGet([job])
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

test('무엇을 누구에게 맡겼고 얼마인지 보여준다', async () => {
  renderPage()

  // 제목이 어느 요청인지 말한다 — 요청 번호는 UUID 라 제목이 될 수 없다.
  expect(await screen.findByRole('heading', { name: '폐데님 원단' })).toBeInTheDocument()
  expect(screen.getByText('MAT-005')).toBeInTheDocument()
  expect(screen.getByText('데님')).toBeInTheDocument()
  expect(screen.getAllByText('12,500 g').length).toBeGreaterThan(0)
  expect(screen.getAllByText('세척').length).toBeGreaterThan(0)
  expect(screen.getAllByText('리뉴 가공소').length).toBeGreaterThan(0)
  // 금액은 서버가 확정한 값이다. 화면이 단가를 곱하지 않는다.
  expect(screen.getAllByText('120,000원').length).toBeGreaterThan(0)
  expect(screen.getByRole('link', { name: '요청 내역' })).toHaveAttribute('href', '/brand/processing/requests')
  expect(apiClient.get).toHaveBeenCalledWith('/v1/processing-requests/17', { signal: expect.any(AbortSignal) })
  // 견적 조회는 사라졌다.
  expect(vi.mocked(apiClient.get).mock.calls.some(([url]) => String(url).endsWith('/quotes'))).toBe(false)
})

test('결제 전이면 여기서 결제하고, 끝나면 발송으로 안내한다', async () => {
  const user = userEvent.setup()
  vi.mocked(apiClient.post).mockImplementation((url: string) => {
    if (url.endsWith('/payment')) return Promise.resolve({ data: {
      jobId: 91, tid: 'MOCK-TID-91', redirectUrl: '', amount: 120_000, live: false,
      paymentExpiresAt: '2026-09-09T01:15:00',
    } })
    return Promise.resolve({ data: { ...job, status: 'PAID', paymentReference: 'MOCK-TID-91' } })
  })
  renderPage()

  await user.click(await screen.findByRole('button', { name: '결제 진행' }))

  expect(apiClient.post).toHaveBeenCalledWith('/processing-jobs/91/payment')
  expect(await screen.findByText('테스트 결제 환경입니다. 실제 금액은 청구되지 않습니다.')).toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: '결제 완료로 처리 (시연)' }))

  expect(apiClient.post).toHaveBeenCalledWith('/processing-jobs/91/payment/confirm', { pgToken: 'MOCK-PG-TOKEN' })
  expect(await screen.findByText('가공비 결제가 완료되었습니다.')).toBeInTheDocument()
  // 발송은 온체인 등록·서명을 거치는 여러 단계라 작업 상세가 맡는다.
  expect(screen.getByRole('link', { name: '소재 발송하기' })).toHaveAttribute('href', '/processing/jobs/91')
})

// 기한이 지나면 요청이 만료되고 잠긴 소재가 풀린다 — 결제를 기다리는 동안에만 적는다.
test('결제 기한을 결제 전에만 알린다', async () => {
  renderPage()

  expect(await screen.findByText(/까지 결제하지 않으면 요청이 만료되고/)).toBeInTheDocument()
})

test('결제한 뒤에는 결제 칸을 두지 않는다', async () => {
  mockGet([{ ...job, status: 'PAID', paymentAmount: 120_000, acceptExpiresAt: '2026-09-12T01:00:00' }])
  renderPage()

  await screen.findByRole('heading', { name: '폐데님 원단' })
  expect(screen.queryByRole('button', { name: '결제 진행' })).not.toBeInTheDocument()
  expect(screen.queryByText(/까지 결제하지 않으면/)).not.toBeInTheDocument()
})
