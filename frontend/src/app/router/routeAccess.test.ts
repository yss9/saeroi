import { describe, expect, it } from 'vitest'
import { canAccess, getRouteAccess, isRoleResolving } from './routeAccess.ts'

describe('routeAccess — routes.tsx 에서 파생한 접근 조건', () => {
  it('공개 경로는 비회원도 볼 수 있다', () => {
    for (const path of ['/', '/materials/:materialId', '/marketplace', '/qr/:qrToken', '/403']) {
      const access = getRouteAccess(path)
      expect(access, path).toEqual({ kind: 'public' })
      expect(canAccess(access!, { isAuthenticated: false, role: null }), path).toBe(true)
    }
  })

  // RoleGuard 가 없는 Protected 경로. 로그인만 하면 역할과 무관하게 들어간다.
  // 여기에 역할 조건을 새로 붙이지 않는다 — 권한 Matrix 가 정하지 않은 부분이다.
  it('가드 없는 Protected 경로는 로그인만 요구한다', () => {
    for (const path of ['/inventory', '/trades', '/trades/:tradeId', '/products', '/products/:productId']) {
      expect(getRouteAccess(path), path).toEqual({ kind: 'auth' })
    }
    expect(canAccess({ kind: 'auth' }, { isAuthenticated: false, role: null })).toBe(false)
    expect(canAccess({ kind: 'auth' }, { isAuthenticated: true, role: 'CONSUMER' })).toBe(true)
  })

  it('RoleGuard 가 걸린 경로는 allow 목록을 그대로 파생한다', () => {
    expect(getRouteAccess('/mypage')).toEqual({ kind: 'role', allow: ['CONSUMER'] })
    expect(getRouteAccess('/company/account')).toEqual({
      kind: 'role',
      allow: ['SUPPLIER', 'BRAND', 'PROCESSOR'],
    })
    expect(getRouteAccess('/supplier/listings')).toEqual({ kind: 'role', allow: ['SUPPLIER'] })
    expect(getRouteAccess('/brand/products/new')).toEqual({ kind: 'role', allow: ['BRAND'] })
    expect(getRouteAccess('/processor/jobs')).toEqual({ kind: 'role', allow: ['PROCESSOR'] })
    expect(getRouteAccess('/admin')).toEqual({ kind: 'role', allow: ['ADMIN'] })
  })

  it('정의되지 않은 경로는 undefined 다', () => {
    expect(getRouteAccess('/nope')).toBeUndefined()
    // 와일드카드는 "존재하는 경로" 로 취급하지 않는다 — 메뉴 항목이 오타여도
    // * 에 걸려 통과하면 링크 깨짐을 놓친다.
    expect(getRouteAccess('*')).toBeUndefined()
  })

  // 인증됐지만 역할을 아직 모르는 구간. RoleGuard 가 같은 조합을 판정 보류로 다룬다.
  it('역할 판정 중 여부를 인증 상태와 role 로 구분한다', () => {
    expect(isRoleResolving({ isAuthenticated: true, role: null })).toBe(true)
    expect(isRoleResolving({ isAuthenticated: true, role: 'BRAND' })).toBe(false)
    expect(isRoleResolving({ isAuthenticated: false, role: null })).toBe(false)
  })

  it('역할을 모르는 동안에는 역할 전용 경로를 열지 않는다', () => {
    const access = getRouteAccess('/admin')!
    expect(canAccess(access, { isAuthenticated: true, role: null })).toBe(false)
  })
})
