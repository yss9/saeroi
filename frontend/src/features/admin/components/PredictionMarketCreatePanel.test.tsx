import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PredictionMarketCreatePanel } from './PredictionMarketCreatePanel.tsx'

const adminApi = vi.hoisted(() => ({ createPredictionMarket: vi.fn() }))
const predictionApi = vi.hoisted(() => ({ getPredictionMarkets: vi.fn() }))
vi.mock('../api/predictionMarketAdminApi.ts', () => adminApi)
vi.mock('../../prediction/api/predictionApi.ts', () => predictionApi)

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><PredictionMarketCreatePanel /></QueryClientProvider>)
}

describe('PredictionMarketCreatePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    predictionApi.getPredictionMarkets.mockResolvedValue([])
  })
  afterEach(cleanup)

  it('입력한 배치와 판매량 구간으로 관리자 개설 API를 호출한다', async () => {
    adminApi.createPredictionMarket.mockResolvedValue({
      id: 4, chainMarketId: 9, productName: '순환 재킷', productionBatchId: 12,
      status: 'OPEN', buckets: [],
    })
    renderPanel()
    await userEvent.type(screen.getByLabelText(/생산 배치 ID/), '12')
    const bounds = screen.getByLabelText(/판매량 구간 상한/)
    await userEvent.clear(bounds)
    await userEvent.type(bounds, '10, 20, 40')
    await userEvent.click(screen.getByRole('button', { name: '예측시장 개설' }))

    await waitFor(() => expect(adminApi.createPredictionMarket).toHaveBeenCalledWith(expect.objectContaining({
      productionBatchId: 12,
      bucketUpperBounds: [10, 20, 40],
    }), expect.anything()))
    expect(await screen.findByText(/순환 재킷/)).toBeInTheDocument()
  })
})
