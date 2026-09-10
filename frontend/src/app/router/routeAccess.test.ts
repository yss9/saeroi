import { describe, expect, it } from 'vitest'
import { canAccess, getRouteAccess, getRouteAccessMap, isRoleResolving } from './routeAccess.ts'

describe('routeAccess — routes.tsx 에서 파생한 접근 조건', () => {
  it('공개 경로는 비회원도 볼 수 있다', () => {
    for (const path of ['/', '/qr/:qrToken', '/403']) {
      const access = getRouteAccess(path)
      expect(access, path).toEqual({ kind: 'public' })
      expect(canAccess(access!, { isAuthenticated: false, role: null }), path).toBe(true)
    }
  })

  /**
   * <b>비회원이 볼 수 있는 것은 이 여덟 개가 전부다.</b>
   *
   * 위 검사는 "이것들이 공개인가" 만 본다. 공개 경로가 하나 <b>늘어나는</b> 것은 잡지
   * 못하는데, 실수는 그쪽으로 난다 — 라우트를 ProtectedRoute 밖에 두는 것은 한 줄이면
   * 되고 화면은 멀쩡히 뜨기 때문에 리뷰에서 눈에 띄지 않는다.
   *
   * 실제로 그렇게 새어 있었다. 마켓플레이스·소재 상세가 공개라 비회원에게 필터바만
   * 뜨고 목록은 401 로 비어 있었고, 소재를 사지도 팔지도 않는 CONSUMER 에게까지
   * 메뉴가 보였다. 비회원에게 보여줄 것은 <b>완성된 제품을 파는 쇼핑몰</b>과 QR 여권
   * 뿐이라는 것이 결정이고, 그 결정을 여기서 잠근다.
   *
   * ⚠️ 이 목록을 늘리기 전에 두 가지를 확인할 것.
   *   1. 그 화면이 부르는 API 가 SecurityConfig 에서 열려 있는가. 아니면 비회원은
   *      화면만 보고 데이터는 401 을 받는다 — 없는 것보다 나쁘다.
   *   2. 로그인한 사용자가 들어가도 셸을 잃지 않는가 (shellCoverage.test.ts).
   */
  it('비회원에게 열린 경로는 이 목록이 전부다', () => {
    const EXPECTED_PUBLIC: ReadonlyMap<string, string> = new Map([
      ['/', '쇼핑몰 랜딩. 비회원이 보는 유일한 서비스 화면이다'],
      ['/login', '인증 흐름'],
      ['/signup', '인증 흐름'],
      ['/signup/consumer', '인증 흐름'],
      ['/signup/business', '인증 흐름'],
      ['/signup/complete', '가입 직후. 아직 로그인 상태가 아니다'],
      ['/qr/:qrToken', 'QR 여권. 소비자가 제품을 찍고 들어오는 입구라 로그인을 요구하면 서비스 목적이 무너진다'],
      ['/403', '가드가 보내는 곳. 이 화면 자체가 막히면 갈 곳이 없어진다'],
    ])

    const actual = [...getRouteAccessMap()]
      .filter(([, access]) => access.kind === 'public')
      .map(([path]) => path)

    expect(new Set(actual)).toEqual(new Set(EXPECTED_PUBLIC.keys()))
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
