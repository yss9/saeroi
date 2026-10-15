import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Loading } from '../../../components/common/index.ts'
import { useAuth } from '../../../features/auth/hooks/useAuth.ts'
import type { UserRole } from '../../../types/role.types.ts'

/**
 * 권한 없음(403 성격) 안내 화면 경로.
 *
 * routes.tsx 가 아니라 여기서 내보내는 이유: routes.tsx 가 이 파일을 import 하므로
 * 반대 방향으로 가져오면 순환이 된다.
 */
export const FORBIDDEN_PATH = '/403'

export interface RoleGuardProps {
  /** 이 중 하나라도 가지고 있으면 통과 */
  allow: readonly UserRole[]
  /**
   * 권한이 없을 때 보낼 곳. 기본은 권한 없음 안내 화면.
   *
   * ⚠️ 여기에 `/login` 을 넣지 말 것. 아래 401/403 구분 주석 참고.
   */
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
 *       <Route path="/supplier/materials/new" element={<MaterialRegisterPage />} />
 *     </Route>
 *   </Route>
 *
 * ## 미인증(401)과 권한없음(403)을 다르게 처리한다
 *
 * 백엔드가 401 과 403 을 구분해서 주는 것과 같은 이유로 여기서도 갈라야 한다.
 *
 * - **미인증** → `/login` (원래 위치를 `state.from` 으로 넘겨 로그인 후 되돌린다)
 * - **권한없음** → `FORBIDDEN_PATH`
 *
 * 권한없음을 `/login` 으로 보내면, 이미 로그인한 사용자가 로그인 화면을 다시 보고,
 * 로그인에 성공하면 LoginPage 가 `state.from` 으로 되돌려 보내 같은 자리에서 또
 * 튕긴다 — 무한 왕복이다. 그래서 두 경로는 절대 합치지 않는다.
 *
 * ## Role 이 아직 없을 때 403 이 번쩍이지 않는가
 *
 * 지금은 번쩍일 여지가 없다. Role 의 출처는 authStore 하나이고(`useAuth`),
 * 그 스토어는 zustand persist + localStorage 라 **동기적으로** 복원된다.
 * 즉 첫 렌더 시점에 이미 user/role 이 채워져 있다.
 *
 * 나중에 Role 을 서버에서 비동기로 조회하게 되면(`/auth/me` 등) 그때는
 * "로딩 중" 상태가 생기므로, 판정을 미루고 로딩 화면을 렌더해야 한다.
 * `isAuthenticated === true && role === null` 이 그 신호다 — 아래에서 이 조합을
 * 판정 보류로 처리해 두었으니, 스토어가 로딩 상태를 갖게 되어도 403 이 번쩍이지 않는다.
 *
 * ⚠️ ProtectedRoute 와 마찬가지로 UX 용이다. Backend 의 @PreAuthorize 를 대체하지 않는다.
 */
export function RoleGuard({ allow, redirectTo = FORBIDDEN_PATH }: RoleGuardProps) {
  const { isAuthenticated, role, hasRole } = useAuth()
  const location = useLocation()

  // 로그인 자체가 안 된 경우는 ProtectedRoute 가 처리하지만, RoleGuard 를 단독으로
  // 쓰는 실수를 대비해 여기서도 방어한다. ProtectedRoute 와 같은 형태로 보내야
  // 로그인 후 복귀 흐름이 동일하게 동작한다.
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  // 인증은 됐는데 Role 을 아직 모르는 상태. "권한 없음" 과 다르므로 판정하지 않고
  // 기다린다 — 여기서 403 으로 보내면 복구할 방법이 없다(그 화면에는 재시도가 없다).
  // 빈 화면 대신 스피너를 두는 이유: 아무것도 안 그리면 사용자는 화면이 죽었다고 본다.
  if (role === null) return <Loading label="권한을 확인하는 중" />

  if (!hasRole(allow)) {
    // 어디로 들어가려 했고 어떤 역할이 필요한지 안내 화면이 설명할 수 있게 넘긴다.
    // allow 는 readonly 라 history state 로 넘기기 전에 평범한 배열로 복사한다.
    return <Navigate to={redirectTo} state={{ from: location, allow: [...allow] }} replace />
  }

  return <Outlet />
}
