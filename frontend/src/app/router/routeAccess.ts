// 경로별 접근 조건을 routes.tsx 에서 **파생**시킨다.
//
// 왜 파생인가 — 메뉴 노출은 "이 사용자가 이 경로에 들어갈 수 있는가" 를 화면에 미리
// 반영하는 일이다. 그 판정은 이미 routes.tsx 의 ProtectedRoute / RoleGuard 중첩에
// 적혀 있다. 메뉴 쪽에 `{ label: '소재 등록', roles: ['SUPPLIER'] }` 같은 표를 새로
// 만들면 같은 정책이 두 곳에 생기고, 한쪽만 고쳤을 때
//
//   - 메뉴는 보이는데 누르면 /403
//   - 권한은 있는데 메뉴가 없다
//
// 두 증상이 조용히 생긴다. 그래서 정책의 정본은 routes.tsx 하나로 두고, 이 파일은
// 그 트리를 읽어서 옮겨 적을 뿐이다. 새 매핑을 정하지 않는다.
//
// ⚠️ 이것도 보안이 아니다. RoleGuard 와 같은 성격의 UX 장치다
// (docs/ARCHITECTURE.md §9 "Frontend Guard는 보안이 아닙니다").
// 실제 인가는 매 요청마다 서버가 DB 의 role·status 를 다시 읽어 판단한다.

import { isValidElement, type ReactElement } from 'react'
import { matchPath, type RouteObject } from 'react-router-dom'
import type { UserRole } from '../../types/role.types.ts'
import { ProtectedRoute } from './guards/ProtectedRoute.tsx'
import { RoleGuard, type RoleGuardProps } from './guards/RoleGuard.tsx'
import { routes } from './routes.tsx'

/**
 * 한 경로의 접근 조건.
 *
 * - `public` — 가드 밖. 비회원도 본다 (`/shop`, `/products/:productId` 등)
 * - `auth`   — ProtectedRoute 안이지만 RoleGuard 가 없다. **로그인만 하면 역할 무관.**
 *              `/inventory`, `/trades/:tradeId` 가 여기다. 거래 상세가 여기 있는 것은
 *              판매자와 구매자가 같은 주소를 쓰기 때문이다 — 당사자인지는 서버가 판단한다.
 *              좁혀야 한다면 이 파일이 아니라 routes.tsx 의 가드를 고친다
 * - `role`   — RoleGuard `allow` 목록에 든 역할만
 */
export type RouteAccess =
  | { readonly kind: 'public' }
  | { readonly kind: 'auth' }
  | { readonly kind: 'role'; readonly allow: readonly UserRole[] }

interface WalkContext {
  readonly parentPath: string
  readonly requiresAuth: boolean
  readonly allow: readonly UserRole[] | null
}

/**
 * `path` 조각을 부모 경로에 이어 붙인다.
 *
 * 지금 routes.tsx 는 자식도 전부 절대 경로(`/inventory`)로 적고 있어 이어 붙일 일이
 * 없지만, 상대 경로로 바꿔도 파생이 조용히 어긋나지 않도록 처리해 둔다.
 */
function joinPath(parentPath: string, path: string): string {
  if (path.startsWith('/')) return path
  const base = parentPath === '/' ? '' : parentPath
  return `${base}/${path}`
}

function isElementOf<P>(element: unknown, component: (props: P) => unknown): element is ReactElement<P> {
  return isValidElement(element) && element.type === component
}

/**
 * 중첩된 RoleGuard 는 **교집합**이다. 안쪽 가드가 바깥을 넓힐 수는 없다 —
 * 바깥 가드가 먼저 /403 으로 보내기 때문이다. 지금 트리에 중첩은 없지만,
 * 생겼을 때 메뉴가 실제보다 넓게 보이는 쪽으로 틀리지 않게 한다.
 */
function narrow(current: readonly UserRole[] | null, next: readonly UserRole[]): readonly UserRole[] {
  if (current === null) return next
  return current.filter((role) => next.includes(role))
}

function toAccess(context: WalkContext): RouteAccess {
  if (context.allow !== null) return { kind: 'role', allow: context.allow }
  if (context.requiresAuth) return { kind: 'auth' }
  return { kind: 'public' }
}

