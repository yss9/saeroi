import { describe, expect, it } from 'vitest'
import type { BrandPredictionInsight } from '../types/prediction.types.ts'
import { marketStats, positionInBuckets, summarizeInsights } from './predictionInsightStats.ts'

const PRED = 10n ** 18n

function insight(overrides: {
  counts: number[]
  stakes: number[]
  bounds?: [number, number][]
  production?: number
  sold?: number | null
  winning?: number | null
  status?: BrandPredictionInsight['market']['status']
}): BrandPredictionInsight {
  const bounds = overrides.bounds ?? [[0, 49], [50, 99], [100, 149], [150, 200]]
  const buckets = bounds.map(([lowerBound, upperBound], index) => ({
    index,
    lowerBound,
    upperBound,
    responseCount: overrides.counts[index],
    stakeAmount: (BigInt(overrides.stakes[index]) * PRED).toString(),
  }))
  const participantCount = overrides.counts.reduce((sum, value) => sum + value, 0)
  const totalStake = overrides.stakes.reduce((sum, value) => sum + value, 0)
  return {
    market: {
      id: 1,
      chainMarketId: 1,
      productionBatchId: 1,
      productModelId: 1,
      productName: '재킷',
      productThumbnailUrl: null,
      unitPrice: 100_000,
      productionQuantity: overrides.production ?? 200,
      brandWallet: '0x0',
      bettingEndsAt: '2026-09-21T00:00:00',
      salesStartsAt: '2026-09-22T00:00:00',
      salesEndsAt: '2026-09-30T00:00:00',
      status: overrides.status ?? (overrides.sold == null ? 'OPEN' : 'RESOLVED'),
      soldQuantity: overrides.sold ?? null,
      winningBucketIndex: overrides.winning ?? null,
      resultHash: null,
      buckets: buckets.map(({ index, lowerBound, upperBound }) => ({ index, lowerBound, upperBound })),
    },
    participantCount,
    totalStakeAmount: (BigInt(totalStake) * PRED).toString(),
    buckets,
    reasons: [],
    comments: [],
  }
}

describe('marketStats', () => {
  it('참여자 절반이 넘어가는 구간을 중앙 구간으로 본다', () => {
    // 누적 1 → 4 → 9 → 10. 10명의 절반(5)을 넘는 것은 세 번째 구간이다.
    const stats = marketStats(insight({ counts: [1, 3, 5, 1], stakes: [1, 1, 1, 1] }))

    expect(stats.medianBucketIndex).toBe(2)
  })

  it('기대 판매량은 구간 가운데 값을 인원·예치로 각각 가중한 평균이다', () => {
    // 가운데 값 24.5 / 74.5 / 124.5 / 175
    const stats = marketStats(insight({ counts: [1, 1, 0, 0], stakes: [30, 10, 0, 0] }))

    expect(stats.expectedByCount).toBeCloseTo((24.5 + 74.5) / 2)
    expect(stats.expectedByStake).toBeCloseTo((24.5 * 30 + 74.5 * 10) / 40)
  })

  it('결과가 확정되면 적중률·판매율·오차를 낸다', () => {
    const stats = marketStats(insight({
      counts: [2, 6, 2, 0], stakes: [10, 30, 10, 0], sold: 80, winning: 1, production: 200,
    }))

    expect(stats.hitRate).toBeCloseTo(0.6)
    expect(stats.sellThrough).toBeCloseTo(0.4)
    // 예치 가중 기대 = (24.5·10 + 74.5·30 + 124.5·10) / 50 = 74.5 → (74.5 − 80) / 80
    expect(stats.errorRate).toBeCloseTo((74.5 - 80) / 80)
  })

  it('확정 전에는 결과에 기대는 값을 비운다', () => {
    const stats = marketStats(insight({ counts: [1, 1, 0, 0], stakes: [1, 1, 0, 0] }))

    expect(stats.hitRate).toBeNull()
    expect(stats.sellThrough).toBeNull()
    expect(stats.errorRate).toBeNull()
  })

  it('참여자가 없으면 예측값을 지어내지 않는다', () => {
    const stats = marketStats(insight({ counts: [0, 0, 0, 0], stakes: [0, 0, 0, 0] }))

    expect(stats.medianBucketIndex).toBeNull()
    expect(stats.expectedByCount).toBeNull()
    expect(stats.expectedByStake).toBeNull()
  })

  it('실제 판매가 0개면 오차를 비율로 낼 수 없다', () => {
    const stats = marketStats(insight({ counts: [1, 0, 0, 0], stakes: [1, 0, 0, 0], sold: 0, winning: 0 }))

    expect(stats.errorRate).toBeNull()
    expect(stats.sellThrough).toBe(0)
  })
})

describe('positionInBuckets', () => {
  const buckets = [
    { index: 0, lowerBound: 0, upperBound: 49 },
    { index: 1, lowerBound: 50, upperBound: 99 },
  ]

  it('값이 든 구간의 칸 안에서 비례한 위치를 준다', () => {
    expect(positionInBuckets(buckets, 0)).toBeCloseTo(0.5 / 50)
    expect(positionInBuckets(buckets, 75)).toBeCloseTo(1 + 25.5 / 50)
  })

  it('범위를 벗어나면 양 끝에 붙인다', () => {
    expect(positionInBuckets(buckets, -3)).toBe(0)
    expect(positionInBuckets(buckets, 500)).toBe(2)
  })
})

describe('summarizeInsights', () => {
  it('적중률은 확정된 시장의 참여자 수로 가중한다', () => {
    const summary = summarizeInsights([
      insight({ counts: [1, 9, 0, 0], stakes: [1, 9, 0, 0], sold: 60, winning: 1 }), // 9/10
      insight({ counts: [1, 1, 0, 0], stakes: [1, 1, 0, 0], sold: 10, winning: 0 }), // 1/2
      insight({ counts: [5, 5, 0, 0], stakes: [5, 5, 0, 0] }), // 확정 전 — 적중률에서 빠진다
    ])

    expect(summary.marketCount).toBe(3)
    expect(summary.resolvedCount).toBe(2)
    expect(summary.participantCount).toBe(22)
    expect(summary.totalStake).toBe(22n * PRED)
    expect(summary.hitRate).toBeCloseTo(10 / 12)
  })

  it('확정된 시장이 없으면 적중률을 비운다', () => {
    expect(summarizeInsights([insight({ counts: [1, 0, 0, 0], stakes: [1, 0, 0, 0] })]).hitRate).toBeNull()
  })
})
