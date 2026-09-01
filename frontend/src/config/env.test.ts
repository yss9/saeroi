import { describe, expect, it } from 'vitest'
import { toChainIdHex } from './env.ts'

describe('toChainIdHex', () => {
  it('10진수 문자열을 16진수로 바꾼다', () => {
    expect(toChainIdHex('1337')).toBe('0x539')
  })

  it('이미 16진수면 소문자로 통일만 한다', () => {
    expect(toChainIdHex('0x5A9')).toBe('0x5a9')
  })

  // .env 의 `VITE_BESU_CHAIN_ID=` 빈 줄이 여기로 들어온다. 현재 상태가 정확히 이것이다.
  it('빈 값이면 null (체인 검증 UI 가 꺼진다)', () => {
    expect(toChainIdHex(null)).toBeNull()
  })

  it('해석 불가한 값은 null — 잘못된 설정이 조용히 통과하지 않게 한다', () => {
    expect(toChainIdHex('besu')).toBeNull()
    expect(toChainIdHex('0xZZ')).toBeNull()
    expect(toChainIdHex('12.5')).toBeNull()
  })
})
