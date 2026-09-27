import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, test, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { ProcessingPaymentCallbackPage } from './ProcessingPaymentCallbackPage.tsx'

vi.mock('../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))
const chain = vi.hoisted(() => ({ register: vi.fn() }))
vi.mock('../../features/processing/services/registerProcessingChainJob.ts', () => ({ registerProcessingChainJob: chain.register }))

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
  // 결제 다음은 발송이 아니라 가공사의 수락이다.
  expect(screen.getByRole('link', { name: '가공 작업 보기' })).toHaveAttribute('href', '/processing/jobs/91')
  expect(screen.getByRole('link', { name: '요청 상세로 돌아가기' })).toHaveAttribute('href', '/brand/processing/requests/17')
  expect(apiClient.post).toHaveBeenCalledWith('/processing-jobs/91/payment/confirm', { pgToken: 'approved-token' })
})

const paidJob = {
  jobId: 91, jobCode: 'JOB-091', requestId: 17, quoteId: 31, brandId: 3, processorId: 8,
  fee: 120_000, paymentMethod: 'KAKAO_PAY', paymentReference: 'T123', paymentAmount: 120_000,
  status: 'PAID', paymentExpiresAt: '2026-09-09T03:15:00', createdAt: '2026-09-09T03:00:00',
}

/*
  결제 다음에 브랜드가 따로 누를 "작업 등록" 은 없다. 결제가 확정되면 곧바로 지갑 서명을
  요청한다 — 그래야 가공업체가 수락(온체인 서명)할 대상이 체인에 생긴다.
*/
test('결제가 확정되면 곧바로 가공 작업을 온체인에 등록하고 가공업체 수락을 기다린다', async () => {
  vi.mocked(apiClient.get).mockResolvedValue({ data: createdJob })
  vi.mocked(apiClient.post).mockResolvedValue({ data: paidJob })
  chain.register.mockResolvedValue('0xabc')

  renderPage('/processing/jobs/91/payment/callback?pg_token=approved-token')

  expect(await screen.findByText('가공업체가 수락하면 소재를 보낼 수 있어요.')).toBeInTheDocument()
  expect(chain.register).toHaveBeenCalledTimes(1)
  expect(chain.register).toHaveBeenCalledWith(91)
})

test('등록 서명이 끝나지 않으면 결제 완료는 그대로 두고 다시 서명할 수 있다', async () => {
  const user = userEvent.setup()
  vi.mocked(apiClient.get).mockResolvedValue({ data: createdJob })
  vi.mocked(apiClient.post).mockResolvedValue({ data: paidJob })
  chain.register.mockRejectedValueOnce(new Error('user rejected action')).mockResolvedValueOnce('0xabc')

  renderPage('/processing/jobs/91/payment/callback?pg_token=approved-token')

  expect(await screen.findByText('결제는 완료됐지만 가공 작업 등록 서명이 끝나지 않았어요.')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '가공비 결제가 완료되었습니다' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '다시 서명하기' }))

  expect(await screen.findByText('가공업체가 수락하면 소재를 보낼 수 있어요.')).toBeInTheDocument()
  expect(chain.register).toHaveBeenCalledTimes(2)
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
