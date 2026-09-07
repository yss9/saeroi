import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { ProcessingPage } from './ProcessingPage.tsx'

vi.mock('../../services/apiClient.ts', () => ({ apiClient: { get: vi.fn() } }))

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

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter><ProcessingPage /></MemoryRouter>
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
  vi.mocked(apiClient.get).mockResolvedValue({ data: requests })
})

afterEach(cleanup)

test('로그인한 Processor에게 지정된 가공 요청을 표시한다', async () => {
  renderPage()

  expect(await screen.findByRole('heading', { name: '견적 요청' })).toBeInTheDocument()
  expect(await screen.findByText('PR-007')).toBeInTheDocument()
  expect(screen.getByText('폐데님')).toBeInTheDocument()
  expect(screen.getByText('20,000 g')).toBeInTheDocument()
  expect(screen.getByText('세척')).toBeInTheDocument()
  expect(screen.getByText('해체')).toBeInTheDocument()
  expect(screen.getByText('새로이 스튜디오')).toBeInTheDocument()
  expect(apiClient.get).toHaveBeenCalledWith('/v1/processing-requests', {
    params: { page: 0, size: 20 },
    signal: expect.any(AbortSignal),
  })
})

test('상태 필터를 선택하면 첫 페이지부터 다시 조회한다', async () => {
  const user = userEvent.setup()
  renderPage()
  await screen.findByText('PR-007')

  await user.click(screen.getByRole('button', { name: '거절' }))

  expect(apiClient.get).toHaveBeenLastCalledWith('/v1/processing-requests', {
    params: { page: 0, size: 20, status: 'REJECTED' },
    signal: expect.any(AbortSignal),
  })
})

test('요청 행을 펼치면 만료 정보와 상세 조건을 확인할 수 있다', async () => {
  const user = userEvent.setup()
  renderPage()
  await screen.findByText('PR-007')

  const detailButton = screen.getByRole('button', { name: 'PR-007 요청 상세 보기' })
  await user.click(detailButton)

  expect(detailButton).toHaveAttribute('aria-expanded', 'true')
  expect(screen.getByText('만료 일시')).toBeInTheDocument()
  expect(screen.getByText('DENIM')).toBeInTheDocument()
  expect(screen.getByText(/견적 제출 단계/)).toBeInTheDocument()
})

test('다음 페이지로 이동하면 해당 페이지를 조회한다', async () => {
  const user = userEvent.setup()
  vi.mocked(apiClient.get).mockResolvedValue({ data: { ...requests, total: 21 } })
  renderPage()
  await screen.findByText('PR-007')

  await user.click(screen.getByRole('button', { name: '다음 페이지' }))

  expect(apiClient.get).toHaveBeenLastCalledWith('/v1/processing-requests', {
    params: { page: 1, size: 20 },
    signal: expect.any(AbortSignal),
  })
})
