import { describe, expect, it } from 'vitest'
import type { PredictionMarket } from '../types/prediction.types.ts'
import { bucketShare, marketPhase, sortMarketsForConsumer, timeLeftText } from './marketPhase.ts'

const NOW = new Date('2026-09-28T00:00:00Z').getTime()

function market(overrides: Partial<PredictionMarket>): PredictionMarket {
  return {
    id: 1, status: 'OPEN', bettingEndsAt: '2026-09-29T00:00:00Z',
    salesStartsAt: '2026-09-29T00:00:00Z', salesEndsAt: '2026-10-05T00:00:00Z',
    ...overrides,
  } as PredictionMarket
}

describe('marketPhase', () => {
  it('마감 전 OPEN 만 참여할 수 있다', () => {
    expect(marketPhase(market({}), NOW)).toBe('OPEN')
  })

  // 서버는 오라클이 결과를 올릴 때까지 OPEN 으로 둔다. 마감이 지났으면 참여할 수 없다.
  it('마감이 지난 OPEN 과 RESOLVING 은 집계 중이다', () => {
    expect(marketPhase(market({ bettingEndsAt: '2026-09-27T00:00:00Z' }), NOW)).toBe('COUNTING')
    expect(marketPhase(market({ status: 'RESOLVING' }), NOW)).toBe('COUNTING')
  })

  it('결과와 무효는 그대로다', () => {
    expect(marketPhase(market({ status: 'RESOLVED' }), NOW)).toBe('RESOLVED')
    expect(marketPhase(market({ status: 'CANCELED' }), NOW)).toBe('CANCELED')
  })
})

describe('timeLeftText', () => {
  it('남은 시간을 가장 큰 단위 둘로 말한다', () => {
    expect(timeLeftText(NOW + (2 * 24 + 3) * 3_600_000, NOW)).toBe('2일 3시간 남음')
    expect(timeLeftText(NOW + 5 * 3_600_000 + 20 * 60_000, NOW)).toBe('5시간 20분 남음')
    expect(timeLeftText(NOW + 45 * 60_000, NOW)).toBe('45분 남음')
    expect(timeLeftText(NOW + 20_000, NOW)).toBe('곧 마감')
  })
})

describe('sortMarketsForConsumer', () => {
  it('참여할 수 있는 시장을 마감이 가까운 순서로 먼저 보인다', () => {
    const later = market({ id: 1, bettingEndsAt: '2026-10-01T00:00:00Z' })
    const sooner = market({ id: 2, bettingEndsAt: '2026-09-28T06:00:00Z' })
    const resolved = market({ id: 3, status: 'RESOLVED', salesEndsAt: '2026-09-20T00:00:00Z' })
    const counting = market({ id: 4, status: 'RESOLVING' })

    const { open, past } = sortMarketsForConsumer([resolved, later, counting, sooner], NOW)

    expect(open.map((item) => item.id)).toEqual([2, 1])
    expect(past.map((item) => item.id)).toEqual([4, 3])
  })
})

describe('bucketShare', () => {
  it('구간에 몰린 포인트 비율을 정수 퍼센트로 준다', () => {
    expect(bucketShare(25n, 100n)).toBe(25)
    expect(bucketShare(1n, 3n)).toBe(33)
    expect(bucketShare(0n, 0n)).toBe(0)
  })
})
