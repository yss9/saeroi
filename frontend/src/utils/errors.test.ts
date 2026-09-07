// toErrorMessage 의 계약.
//
// `ErrorMessage.error` 주석이 "AppError, Error, 문자열 무엇이든 받는다" 라고 약속하고
// 세 페이지(Material·Listing·Trade 상세)가 문자열 형태로 쓰고 있다. 그런데 문자열
// 분기가 없어서 그 문구가 전부 "알 수 없는 오류가 발생했습니다." 로 바뀌고 있었다 —
// 원인을 아는 상황에서 모른다고 말하는 화면이 세 개 있었던 것이다.
//
// 조용히 되돌아갈 수 있는 종류의 버그라 테스트로 못 박는다.

import { describe, expect, it } from 'vitest'
import { createAppError, ERROR_KIND, toErrorMessage } from './errors.ts'

// DEFAULT_MESSAGE 는 export 되어 있지 않다. 남의 모듈 공개 API 를 넓히지 않으려고
// 값을 직접 적는다 — 문구가 바뀌면 이 테스트가 알려 준다.
const UNKNOWN = '알 수 없는 오류가 발생했습니다.'

describe('toErrorMessage', () => {
  it('AppError 의 message 를 쓴다', () => {
    const error = createAppError(ERROR_KIND.API_ERROR, { message: '소재를 찾을 수 없습니다.' })

    expect(toErrorMessage(error)).toBe('소재를 찾을 수 없습니다.')
  })

  it('Error 의 message 를 쓴다', () => {
    expect(toErrorMessage(new Error('네트워크가 끊겼습니다.'))).toBe('네트워크가 끊겼습니다.')
  })

  it('문자열은 그대로 쓴다', () => {
    expect(toErrorMessage('잘못된 소재 주소입니다.')).toBe('잘못된 소재 주소입니다.')
  })

  it('빈 문자열은 기본 문구로 대체한다', () => {
    // 빈 말풍선을 띄우면 사용자는 화면이 깨진 것으로 읽는다.
    expect(toErrorMessage('')).toBe(UNKNOWN)
    expect(toErrorMessage('   ')).toBe(UNKNOWN)
  })

  it('문구를 만들 수 없는 값은 기본 문구로 줄인다', () => {
    for (const value of [null, undefined, 0, 42, {}, [], true]) {
      expect(toErrorMessage(value)).toBe(UNKNOWN)
    }
  })
})
