// 서명 메시지 형식을 리터럴로 고정한다.
//
// Backend 의 WalletLoginMessage.build() 와 같은 문자열이어야 한다. 한쪽만 바뀌면
// 컴파일도 되고 다른 테스트도 통과하지만 실서비스 로그인이 전멸한다.
// 이 파일과 Backend 의 WalletLoginMessageTests 가 같은 값을 지키는 한 쌍이다.

import { describe, expect, it } from 'vitest'
import { buildLoginMessage } from '../services/walletMessage.ts'

const CHECKSUM = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
const NONCE = '0123456789abcdef0123456789abcdef'

describe('buildLoginMessage', () => {
  it('Backend 계약과 한 글자도 다르지 않다', () => {
    const expected =
      'SAEROI 로그인 요청\n' +
      '\n' +
      `지갑 주소: ${CHECKSUM}\n` +
      `Nonce: ${NONCE}\n` +
      '\n' +
      '이 서명은 로그인 용도로만 사용되며 자산이 이동하지 않습니다.'

    expect(buildLoginMessage(CHECKSUM, NONCE)).toBe(expected)
  })

  it('빈 줄 두 개를 낀 여섯 줄이다', () => {
    const lines = buildLoginMessage(CHECKSUM, NONCE).split('\n')

    expect(lines).toHaveLength(6)
    expect(lines[1]).toBe('')
    expect(lines[4]).toBe('')
  })

  it('CR 을 포함하지 않는다', () => {
    // Windows 에서 편집하다 CRLF 가 섞이면 서명이 전부 어긋난다.
    expect(buildLoginMessage(CHECKSUM, NONCE)).not.toContain('\r')
  })

  it('UTF-8 바이트 길이가 Backend 와 같다', () => {
    // EIP-191 프리픽스에 들어가는 값이 이 바이트 길이다 (문자 수 140 이 아니다).
    // Backend WalletLoginMessageTests 가 같은 210 을 단언한다.
    expect(new TextEncoder().encode(buildLoginMessage(CHECKSUM, NONCE))).toHaveLength(210)
  })
})
