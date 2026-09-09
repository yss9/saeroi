// 셸(CompanyDashboardLayout)은 ProtectedRoute 가 씌운다. 여기서 또 감싸면 헤더와
// 사이드바가 두 겹으로 그려진다.
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { CompanyAccountView } from '../../features/company/components/CompanyAccountView.tsx'
import { isBusinessUserRole } from '../../features/company/types/companyAccount.types.ts'

export function CompanyAccountPage() {
  const { user } = useAuth()

  if (!user || !isBusinessUserRole(user.role)) {
    return (
      <CompanyAccountView
        state={{ status: 'error', error: new Error('회사 계정 정보를 확인할 수 없습니다.') }}
      />
    )
  }

  return (
    <CompanyAccountView
        state={{
          status: 'ready',
          data: {
            companyName: user.name,
            email: user.email,
            role: user.role,
            businessNumber: null,
            representativeName: null,
            companyPhone: null,
            companyAddress: null,
            status: null,
            walletAddress: user.walletAddress,
          },
        }}
    />
  )
}
