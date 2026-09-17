import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, test, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { ProcessingPaymentCallbackPage } from './ProcessingPaymentCallbackPage.tsx'

vi.mock('../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))

const createdJob = {
  jobId: 91, jobCode: 'JOB-091', requestId: 17, quoteId: 31, brandId: 3, processorId: 8,
  fee: 120_000, paymentAmount: 0, status: 'CREATED', paymentExpiresAt: '2026-09-09T03:15:00',
  createdAt: '2026-09-09T03:00:00', inputMaterialId: 5, inputQuantity: 12_500, lossQuantity: 0,
  resultRevision: 0, carrier: null, trackingNumber: null, returnCarrier: null, returnTrackingNumber: null,
  returnShippingEvidenceHash: null, rejectionReason: null, shippedAt: null, receivedAt: null,
  startedAt: null, resultSubmittedAt: null, rejectedAt: null, returnShippedAt: null,
  completedAt: null, outputs: [],
}

function renderPage(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes><Route path="/processing/jobs/:jobId/payment/callback" element={<ProcessingPaymentCallbackPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

test('PG에서 돌아오면 pg_token으로 가공비 결제를 승인한다', async () => {
  vi.mocked(apiClient.get).mockResolvedValue({ data: createdJob })
  vi.mocked(apiClient.post).mockResolvedValue({ data: {
    jobId: 91, jobCode: 'JOB-091', requestId: 17, quoteId: 31, brandId: 3, processorId: 8,
    fee: 120_000, paymentMethod: 'KAKAO_PAY', paymentReference: 'T123', paymentAmount: 120_000,
    status: 'PAID', paymentExpiresAt: '2026-09-09T03:15:00', createdAt: '2026-09-09T03:00:00',
  } })

  renderPage('/processing/jobs/91/payment/callback?pg_token=approved-token')

  expect(await screen.findByRole('heading', { name: '가공비 결제가 완료되었습니다' })).toBeInTheDocument()
  expect(screen.getByText('120,000원')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '소재 발송하기' })).toHaveAttribute('href', '/processing/jobs/91')
  expect(screen.getByRole('link', { name: '요청 상세로 돌아가기' })).toHaveAttribute('href', '/brand/processing/requests/17')
  expect(apiClient.post).toHaveBeenCalledWith('/processing-jobs/91/payment/confirm', { pgToken: 'approved-token' })
})

test('PG 토큰 없이 돌아오면 요청 상세에서 결제를 다시 시도할 수 있다', async () => {
  vi.mocked(apiClient.get).mockResolvedValue({ data: createdJob })
  renderPage('/processing/jobs/91/payment/callback')

  expect(await screen.findByRole('heading', { name: '결제가 완료되지 않았습니다' })).toBeInTheDocument()
  expect(screen.getByText('결제 기한 전이라면 요청 상세에서 다시 결제할 수 있습니다.')).toBeInTheDocument()
  expect(await screen.findByRole('link', { name: '결제 다시 시도' })).toHaveAttribute('href', '/brand/processing/requests/17')
  expect(apiClient.post).not.toHaveBeenCalled()
})

test('결제 승인 실패 후에도 요청 상세로 돌아가 재결제할 수 있다', async () => {
  vi.mocked(apiClient.get).mockResolvedValue({ data: createdJob })
  vi.mocked(apiClient.post).mockRejectedValue(new Error('결제 승인에 실패했습니다.'))

  renderPage('/processing/jobs/91/payment/callback?pg_token=failed-token')

  expect(await screen.findByRole('heading', { name: '결제 승인에 실패했습니다' })).toBeInTheDocument()
  expect(await screen.findByRole('link', { name: '결제 다시 시도' })).toHaveAttribute('href', '/brand/processing/requests/17')
})
