import { isValidElement } from 'react'
import { matchRoutes, type RouteObject } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { RoleGuard, type RoleGuardProps } from '../router/guards/RoleGuard.tsx'
import { routes } from '../router/routes.tsx'
import { canAccess, getRouteAccess, getRouteAccessMap } from '../router/routeAccess.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { USER_ROLES, type UserRole } from '../../types/role.types.ts'
import { ACCOUNT_MENU_ITEMS, ALL_MENU_ITEMS, BUSINESS_MENU_ITEMS } from './navigationMenu.ts'

/**
 * routes.tsx 의 RoleGuard allow 를 **routeAccess 와 무관한 방법**으로 다시 읽는다.
 *
 * routeAccess 는 트리를 직접 훑지만 여기서는 react-router 의 matchRoutes 로
 * 경로를 해석해 매치된 분기만 본다. 두 방법이 같은 답을 내야 한다 — 한쪽만 틀리면
 * "메뉴는 보이는데 누르면 403" 이 되기 때문에, 그 어긋남을 이 테스트가 잡는다.
 */
function allowFromMatchedBranch(path: string): readonly UserRole[] | null {
  const matches = matchRoutes(routes as RouteObject[], path)
  if (matches === null) return null

  let allow: readonly UserRole[] | null = null
  for (const match of matches) {
    const element = match.route.element
    if (isValidElement(element) && element.type === RoleGuard) {
      const guardAllow = (element.props as RoleGuardProps).allow
      allow = allow === null ? guardAllow : allow.filter((role) => guardAllow.includes(role))
    }
  }
  return allow
}

function signInAs(role: UserRole) {
  useAuthStore
    .getState()
    .signIn({ id: 1, email: 'a@b.c', name: '테스터', role, walletAddress: null }, 'test-token')
}

