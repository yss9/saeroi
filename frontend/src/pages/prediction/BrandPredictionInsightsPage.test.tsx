import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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
  comments: [{ reasonCode: 'DESIGN', reasonText: '색감이 좋아요', submittedAt: '2026-09-20T10:00:00' }],
}

beforeEach(() => vi.mocked(predictionApi.getBrandPredictionInsights).mockResolvedValue([insight]))

describe('BrandPredictionInsightsPage', () => {
  it('shows owned market results and anonymized preferences together', async () => {
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter><BrandPredictionInsightsPage /></MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('heading', { name: '업사이클 재킷' })).toBeInTheDocument()
    expect(screen.getByText('64개 (64%)')).toBeInTheDocument()
    expect(screen.getByText('색감이 좋아요')).toBeInTheDocument()
    expect(screen.queryByText(/0x1111/)).not.toBeInTheDocument()
  })
})
