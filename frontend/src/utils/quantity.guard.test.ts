import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * 수량·금액 변환은 `utils/quantity.ts` 에서만 한다.
 *
 * ## 왜 테스트로 막는가
 *
 * 수량은 gram 정수이고 단가는 kg 당 원이라, 화면에 보이려면 어디선가 1000 으로 나눠야 한다.
 * 그 변환이 흩어지면 두 가지가 망가진다.
 *
 * **반올림 방향이 갈린다.** 정본은 버림(`calculateTotalPrice`)인데 `ListingCard` 는 반올림을
 * 쓰고 있었다. 화면이 보여준 금액과 승인 요청 금액이 다르면 서버가 `PAYMENT_AMOUNT_MISMATCH`
 * 로 거절한다.
 *
 * **같은 수량이 화면마다 다르게 읽힌다.** `JourneyExplorer` 는 1kg 미만을 g 로 보여줬고,
 * 나머지 화면은 kg 으로 보여줬다.
 *
 * `quantity.ts` 의 주석에 "반드시 이 함수를 쓴다" 고 적혀 있었지만, 주석은 읽는 사람에게만
 * 닿는다.
 */
const SOURCE = join(process.cwd(), 'src')

/** 이 파일들은 변환의 정본이거나, 시간 계산이라 수량과 무관하다. */
const ALLOWED = new Set([
  'quantity.ts',
  'quantity.guard.test.ts',
  // 아래는 밀리초 → 초 변환이다. 수량이 아니다.
  'usePaymentCountdown.ts',
  'liveMetrics.ts',
  'BlockchainVerificationPanel.tsx',
])

/** gram 을 kg 으로 바꾸거나, 단가와 수량을 곱하는 줄. */
const CONVERSION = /\/\s*1000\b|pricePerKg\s*\*|\*\s*[a-zA-Z.]*pricePerKg/

describe('수량·금액 변환', () => {
  it('utils/quantity.ts 밖에서 직접 환산하지 않는다', () => {
    const offenders: string[] = []

    for (const file of walk(SOURCE)) {
      const name = file.split(/[/\\]/).pop() ?? ''
      if (ALLOWED.has(name)) continue

      readFileSync(file, 'utf8')
        .split('\n')
        .forEach((line, index) => {
          const code = line.trim()
          if (code.startsWith('//') || code.startsWith('*') || code.startsWith('/*')) return
          if (CONVERSION.test(line)) {
            offenders.push(`${file.replace(SOURCE, 'src')}:${index + 1}  ${code}`)
          }
        })
    }

    expect(
      offenders,
      [
        '수량과 금액 변환은 utils/quantity.ts 의 함수로만 합니다.',
        'formatKg / gramToKg / kgToGram / calculateTotalPrice 를 쓰세요.',
        '여기서 직접 나누면 반올림 방향과 표시 형식이 화면마다 갈립니다.',
      ].join('\n'),
    ).toEqual([])
  })
})

function walk(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const full = join(directory, entry)
    if (statSync(full).isDirectory()) return walk(full)
    return /\.(ts|tsx)$/.test(full) ? [full] : []
  })
}