beforeEach(() => {
  localStorage.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

afterEach(() => {
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  localStorage.clear()
})

describe('메뉴 정의와 라우트 정의', () => {
  // 경로 오타나 라우트 삭제로 메뉴가 404 로 이어지는 것을 막는다.
  it('모든 메뉴 항목의 경로가 routes.tsx 에 존재한다', () => {
    const unknown = ALL_MENU_ITEMS.filter((item) => getRouteAccess(item.to) === undefined)

    expect(unknown.map((item) => `${item.label} → ${item.to}`)).toEqual([])
  })

  // 이 테스트가 이 기능의 핵심 안전장치다. 메뉴 노출 조건은 routes.tsx 에서
  // 파생되므로 원래 어긋날 수 없지만, 파생 자체가 틀리면(가드 중첩 구조가 바뀌는 등)
  // 조용히 넓어질 수 있다. matchRoutes 로 다시 읽어 대조한다.
  it('메뉴 노출 조건이 그 경로의 RoleGuard allow 와 일치한다', () => {
    const mismatches: string[] = []

    for (const item of ALL_MENU_ITEMS) {
      const derived = getRouteAccess(item.to)
      const guardAllow = allowFromMatchedBranch(item.to)
      const derivedAllow = derived?.kind === 'role' ? derived.allow : null

      if (JSON.stringify(derivedAllow) !== JSON.stringify(guardAllow)) {
        mismatches.push(`${item.to}: 메뉴=${JSON.stringify(derivedAllow)} 라우터=${JSON.stringify(guardAllow)}`)
      }
    }

    expect(mismatches).toEqual([])
  })

  // 위 대조를 역할 단위까지 내려서 한 번 더 본다. allow 배열이 같아도
  // canAccess 가 잘못 계산하면(예: 미인증에 auth 경로를 열어 주면) 여기서 걸린다.
  it('역할별로 메뉴가 보이는지 여부가 라우터 판정과 같다', () => {
    const mismatches: string[] = []

    for (const item of ALL_MENU_ITEMS) {
      const access = getRouteAccess(item.to)
      if (access === undefined) continue
      const guardAllow = allowFromMatchedBranch(item.to)

      for (const role of USER_ROLES) {
        const menuShows = canAccess(access, { isAuthenticated: true, role })
        // 라우터 기준: RoleGuard 가 있으면 allow 포함 여부, 없으면 로그인만으로 통과
        const routerAllows = guardAllow === null ? true : guardAllow.includes(role)

        if (menuShows !== routerAllows) {
          mismatches.push(`${item.to} / ${role}: 메뉴=${menuShows} 라우터=${routerAllows}`)
        }
      }
    }

    expect(mismatches).toEqual([])
  })

  // 역할 조건이 코드 밖(정본은 routes.tsx)에서 왔다는 사실을 문서화하는 성격의 검사.
  // 메뉴 항목 객체에 roles 같은 필드가 다시 생기면 실패한다.
  it('메뉴 항목 자체는 역할 조건을 들고 있지 않다', () => {
    for (const item of ALL_MENU_ITEMS) {
      expect(Object.keys(item).sort()).toEqual(['Icon', 'label', 'to'])
    }
  })
})

describe('역할별 사업자 메뉴', () => {
  function visibleLabels(role: UserRole | null, isAuthenticated = role !== null): string[] {
    const map = getRouteAccessMap()
    return BUSINESS_MENU_ITEMS.filter((item) => {
      const access = map.get(item.to)
      return access !== undefined && canAccess(access, { isAuthenticated, role })
    }).map((item) => item.label)
  }

  // 티켓의 예시: SUPPLIER 는 소재 판매 메뉴, PROCESSOR 는 Processing 메뉴.
  it('SUPPLIER 는 판매 계열 메뉴를 보고 가공·생산 메뉴를 보지 않는다', () => {
    expect(visibleLabels('SUPPLIER')).toEqual([
      '회사 정보', '보유 소재', '소재 등록', '판매 관리', '판매 거래', '거래 내역',
    ])
  })

  it('BRAND 는 가공·생산·제품 메뉴를 보고 판매 등록 메뉴를 보지 않는다', () => {
    expect(visibleLabels('BRAND')).toEqual([
      '회사 정보', '보유 소재', '가공 요청', '생산 관리', '제품 등록', '거래 내역',
    ])
  })

  it('PROCESSOR 는 업체 등록·가공 작업 메뉴를 추가로 본다', () => {
    expect(visibleLabels('PROCESSOR')).toEqual(['회사 정보', '보유 소재', '업체 등록', '가공 작업', '거래 내역'])
  })

  // ADMIN 을 만능으로 두지 않는다. 권한 Matrix §4 에서 ADMIN 은 도메인 기능에
  // 단 한 칸도 허용이 없고(지갑이 없어 서명 흐름에 못 들어간다), routes.tsx 의
  // allow 목록에도 없다. 여기서 편의로 열어 주면 정의와 화면이 갈라진다.
  it('ADMIN 과 CONSUMER 는 사업자 전용 메뉴를 보지 않는다', () => {
    // 남는 것은 RoleGuard 가 없는 Protected 경로(/inventory·/trades)뿐이다.
    // 그 둘에 역할 조건을 새로 붙이지 않기로 했다 (권한 Matrix §3-3·§3-5).
    expect(visibleLabels('ADMIN')).toEqual(['보유 소재', '거래 내역'])
    expect(visibleLabels('CONSUMER')).toEqual(['보유 소재', '거래 내역'])
  })

  it('미인증 사용자에게는 로그인이 필요한 메뉴가 하나도 보이지 않는다', () => {
    expect(visibleLabels(null, false)).toEqual([])
  })
})

describe('계정 메뉴', () => {
  function firstAccountLabel(role: UserRole) {
    signInAs(role)
    const map = getRouteAccessMap()
    const visible = ACCOUNT_MENU_ITEMS.filter((item) => {
      const access = map.get(item.to)
      return access !== undefined && canAccess(access, { isAuthenticated: true, role })
    })
    return visible[0]?.label
  }

  it('역할마다 계정 홈이 하나만 남는다', () => {
    expect(firstAccountLabel('CONSUMER')).toBe('마이페이지')
    expect(firstAccountLabel('ADMIN')).toBe('관리자 페이지')
    expect(firstAccountLabel('SUPPLIER')).toBe('회사 대시보드')
    expect(firstAccountLabel('BRAND')).toBe('회사 대시보드')
    expect(firstAccountLabel('PROCESSOR')).toBe('회사 대시보드')
  })
})
