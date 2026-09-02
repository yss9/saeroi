// 인증 상태를 읽는 표준 통로.
//
// 컴포넌트가 useAuthStore 를 직접 구독해도 동작하지만, 그러면 나중에 저장 방식을
// 바꿀 때(예: httpOnly 쿠키 + /auth/me 조회) 모든 호출부를 고쳐야 한다.
// 이 훅 하나만 바꾸면 되도록 한 겹 둔다.

import { useAuthStore } from '../../../stores/authStore.ts'
import type { UserRole } from '../../../types/role.types.ts'

export function useAuth() {
  // 필드를 개별 선택자로 읽는다. 스토어 전체를 구독하면 관련 없는 필드가 바뀔 때도
  // 리렌더가 발생한다.
  const user = useAuthStore((state) => state.user)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const signOut = useAuthStore((state) => state.signOut)

  return {
    user,
    role: user?.role ?? null,
    isAuthenticated,
    signOut,
    /** 여러 Role 중 하나라도 해당하는가. RoleGuard 와 조건부 UI 가 함께 쓴다. */
    hasRole: (roles: readonly UserRole[]) => (user ? roles.includes(user.role) : false),
  }
}
