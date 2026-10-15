import { describe, expect, it } from 'vitest'
import { toBlockNumber, toChainIdHex } from './env.ts'

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

/**
 * <b>온체인 로그 조회 시작 블록.</b>
 *
 * 이 값이 0 으로 떨어지면 `queryFilter` 가 제네시스부터 훑는다. 틀린 결과가 나오지는
 * 않지만 SSAFY 체인에서 <b>33초</b>가 걸리고(2026-09-11 실측, 블록 1,122만) 그 시간이
 * 통째로 MetaMask 서명 팝업 앞을 막는다. 배포 블록부터면 같은 질의가 455ms 다.
 *
 * 그래서 파싱이 조용히 0 으로 떨어지는 경우를 여기서 고정한다 — 느려지는 것은
 * 화면에 오류로 드러나지 않아서 눈치채기 어렵다.
 */
describe('toBlockNumber', () => {
  it('10진수 블록 번호를 정수로 읽는다', () => {
    expect(toBlockNumber('11221171')).toBe(11221171)
  })

  it('16진수도 읽는다', () => {
    expect(toBlockNumber('0xab3a73')).toBe(0xab3a73)
  })

  it('빈 값이면 0 — 예전 동작(제네시스부터)으로 돌아간다', () => {
    // 느릴 뿐 틀리지 않는다. 설정이 빠졌다고 발행 자체를 막으면 더 나쁘다.
    expect(toBlockNumber(null)).toBe(0)
  })

  it('해석 불가한 값도 0 으로 떨어뜨린다', () => {
    // NaN 이 그대로 fromBlock 에 들어가면 ethers 가 던진다. 발행이 통째로 막힌다.
    expect(toBlockNumber('나중에')).toBe(0)
  })

  it('음수는 받지 않는다', () => {
    expect(toBlockNumber('-1')).toBe(0)
  })
})
