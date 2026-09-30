import type { BrandPredictionInsight, PredictionBucket } from '../types/prediction.types.ts'

/**
 * 브랜드 예측·선호 분석 화면이 그리는 수치.
 *
 * 서버는 구간별 응답 수·예치량과 결과만 준다. "소비자는 몇 개 팔릴 거라 봤는가" 를 한 숫자로
 * 말하려면 여기서 계산해야 하고, 그 계산은 화면과 떼어 둬야 시험할 수 있다.
 *
 * 구간은 0 부터 생산 수량까지 빈틈없이 이어지고 양 끝을 포함한다(서버의 구간 생성 규칙).
 */
export interface MarketStats {
  /** 참여자 누적이 절반을 넘는 구간. 참여자가 없으면 null */
  medianBucketIndex: number | null
  /** 구간 가운데 값을 인원으로 가중한 평균 */
  expectedByCount: number | null
  /** 구간 가운데 값을 예치 포인트로 가중한 평균 — 확신을 걸고 고른 예측 */
  expectedByStake: number | null
  /** 적중 구간을 고른 참여자 비율(0~1). 확정 전이면 null */
  hitRate: number | null
  /** 판매 ÷ 생산(0~1). 확정 전이면 null */
  sellThrough: number | null
  /** (예치 가중 기대 − 실제) ÷ 실제. 확정 전이거나 실제가 0 이면 null */
  errorRate: number | null
}

export interface InsightsSummary {
  marketCount: number
  resolvedCount: number
  participantCount: number
  totalStake: bigint
  /** 확정된 시장들의 참여자 수로 가중한 적중률. 확정된 시장이 없으면 null */
  hitRate: number | null
}

/** 예치량은 18자리 정수 문자열이다. 비율만 쓰므로 정밀도를 조금 잃어도 된다. */
function stakeOf(value: string): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function midpoint(bucket: PredictionBucket): number {
  return (bucket.lowerBound + bucket.upperBound) / 2
}

function weightedMean(values: readonly number[], weights: readonly number[]): number | null {
  const total = weights.reduce((sum, weight) => sum + weight, 0)
  if (total <= 0) return null
  return values.reduce((sum, value, index) => sum + value * weights[index], 0) / total
}

function isResolved(insight: BrandPredictionInsight): boolean {
  return insight.market.status === 'RESOLVED' && insight.market.soldQuantity !== null
}

function winningCount(insight: BrandPredictionInsight): number {
  const winning = insight.buckets.find((bucket) => bucket.index === insight.market.winningBucketIndex)
  return winning?.responseCount ?? 0
}

export function marketStats(insight: BrandPredictionInsight): MarketStats {
  const buckets = [...insight.buckets].sort((left, right) => left.index - right.index)
  const counts = buckets.map((bucket) => bucket.responseCount)
  const midpoints = buckets.map(midpoint)
  const participants = counts.reduce((sum, count) => sum + count, 0)

  let medianBucketIndex: number | null = null
  let cumulative = 0
  for (const bucket of buckets) {
    cumulative += bucket.responseCount
    if (participants > 0 && cumulative * 2 > participants) {
      medianBucketIndex = bucket.index
      break
    }
  }

  const expectedByCount = weightedMean(midpoints, counts)
  const expectedByStake = weightedMean(midpoints, buckets.map((bucket) => stakeOf(bucket.stakeAmount)))

  if (!isResolved(insight)) {
    return { medianBucketIndex, expectedByCount, expectedByStake, hitRate: null, sellThrough: null, errorRate: null }
  }

  const sold = insight.market.soldQuantity as number
  const production = insight.market.productionQuantity
  return {
    medianBucketIndex,
    expectedByCount,
    expectedByStake,
    hitRate: participants > 0 ? winningCount(insight) / participants : null,
    sellThrough: production > 0 ? sold / production : null,
    errorRate: sold > 0 && expectedByStake !== null ? (expectedByStake - sold) / sold : null,
  }
}

/**
 * 수량이 구간 막대 줄의 어디에 오는가. 정수부는 구간 번호, 소수부는 그 칸 안의 비례 위치다.
 *
 * 구간 폭이 서로 달라 수량 축을 곧게 그을 수 없다. 대신 값이 든 칸 안에서만 비례시킨다 —
 * 실제 판매량 선이 적중 구간 막대 위에 정확히 선다.
 */
export function positionInBuckets(buckets: readonly PredictionBucket[], value: number): number {
  const sorted = [...buckets].sort((left, right) => left.index - right.index)
  if (sorted.length === 0 || value < sorted[0].lowerBound) return 0
  for (let slot = 0; slot < sorted.length; slot++) {
    const { lowerBound, upperBound } = sorted[slot]
    if (value <= upperBound) return slot + (value - lowerBound + 0.5) / (upperBound - lowerBound + 1)
  }
  return sorted.length
}

export function summarizeInsights(insights: readonly BrandPredictionInsight[]): InsightsSummary {
  const resolved = insights.filter(isResolved)
  const resolvedParticipants = resolved.reduce((sum, item) => sum + item.participantCount, 0)
  return {
    marketCount: insights.length,
    resolvedCount: resolved.length,
    participantCount: insights.reduce((sum, item) => sum + item.participantCount, 0),
    totalStake: insights.reduce((sum, item) => sum + BigInt(item.totalStakeAmount), 0n),
    hitRate: resolvedParticipants > 0
      ? resolved.reduce((sum, item) => sum + winningCount(item), 0) / resolvedParticipants
      : null,
  }
}
