// 셸(CompanyDashboardLayout)은 ProtectedRoute 가 씌운다. 여기서 또 감싸면 헤더와
// 사이드바가 두 겹으로 그려진다.
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { CompanyAccountView } from '../../features/company/components/CompanyAccountView.tsx'
import { isBusinessUserRole } from '../../features/company/types/companyAccount.types.ts'
import type { CompanyProfile } from '../../features/company/api/getCompanyProfile.ts'
import { useCompanyProfile } from '../../features/company/hooks/useCompanyProfile.ts'
import { useMyWallets } from '../../features/wallet/hooks/useMyWallets.ts'

/**
 * 업체 주소 표시용 문자열. Backend 는 우편번호·주소·상세주소를 나눠 갖고 있다
 * (`company_profile.zip_code/address/address_detail`) — 화면은 한 줄로 합쳐 보여준다.
 *
 * -132 는 "상세 화면"(조회)만 범위다. 수정 화면(주소를 다시 3필드로 쪼개 입력받는 폼)은
 * 별도 스코프로 남겨 둔다 — Backend `PUT /v1/companies/me` 는 이미 구현돼 있지만(-130),
 * 여기서는 아직 소비하지 않는다.
 */
function formatCompanyAddress(profile: CompanyProfile): string {
  const detail = profile.addressDetail ? ` ${profile.addressDetail}` : ''
  return `(${profile.zipCode}) ${profile.address}${detail}`
}

export function CompanyAccountPage() {
  const { user } = useAuth()
  const isBusiness = Boolean(user && isBusinessUserRole(user.role))
  const wallets = useMyWallets(isBusiness)
  const companyProfile = useCompanyProfile(isBusiness)

  if (!user || !isBusinessUserRole(user.role)) {
    return (
      <CompanyAccountView
        state={{ status: 'error', error: new Error('회사 계정 정보를 확인할 수 없습니다.') }}
        walletState={{ status: 'ready', registeredAddress: null }}
      />
    )
  }

  const walletState = wallets.isPending
    ? { status: 'loading' as const }
    : wallets.isError
      ? { status: 'error' as const, error: wallets.error }
      : { status: 'ready' as const, registeredAddress: wallets.activeWallet?.walletAddress ?? null }

  // 업체 정보가 없는 계정(데이터 정합성이 깨진 상태)이나 네트워크 오류는 화면 전체를
  // 오류로 보여준다 — 지갑 카드까지 절반만 그려 봐야 사용자가 할 수 있는 일이 없다.
  if (companyProfile.isError) {
    return (
      <CompanyAccountView
        state={{ status: 'error', error: companyProfile.error }}
        walletState={walletState}
        onRetryWallet={() => void wallets.refetch()}
      />
    )
  }

  const profile = companyProfile.profile

  return (
    <CompanyAccountView
        walletState={walletState}
        onRetryWallet={() => void wallets.refetch()}
        state={{
          status: 'ready',
          data: {
            // 조회가 끝나기 전(pending)에는 이전처럼 null 로 비워 둔다.
            companyName: profile?.companyName ?? user.name,
            email: user.email,
            role: user.role,
            businessNumber: profile?.businessNumber ?? null,
            representativeName: profile?.representativeName ?? null,
            companyPhone: profile?.companyPhone ?? null,
            companyAddress: profile ? formatCompanyAddress(profile) : null,
            status: profile?.status ?? null,
          },
        }}
    />
  )
}
