import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { useAuthStore } from '../../../stores/authStore.ts'
import { ProcessingCustodyList } from './ProcessingCustodyList.tsx'

const api = vi.hoisted(() => ({ getProcessingCustodyMaterials: vi.fn() }))
vi.mock('../api/getProcessingCustodyMaterials.ts', () => api)

function renderList() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter><ProcessingCustodyList /></MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  useAuthStore.setState({
    user: { id: 8, email: 'processor@test.com', name: '가공사', role: 'PROCESSOR', walletAddress: null },
    accessToken: 'token',
    isAuthenticated: true,
  })
  api.getProcessingCustodyMaterials.mockReset().mockResolvedValue([
    {
      jobId: 17,
      jobCode: 'PJ-017',
      materialId: 11,
      materialCode: 'MAT-011',
      materialName: '폐데님 원단',
      materialType: 'DENIM',
      quantity: 20_000,
      status: 'SHIPPED',
      shippedAt: '2026-09-15T10:00:00',
      receivedAt: null,
    },
  ])
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

test('Processor에게 배송 중인 가공 소재를 소유 토큰과 구분해 표시한다', async () => {
  renderList()

  expect(await screen.findByRole('heading', { name: '가공 보관 소재' })).toBeInTheDocument()
  expect(await screen.findByText('폐데님 원단')).toBeInTheDocument()
  expect(screen.getByText('20 kg')).toBeInTheDocument()
  expect(screen.getByText('배송 중')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /폐데님 원단/ })).toHaveAttribute('href', '/processing/jobs/17')
})

test('Processor가 아니면 가공 보관 소재를 조회하거나 표시하지 않는다', () => {
  useAuthStore.setState({
    user: { id: 2, email: 'brand@test.com', name: '브랜드', role: 'BRAND', walletAddress: null },
    accessToken: 'token',
    isAuthenticated: true,
  })
  renderList()

  expect(screen.queryByRole('heading', { name: '가공 보관 소재' })).not.toBeInTheDocument()
  expect(api.getProcessingCustodyMaterials).not.toHaveBeenCalled()
})