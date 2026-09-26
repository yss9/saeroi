import { Outlet } from 'react-router-dom'
import { CompanyDashboardLayout } from '../../../features/company/components/CompanyDashboardLayout.tsx'
import { useAuth } from '../../../features/auth/hooks/useAuth.ts'

/**
 * <b>공개 조회 화면에도 로그인한 사람에게는 메뉴를 씌운다.</b>
 *
 * 소재 목록·마켓플레이스는 비회원도 볼 수 있어야 해서 {@code ProtectedRoute} 밖에 있다
 * (QR 공개 조회와 같은 결정). 그런데 그 대가로 <b>로그인한 사용자가 들어가면 사이드바가
 * 사라졌다.</b> 브랜드가 소재를 사러 마켓플레이스에 들어간 순간 메뉴가 없어지니, 거기서
 * 판매 관리나 거래 내역으로 갈 방법이 브라우저 뒤로 가기밖에 없었다.
 *
 * 접근 조건은 그대로 공개다. 이것은 <b>가드가 아니라 껍데기 선택</b>이다 —
 * 로그인했으면 대시보드 안에서 보고, 아니면 그냥 화면만 본다.
 *
 * <b>{@code routeAccess} 파생에 영향을 주지 않는다.</b> 그쪽은 element 타입이
 * {@code ProtectedRoute}·{@code RoleGuard} 인지만 보므로, 이 레이아웃이 끼어도 경로는
 * 계속 {@code public} 으로 읽힌다. 즉 "메뉴에 보이지만 못 들어간다" 가 생기지 않는다.
 *
 * ⚠️ QR Passport({@code /qr/:qrToken}) 에는 쓰지 않는다. 그 화면은 비회원이 폰으로
 * 들어오는 입구라 사업자 대시보드 껍데기가 어울리지 않는다.
 */
export function BrowseLayout() {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated) return <Outlet />

  return (
    <CompanyDashboardLayout>
      <Outlet />
    </CompanyDashboardLayout>
  )
}
