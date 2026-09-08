import { Link, useLocation } from 'react-router-dom'
import { EmptyState } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { ROLE_LABEL, isUserRole, type UserRole } from '../../types/role.types.ts'

interface ForbiddenState {
  /** 사용자가 들어가려 했던 위치. RoleGuard 가 넘겨 준다. */
  from?: { pathname?: string }
  /** 그 화면이 허용하는 Role 목록. RoleGuard 가 넘겨 준다. */
  allow?: string[]
}

/**
 * 권한이 없어 화면에 들어갈 수 없을 때 보여 주는 화면 (HTTP 403 성격).
 *
 * ⚠️ 여기서 **로그인 화면으로 유도하지 않는다.** 이 화면에 온 사용자는 이미 로그인해
 * 있고, 역할이 맞지 않을 뿐이다. "로그인하세요" 라고 안내하면
 *   (1) 이미 로그인한 사용자에게 다시 로그인을 시키는 셈이 되고,
 *   (2) 로그인 성공 후 원래 가려던 곳으로 되돌리는 흐름과 맞물려 같은 자리를
 *       무한히 오가게 된다.
 * 그래서 안내는 "이 화면은 다른 역할용이다" 와 홈으로 나가는 길뿐이다.
 *
 * 로그인 자체가 안 된 경우(401 성격)는 이 화면이 아니라 ProtectedRoute /
 * RoleGuard 가 `/login` 으로 보낸다.
 */
export function ForbiddenPage() {
  const location = useLocation()
  const { role } = useAuth()

  const state = (location.state as ForbiddenState | null) ?? null
  const attempted = state?.from?.pathname
  // 라우트 정의에서 넘어온 값이지만 history state 는 사용자가 조작할 수 있으므로
  // 알고 있는 Role 만 라벨로 바꾼다.
  const allowedLabels = (state?.allow ?? [])
    .filter((value): value is UserRole => isUserRole(value))
    .map((value) => ROLE_LABEL[value])

  const description = [
    attempted ? `${attempted} 는 다른 역할을 위한 화면입니다.` : '다른 역할을 위한 화면입니다.',
    allowedLabels.length > 0 ? `접근 가능 역할: ${allowedLabels.join(', ')}.` : null,
    role ? `현재 로그인한 역할: ${ROLE_LABEL[role]}.` : null,
  ]
    .filter((line) => line !== null)
    .join(' ')

  return (
    <main className="page">
      <h1>접근 권한이 없습니다</h1>
      <EmptyState
        title="이 화면에 들어갈 수 있는 역할이 아닙니다"
        description={description}
        action={<Link to="/">홈으로 돌아가기</Link>}
      />
    </main>
  )
}
