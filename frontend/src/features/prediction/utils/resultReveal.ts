export interface ResultRevealStep {
  value: number
  delayMs: number
}

/**
 * 초반에는 큰 폭으로 빠르게 올라가고, 정답에 가까워질수록 작은 폭으로 천천히 멈춘다.
 * 같은 정수가 연속으로 나오면 계단이 멈춘 것처럼 보이므로 중복 값은 제거한다.
 */
export function buildResultRevealSteps(target: number): ResultRevealStep[] {
  const finalValue = Math.max(0, Math.floor(target))
  if (finalValue === 0) return [{ value: 0, delayMs: 0 }]

  const sampleCount = Math.min(24, Math.max(8, finalValue))
  const steps: ResultRevealStep[] = []

  for (let index = 1; index <= sampleCount; index += 1) {
    const progress = index / sampleCount
    const easedProgress = 1 - ((1 - progress) ** 3)
    const value = Math.min(finalValue, Math.round(finalValue * easedProgress))
    if (steps.at(-1)?.value === value) continue

    steps.push({
      value,
      delayMs: 35 + Math.round(200 * (progress ** 2)),
    })
  }

  if (steps.at(-1)?.value !== finalValue) {
    steps.push({ value: finalValue, delayMs: 235 })
  }
  return steps
}
