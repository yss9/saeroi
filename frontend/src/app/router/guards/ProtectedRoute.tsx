import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../../../features/auth/hooks/useAuth.ts'

/**
 * 로그인한 사용자만 통과시킨다.
 *
 * ⚠️ 이 가드는 **UX 를 위한 것이지 보안 장치가 아니다.**
 * 브라우저에서 도는 코드는 사용자가 얼마든지 우회할 수 있다. 실제 접근 통제는
 * Spring Security 가 매 요청마다 JWT 를 검증해서 수행한다. 여기서 막는 이유는
 * 로그인하지 않은 사람이 빈 화면을 보고 401 만 잔뜩 만드는 걸 피하기 위해서다.
 *
 * 즉, 프론트에서 가드를 빠뜨려도 데이터가 새지는 않지만,
 * Backend 에서 인가를 빠뜨리면 데이터가 샌다.
 */
export function ProtectedRoute() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    // 사용자가 있던 위치를 넘겨, 로그인 후 원래 가려던 곳으로 되돌려 보낸다.
    // replace 를 쓰는 이유: 뒤로 가기를 눌렀을 때 다시 이 가드로 튕기지 않게 한다.
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <Outlet />
}
