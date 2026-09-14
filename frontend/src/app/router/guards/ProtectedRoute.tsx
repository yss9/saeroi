import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { CompanyDashboardLayout } from '../../../features/company/components/CompanyDashboardLayout.tsx'
import { ConsumerShopLayout } from '../../../features/user/components/ConsumerAccountLayout.tsx'
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
  const { isAuthenticated, role } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    // 사용자가 있던 위치를 넘겨, 로그인 후 원래 가려던 곳으로 되돌려 보낸다.
    // replace 를 쓰는 이유: 뒤로 가기를 눌렀을 때 다시 이 가드로 튕기지 않게 한다.
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  // **셸을 여기서 씌운다.** 메뉴는 화면마다 넣는 구조였고, 그래서 실제로
  // CompanyAccountPage 와 ProcessorProfilePage 두 곳에만 붙어 있었다. 나머지 14개
  // 보호 화면에는 메뉴가 없어서, 한 번 대시보드를 벗어나면 뒤로 가기나 주소 직접
  // 입력 말고는 돌아갈 길이 없었다 — 거래 목록으로 갈 방법이 없다는 제보가 그것이다.
  //
  // 로그인이 확인된 모든 화면이 이 게이트를 지나므로 여기 한 곳이면 전부 덮인다.
  // 각 화면에 넣는 방식은 새 화면이 생길 때마다 빠뜨리게 된다.
  // **소비자에게는 쇼핑 껍데기를 씌운다.** 이 가드 안에서 소비자가 닿는 곳은 제품 목록과
  // 상세뿐인데, 거기에 사업자 사이드바(회사 정보·보유 소재…)를 붙이면 옷을 고르는 화면에
  // 남의 업무 메뉴가 따라붙는다. 계정 화면과 같은 머리말·푸터라 오갈 때 위아래가 그대로다.
  if (role === 'CONSUMER') {
    return (
      <ConsumerShopLayout>
        <Outlet />
      </ConsumerShopLayout>
    )
  }

  return (
    <CompanyDashboardLayout>
      <Outlet />
    </CompanyDashboardLayout>
  )
}