function walk(list: readonly RouteObject[], context: WalkContext, out: Map<string, RouteAccess>): void {
  for (const route of list) {
    let next = context

    // 가드는 `path` 없는 레이아웃 라우트의 element 로 걸려 있다. element 의 타입을
    // 직접 비교하는 이유: 문자열 이름이나 별도 메타데이터로 표시하면 routes.tsx 에
    // "가드를 걸었다" 와 "메타를 적었다" 라는 두 가지 진실이 생긴다.
    if (isElementOf(route.element, ProtectedRoute)) {
      next = { ...next, requiresAuth: true }
    } else if (isElementOf<RoleGuardProps>(route.element, RoleGuard)) {
      next = { ...next, requiresAuth: true, allow: narrow(next.allow, route.element.props.allow) }
    }

    if (route.path !== undefined && route.path !== '*') {
      const fullPath = joinPath(next.parentPath, route.path)
      next = { ...next, parentPath: fullPath }
      out.set(fullPath, toAccess(next))
    }

    if (route.children) walk(route.children, next, out)
  }
}

function buildRouteAccess(list: readonly RouteObject[]): ReadonlyMap<string, RouteAccess> {
  const out = new Map<string, RouteAccess>()
  walk(list, { parentPath: '', requiresAuth: false, allow: null }, out)
  return out
}

let cached: ReadonlyMap<string, RouteAccess> | null = null

/**
 * 경로 → 접근 조건. routes.tsx 를 한 번 훑어 만들고 캐시한다.
 *
 * ⚠️ **모듈 로드 시점에 만들지 않는다 — 순환 import 때문이다.**
 * routes.tsx → LandingPage → Header → (이 파일) → routes.tsx 로 고리가 생기는데,
 * 고리 안에서 top-level 로 `routes` 를 읽으면 아직 초기화 전인 값을 잡아 `undefined`
 * 가 된다. 첫 호출은 컴포넌트 렌더 시점이라 그때는 모든 모듈이 완성돼 있다.
 * 라우트 정의는 정적이므로 한 번만 만들면 된다.
 */
export function getRouteAccessMap(): ReadonlyMap<string, RouteAccess> {
  cached ??= buildRouteAccess(routes)
  return cached
}

/**
 * 정의되지 않은 경로는 `undefined` 다. 메뉴 항목이 이걸 받으면 링크가 깨진 것이므로
 * "안 보이게" 처리하고 끝내지 않고 테스트에서 실패시킨다 (navigationMenu.test.ts).
 */
export function getRouteAccess(path: string): RouteAccess | undefined {
  return getRouteAccessMap().get(path)
}

/**
 * 실제 브라우저 주소에 해당하는 접근 조건을 찾는다.
 *
 * getRouteAccess 는 메뉴처럼 `/products/:productId` 형태의 라우트 정의를 검사할 때 쓴다.
 * 로그인 후 복귀 주소는 `/products/31?from=cart` 같은 실제 주소이므로, 라우트 패턴과
 * 대조하는 이 함수가 필요하다. 쿼리와 해시는 접근 권한을 바꾸지 않으므로 제외한다.
 */
export function getLocationAccess(location: string): RouteAccess | undefined {
  const pathname = location.split(/[?#]/, 1)[0] || '/'
  const exact = getRouteAccess(pathname)
  if (exact) return exact

  for (const [routePattern, access] of getRouteAccessMap()) {
    if (matchPath({ path: routePattern, end: true }, pathname)) return access
  }
  return undefined
}

export interface Viewer {
  readonly isAuthenticated: boolean
  /** 인증됐지만 아직 역할을 모르는 상태는 `null`. useAuth() 가 그대로 준다. */
  readonly role: UserRole | null
}

/**
 * 역할 판정이 아직 끝나지 않았는가.
 *
 * `isAuthenticated === true && role === null` 이 그 신호다 — RoleGuard 가 같은 조합을
 * "판정 보류" 로 다룬다. 지금 authStore 는 persist + localStorage 라 동기 복원이지만,
 * 나중에 `/auth/me` 로 역할을 비동기 조회하게 되면 이 구간에서 메뉴가 한 번
 * "비회원 메뉴 → 역할 메뉴" 로 번쩍인다. 그걸 미리 막는다.
 */
export function isRoleResolving(viewer: Viewer): boolean {
  return viewer.isAuthenticated && viewer.role === null
}

/** 이 사용자가 그 경로에 들어갈 수 있는가 (= 메뉴에 보여도 되는가). */
export function canAccess(access: RouteAccess, viewer: Viewer): boolean {
  if (access.kind === 'public') return true
  if (!viewer.isAuthenticated) return false
  if (access.kind === 'auth') return true
  return viewer.role !== null && access.allow.includes(viewer.role)
}
