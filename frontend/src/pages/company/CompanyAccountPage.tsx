import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { CompanyAccountView } from '../../features/company/components/CompanyAccountView.tsx'
import { CompanyDashboardLayout } from '../../features/company/components/CompanyDashboardLayout.tsx'
import { isBusinessUserRole } from '../../features/company/types/companyAccount.types.ts'

export function CompanyAccountPage() {
  const { user } = useAuth()

  if (!user || !isBusinessUserRole(user.role)) {
    return (
      <CompanyDashboardLayout>
        <CompanyAccountView state={{ status: 'error', error: new Error('회사 계정 정보를 확인할 수 없습니다.') }} />
      </CompanyDashboardLayout>
    )
  }

  return (
    <CompanyDashboardLayout>
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
    </CompanyDashboardLayout>
  )
}
