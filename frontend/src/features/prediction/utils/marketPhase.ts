import type { PredictionMarket } from '../types/prediction.types.ts'

/**
 * 소비자가 보는 시장의 단계.
 *
 * 서버 상태를 그대로 쓰지 않는다. 서버는 오라클이 결과를 올릴 때까지 `OPEN` 으로 두므로, 마감이
 * 지난 시장도 `OPEN` 이다 — 그대로 보이면 "진행 중" 이라면서 참여할 수 없다.
 */
export type MarketPhase = 'OPEN' | 'COUNTING' | 'RESOLVED' | 'CANCELED'

export const MARKET_PHASE_LABEL: Record<MarketPhase, string> = {
  OPEN: '예측 진행 중',
  COUNTING: '판매 집계 중',
  RESOLVED: '결과 발표',
  CANCELED: '무효 · 환불',
}

export function marketPhase(market: PredictionMarket, now: number): MarketPhase {
  if (market.status === 'RESOLVED') return 'RESOLVED'
  if (market.status === 'CANCELED') return 'CANCELED'
  if (market.status === 'OPEN' && now < new Date(market.bettingEndsAt).getTime()) return 'OPEN'
  return 'COUNTING'
}

/** 마감까지 남은 시간. 날짜보다 "얼마나 남았나" 가 참여를 정하는 데 먼저 읽힌다. */
export function timeLeftText(deadline: number, now: number): string {
  const minutes = Math.floor((deadline - now) / 60_000)
  if (minutes < 1) return '곧 마감'
  const days = Math.floor(minutes / 1_440)
  const hours = Math.floor((minutes % 1_440) / 60)
  const rest = minutes % 60
  if (days > 0) return `${days}일 ${hours}시간 남음`
  if (hours > 0) return `${hours}시간 ${rest}분 남음`
  return `${rest}분 남음`
}

/**
 * 참여할 수 있는 시장과 지난 시장으로 나눈다.
 *
 * 섞어 두면 결과가 난 시장 사이에서 참여할 시장을 찾아야 한다. 참여할 시장은 마감이 가까운
 * 순서로, 지난 시장은 판매가 늦게 끝난(최근) 순서로 둔다.
 */
export function sortMarketsForConsumer(markets: readonly PredictionMarket[], now: number) {
  const time = (value: string) => new Date(value).getTime()
  const open = markets
    .filter((market) => marketPhase(market, now) === 'OPEN')
    .sort((left, right) => time(left.bettingEndsAt) - time(right.bettingEndsAt))
  const past = markets
    .filter((market) => marketPhase(market, now) !== 'OPEN')
    .sort((left, right) => time(right.salesEndsAt) - time(left.salesEndsAt))
  return { open, past }
}

/** 구간에 몰린 포인트의 비율(%). 아직 아무도 참여하지 않았으면 0 이다. */
export function bucketShare(pool: bigint, total: bigint): number {
  if (total <= 0n) return 0
  return Number((pool * 100n) / total)
}
