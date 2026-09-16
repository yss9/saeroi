import { describe, expect, it } from 'vitest'
import { getRoleHomePath } from './roleHomePath.ts'

describe('getRoleHomePath', () => {
  // 사업자 셋은 모두 회사 정보다. 업무 화면 하나를 골라 보내면 그 역할이 하는 여러 일 중
  // 하나를 임의로 앞세우는 셈이고, 사이드바가 이미 그 전부를 들고 있다.
  it.each([
    ['SUPPLIER', '/company/account'],
    ['BRAND', '/company/account'],
    ['PROCESSOR', '/company/account'],
    ['CONSUMER', '/mypage'],
    ['ADMIN', '/admin'],
  ] as const)('%s의 로그인 직후 화면을 반환한다', (role, path) => {
    expect(getRoleHomePath(role)).toBe(path)
  })
})
