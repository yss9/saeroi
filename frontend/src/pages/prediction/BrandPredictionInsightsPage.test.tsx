import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as predictionApi from '../../features/prediction/api/predictionApi.ts'
import type { BrandPredictionInsight } from '../../features/prediction/types/prediction.types.ts'
import { BrandPredictionInsightsPage } from './BrandPredictionInsightsPage.tsx'

vi.mock('../../features/prediction/api/predictionApi.ts', async (importOriginal) => ({
  ...await importOriginal<typeof predictionApi>(),
  getBrandPredictionInsights: vi.fn(),
}))

const insight: BrandPredictionInsight = {
  market: {
    id: 31,
    chainMarketId: 9,
    productionBatchId: 11,
    productModelId: 13,
    productName: '업사이클 재킷',
    productThumbnailUrl: null,
    unitPrice: 120000,
    productionQuantity: 100,
    brandWallet: '0x1111111111111111111111111111111111111111',
    bettingEndsAt: '2026-09-21T00:00:00',
    salesStartsAt: '2026-09-22T00:00:00',
    salesEndsAt: '2026-09-30T00:00:00',
    status: 'RESOLVED',
    soldQuantity: 64,
    winningBucketIndex: 1,
    resultHash: '0xresult',
    buckets: [{ index: 0, lowerBound: 0, upperBound: 49 }, { index: 1, lowerBound: 50, upperBound: 100 }],
  },
  participantCount: 2,
  totalStakeAmount: '35000000000000000000',
  buckets: [
    { index: 0, lowerBound: 0, upperBound: 49, responseCount: 1, stakeAmount: '10000000000000000000' },
    { index: 1, lowerBound: 50, upperBound: 100, responseCount: 1, stakeAmount: '25000000000000000000' },
  ],
  reasons: [
    { reasonCode: 'DESIGN', responseCount: 1 },
    { reasonCode: 'PRICE', responseCount: 1 },
    { reasonCode: 'BRAND', responseCount: 0 },
    { reasonCode: 'SUSTAINABILITY', responseCount: 0 },
    { reasonCode: 'PRACTICALITY', responseCount: 0 },
    { reasonCode: 'TREND', responseCount: 0 },
    { reasonCode: 'OTHER', responseCount: 0 },
  ],
  comments: [
    { reasonCode: 'DESIGN', reasonText: '색감이 좋아요', submittedAt: '2026-09-20T10:00:00' },
    { reasonCode: 'PRICE', reasonText: '가격이 조금 높아요', submittedAt: '2026-09-20T11:00:00' },
  ],
}

beforeEach(() => vi.mocked(predictionApi.getBrandPredictionInsights).mockResolvedValue([insight]))
afterEach(cleanup)

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter><BrandPredictionInsightsPage /></MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('BrandPredictionInsightsPage', () => {
  it('shows owned market results and anonymized preferences together', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: '업사이클 재킷' })).toBeInTheDocument()
    expect(screen.getByText('64개 (64%)')).toBeInTheDocument()
    expect(screen.getByText('색감이 좋아요')).toBeInTheDocument()
    expect(screen.queryByText(/0x1111/)).not.toBeInTheDocument()
  })

  // 중앙 구간·기대 판매량·오차는 화면이 계산한다. 계산이 문장으로 이어지는지 본다.
  // 예치 가중 기대 = (24.5·10 + 75·25) / 35 ≈ 60.6 → 61개, 실제 64개 → 오차 −5%
  it('상품마다 예측 요약과 적중률을 보인다', async () => {
    renderPage()

    const card = (await screen.findByRole('heading', { name: '업사이클 재킷' })).closest('article') as HTMLElement
    expect(within(card).getByText('61개')).toBeInTheDocument()
    expect(within(card).getByText('−5%')).toBeInTheDocument()
    // 적중 구간(50~100)을 고른 사람은 2명 중 1명
    expect(within(card).getAllByText('50%').length).toBeGreaterThan(0)
  })

  it('전체 지표와 상품 사이 예측·실제 비교를 함께 보인다', async () => {
    renderPage()

    const kpis = await screen.findByLabelText('전체 지표')
    expect(within(kpis).getByText('평균 적중률')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '소비자 예측 vs 실제 판매' })).toBeInTheDocument()
    expect(screen.getByLabelText('업사이클 재킷: 예측 61개 · 실제 64개 · 생산 100개')).toBeInTheDocument()
  })

  it('분포를 예치 포인트 기준으로 바꿔 본다', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '예치 포인트' }))

    expect(screen.getByRole('button', { name: '예치 포인트' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText(/예치 포인트 기준/)).toBeInTheDocument()
  })

  it('의견을 이유로 거른다', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '가격 1' }))

    expect(screen.getByText('가격이 조금 높아요')).toBeInTheDocument()
    expect(screen.queryByText('색감이 좋아요')).not.toBeInTheDocument()
  })
})
