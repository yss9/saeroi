import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../../features/auth/hooks/useAuth.ts'
import type { UserRole } from '../../../types/role.types.ts'

export interface RoleGuardProps {
  /** 이 중 하나라도 가지고 있으면 통과 */
  allow: readonly UserRole[]
  /** 권한이 없을 때 보낼 곳. 기본은 홈. */
  redirectTo?: string
}

/**
 * Role 기반 접근 제어.
 *
 * ProtectedRoute 안쪽에 중첩해서 쓴다 — 로그인 여부는 그쪽이 이미 확인했으므로
 * 여기서는 Role 만 본다.
 *
 *   <Route element={<ProtectedRoute />}>
 *     <Route element={<RoleGuard allow={['SUPPLIER']} />}>
 *       <Route path="/supplier/materials" element={<MaterialRegisterPage />} />
 *     </Route>
 *   </Route>
 *
 * ⚠️ ProtectedRoute 와 마찬가지로 UX 용이다. Backend 의 @PreAuthorize 를 대체하지 않는다.
 */
export function RoleGuard({ allow, redirectTo = '/' }: RoleGuardProps) {
  const { isAuthenticated, hasRole } = useAuth()

  // 로그인 자체가 안 된 경우는 ProtectedRoute 가 처리하지만, RoleGuard 를 단독으로
  // 쓰는 실수를 대비해 여기서도 방어한다.
  if (!isAuthenticated) return <Navigate to="/login" replace />

  if (!hasRole(allow)) return <Navigate to={redirectTo} replace />

  return <Outlet />
}
