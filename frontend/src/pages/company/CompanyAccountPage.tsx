// 셸(CompanyDashboardLayout)은 ProtectedRoute 가 씌운다. 여기서 또 감싸면 헤더와
// 사이드바가 두 겹으로 그려진다.
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { CompanyAccountView } from '../../features/company/components/CompanyAccountView.tsx'
import { isBusinessUserRole } from '../../features/company/types/companyAccount.types.ts'
import { useMyWallets } from '../../features/wallet/hooks/useMyWallets.ts'

export function CompanyAccountPage() {
  const { user } = useAuth()
  const wallets = useMyWallets(Boolean(user && isBusinessUserRole(user.role)))

  if (!user || !isBusinessUserRole(user.role)) {
    return (
      <CompanyAccountView
        state={{ status: 'error', error: new Error('회사 계정 정보를 확인할 수 없습니다.') }}
        walletState={{ status: 'ready', registeredAddress: null }}
      />
    )
  }

  return (
    <CompanyAccountView
        walletState={
          wallets.isPending
            ? { status: 'loading' }
            : wallets.isError
              ? { status: 'error', error: wallets.error }
              : { status: 'ready', registeredAddress: wallets.activeWallet?.walletAddress ?? null }
        }
        onRetryWallet={() => void wallets.refetch()}
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
          },
        }}
    />
  )
}
