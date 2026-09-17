// 셸(CompanyDashboardLayout)은 ProtectedRoute 가 씌운다. 여기서 또 감싸면 헤더와
// 사이드바가 두 겹으로 그려진다.
import { useState } from 'react'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { CompanyProfileForm } from '../../features/company/components/CompanyProfileForm.tsx'
import { useUpdateCompanyProfile } from '../../features/company/hooks/useUpdateCompanyProfile.ts'
import { CompanyAccountView } from '../../features/company/components/CompanyAccountView.tsx'
import { isBusinessUserRole } from '../../features/company/types/companyAccount.types.ts'
import type { CompanyProfile } from '../../features/company/api/getCompanyProfile.ts'
import { useCompanyProfile } from '../../features/company/hooks/useCompanyProfile.ts'
import { useMyWallets } from '../../features/wallet/hooks/useMyWallets.ts'

/**
 * 업체 주소 표시용 문자열. Backend 는 우편번호·주소·상세주소를 나눠 갖고 있다
 * (`company_profile.zip_code/address/address_detail`) — 화면은 한 줄로 합쳐 보여준다.
 *
 * 수정 폼은 이 값을 다시 우편번호·주소·상세주소 세 칸으로 쪼개 받는다 — 합쳐 보여주는
 * 것은 읽기 편하라고 하는 일이고, 서버가 가진 모양은 셋이다.
 */
function formatCompanyAddress(profile: CompanyProfile): string {
  const detail = profile.addressDetail ? ` ${profile.addressDetail}` : ''
  return `(${profile.zipCode}) ${profile.address}${detail}`
}

export function CompanyAccountPage() {
  const { user } = useAuth()
  // 같은 주소에서 조회와 수정을 오간다. 주소를 나누면 새로고침·뒤로가기로 수정 화면에
  // 바로 들어올 수 있는데, 그때 아직 안 읽은 업체 정보로 폼을 채울 수 없다.
  const [isEditing, setEditing] = useState(false)
  const update = useUpdateCompanyProfile()
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

  // 수정은 업체 정보를 실제로 읽었을 때만 연다. 조회 전에 폼을 그리면 빈 칸으로 저장해
  // 멀쩡한 값을 지우게 된다 — 서버가 전부 필수로 받기 때문이다.
  if (isEditing && profile) {
    return (
      <CompanyProfileForm
        profile={profile}
        isSubmitting={update.isPending}
        error={update.error}
        onCancel={() => {
          update.reset()
          setEditing(false)
        }}
        onSubmit={(request) =>
          update.mutate(request, {
            // 저장이 성공할 때만 닫는다. 실패했는데 닫으면 사용자가 적은 것이 사라지고,
            // 무엇이 잘못됐는지도 볼 수 없다.
            onSuccess: () => setEditing(false),
          })
        }
      />
    )
  }

  return (
    <CompanyAccountView
        walletState={walletState}
        onRetryWallet={() => void wallets.refetch()}
        {...(profile ? { onEdit: () => setEditing(true) } : {})}
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
