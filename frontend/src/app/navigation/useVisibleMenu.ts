// 메뉴 항목을 현재 사용자가 들어갈 수 있는 것만 남긴다.
//
// 역할의 출처는 useAuth() = authStore 하나다. JWT 를 여기서 따로 디코드하지 않는다 —
// 같은 값을 읽는 두 번째 경로가 생기면 둘이 어긋난다 (docs/ARCHITECTURE.md §9).

import { useMemo } from 'react'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { canAccess, getRouteAccess, isRoleResolving } from '../router/routeAccess.ts'
import type { NavigationMenuItem } from './navigationMenu.ts'

export interface VisibleMenu {
  readonly items: readonly NavigationMenuItem[]
  /**
   * 역할 판정이 끝나지 않아 아직 아무것도 그리면 안 되는 상태.
   *
   * 이때 `items` 는 비어 있다. 호출부는 "메뉴가 없다" 와 구분해서, 링크 대신
   * 자리만 잡거나 로딩 표시를 해야 한다. 로그인 상태인데 비회원용 메뉴를 잠깐
   * 그려 버리면 역할이 도착한 순간 메뉴가 번쩍 바뀐다.
   */
  readonly isResolving: boolean
}

export function useVisibleMenu(items: readonly NavigationMenuItem[]): VisibleMenu {
  const { isAuthenticated, role } = useAuth()

  return useMemo(() => {
    const viewer = { isAuthenticated, role }
    if (isRoleResolving(viewer)) return { items: [], isResolving: true }

    const visible = items.filter((item) => {
      const access = getRouteAccess(item.to)
      // 라우터에 없는 경로는 링크가 깨진 것이다. 화면에서는 조용히 감추고
      // (누르면 404 가 되므로) 어긋남 자체는 테스트가 잡는다.
      return access !== undefined && canAccess(access, viewer)
    })

    return { items: visible, isResolving: false }
  }, [items, isAuthenticated, role])
}
