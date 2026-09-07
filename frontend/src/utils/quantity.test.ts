// 수량 변환 검증.
//
// 여기서 잡으려는 것은 단순 계산 실수가 아니라 **부동소수점 오차** 다.
// 오차가 섞인 수량이 체인에 나가면 잔량이 어긋나고 되돌릴 수 없다.

import { describe, expect, it } from 'vitest'
import { calculateTotalPrice, formatKg, formatWon, gramToKg, kgToGram } from './quantity.ts'

describe('kgToGram', () => {
  it('정수 kg 을 gram 으로 바꾼다', () => {
    expect(kgToGram(20)).toBe(20000)
  })

  // 이 케이스가 이 파일의 존재 이유다.
  // 20.3 * 1000 은 JS 에서 20299.999999999996 이다. 그대로 내보내면 1g 이 사라진다.
  it('부동소수점 오차가 나는 값도 정수로 떨어진다', () => {
    expect(kgToGram(20.3)).toBe(20300)
    expect(kgToGram(0.1 + 0.2)).toBe(300)
    expect(kgToGram(18.7)).toBe(18700)
  })

  it('음수와 비정상 값은 0 으로 막는다', () => {
    expect(kgToGram(-5)).toBe(0)
    expect(kgToGram(Number.NaN)).toBe(0)
    expect(kgToGram(Number.POSITIVE_INFINITY)).toBe(0)
  })
})

describe('gramToKg', () => {
  it('gram 을 kg 으로 바꾼다', () => {
    expect(gramToKg(20500)).toBe(20.5)
  })

  // 체인은 uint256 을 bigint 로 준다. 그대로 나눗셈하면 TypeError 가 난다.
  it('bigint 도 받는다', () => {
    expect(gramToKg(20500n)).toBe(20.5)
  })
})

describe('formatKg', () => {
  it('불필요한 소수점을 붙이지 않는다', () => {
    expect(formatKg(20000)).toBe('20 kg')
    expect(formatKg(20500)).toBe('20.5 kg')
  })

  it('천 단위를 구분한다', () => {
    expect(formatKg(1234000)).toBe('1,234 kg')
  })

  it('단위를 뺄 수 있다', () => {
    expect(formatKg(20500, { withUnit: false })).toBe('20.5')
  })
})

describe('formatWon', () => {
  // 정책 08절에서 포인트가 폐지되고 원 단위로 통일됐다.
  it('원 단위로 표시한다', () => {
    expect(formatWon(30000)).toBe('30,000원')
  })
})

describe('calculateTotalPrice', () => {
  it('kg당 단가와 gram 수량으로 총액을 구한다', () => {
    // 20kg × 1,500원/kg = 30,000원
    expect(calculateTotalPrice(20000, 1500)).toBe(30000)
  })

  // Solidity 정수 나눗셈과 맞추기 위해 내림한다.
  // 올림하면 화면 금액이 실제 체결 금액보다 커진다.
  it('나누어떨어지지 않으면 내린다', () => {
    expect(calculateTotalPrice(1500, 999)).toBe(1498) // 1498.5 → 1498
  })
})
