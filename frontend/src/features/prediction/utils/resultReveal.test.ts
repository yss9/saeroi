import { describe, expect, it } from 'vitest'
import { buildResultRevealSteps, MIN_RESULT_REVEAL_DURATION_MS } from './resultReveal.ts'

describe('buildResultRevealSteps', () => {
  it('초반보다 정답 근처의 간격이 길고 마지막 값에 정확히 멈춘다', () => {
    const steps = buildResultRevealSteps(63)

    expect(steps.at(-1)?.value).toBe(63)
    expect(steps.at(-1)!.delayMs).toBeGreaterThan(steps[0].delayMs)
    expect(steps.every((step, index) => index === 0 || step.value > steps[index - 1].value)).toBe(true)
  })

  it('작은 판매량과 0도 최종값을 잃지 않는다', () => {
    const smallResult = buildResultRevealSteps(3)
    const zeroResult = buildResultRevealSteps(0)

    expect(smallResult.at(-1)?.value).toBe(3)
    expect(smallResult.reduce((total, step) => total + step.delayMs, 0)).toBeGreaterThanOrEqual(MIN_RESULT_REVEAL_DURATION_MS)
    expect(zeroResult).toEqual([{ value: 0, delayMs: MIN_RESULT_REVEAL_DURATION_MS }])
  })
})
