import { describe, expect, it } from 'vitest'
import { getRoleHomePath } from './roleHomePath.ts'

describe('getRoleHomePath', () => {
  it.each([
    ['SUPPLIER', '/supplier/listings'],
    ['BRAND', '/brand/processing'],
    ['PROCESSOR', '/processor/jobs'],
    ['CONSUMER', '/mypage'],
    ['ADMIN', '/admin'],
  ] as const)('%s의 기본 업무 화면을 반환한다', (role, path) => {
    expect(getRoleHomePath(role)).toBe(path)
  })
})
