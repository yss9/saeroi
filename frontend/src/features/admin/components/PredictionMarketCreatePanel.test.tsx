import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PredictionMarketCreatePanel } from './PredictionMarketCreatePanel.tsx'

const adminApi = vi.hoisted(() => ({
  createPredictionMarket: vi.fn(),
  getPredictionMarketEligibleBatches: vi.fn(),
}))
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
    adminApi.getPredictionMarketEligibleBatches.mockResolvedValue([{
      productionBatchId: 12,
      chainBatchId: 7,
      batchCode: 'PB-000012',
      batchName: '순환 재킷 1차 생산',
      brandName: '새로이 브랜드',
      productName: '순환 재킷',
      productionQuantity: 40,
    }])
  })
  afterEach(cleanup)

  it('입력한 배치와 판매량 구간으로 관리자 개설 API를 호출한다', async () => {
    adminApi.createPredictionMarket.mockResolvedValue({
      id: 4, chainMarketId: 9, productName: '순환 재킷', productionBatchId: 12,
      status: 'OPEN', buckets: [],
    })
    renderPanel()
    await screen.findByRole('option', { name: /새로이 브랜드.*순환 재킷.*PB-000012.*40개/ })
    await userEvent.selectOptions(screen.getByLabelText(/예측 대상 생산 배치/), '12')
    expect(screen.getByText(/온체인 배치 #7/)).toBeInTheDocument()
    const bounds = screen.getByLabelText(/판매량 구간 상한/)
    await userEvent.clear(bounds)
    await userEvent.type(bounds, '10, 20, 40')
    await userEvent.click(screen.getByRole('button', { name: '예측시장 개설' }))

    await waitFor(() => expect(adminApi.createPredictionMarket).toHaveBeenCalledWith(expect.objectContaining({
      productionBatchId: 12,
      bucketUpperBounds: [10, 20, 40],
    }), expect.anything()))
    expect(await screen.findByRole('status')).toHaveTextContent('순환 재킷 예측시장이 열렸습니다.')
  })
})
