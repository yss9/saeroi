// 셸(CompanyDashboardLayout)은 ProtectedRoute 가 씌운다. 여기서 또 감싸면 헤더와
// 사이드바가 두 겹으로 그려진다.
import { useState } from 'react'
import { useLocation } from 'react-router-dom'
import { PasswordConfirm } from '../../features/auth/components/PasswordConfirm.tsx'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { CompanyProfileForm } from '../../features/company/components/CompanyProfileForm.tsx'
import { PasswordChangeForm } from '../../features/company/components/PasswordChangeForm.tsx'
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

/**
 * 회사 정보.
 *
 * **방문이 바뀌면 처음 상태로 돌아간다.** 수정 화면은 이 주소 안에서 상태로 열리는데,
 * 그대로 두면 사이드바의 "회사 정보" 를 눌러도 이미 그 경로라 아무 일도 일어나지 않아
 * 수정 화면에 갇힌다. 같은 주소로 다시 오면 `location.key` 가 바뀌므로 그것을 React 의
 * `key` 로 넘겨 화면을 처음부터 그린다 — 상태를 effect 로 되돌리는 것보다 정확하다.
 */
export function CompanyAccountPage() {
  return <CompanyAccount key={useLocation().key} />
}

function CompanyAccount() {
  const { user } = useAuth()
  /*
    **수정은 주소로 연다.** 예전에는 같은 주소에서 상태로만 열었는데, 그러면 사이드바의
    "회사 정보" 를 눌러도 이미 그 경로라 아무 일도 일어나지 않아 수정 화면에 갇혔다.

    주소를 직접 열어 들어와도 안전하다 — 아래에서 업체 정보를 읽은 뒤에만 폼을 그린다.
  */
  /*
    조회 → 본인 확인 → 수정. 세 걸음이 한 주소 안에서 일어난다.

    **수정에 들어갈 때 비밀번호를 한 번 더 받는다.** 회사 정보는 거래 상대가 보고 판단하는
    값이라, 로그인한 채로 자리를 비운 사이 누구나 바꿀 수 있게 두지 않는다.
  */
  const [stage, setStage] = useState<'view' | 'verify' | 'edit'>('view')
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
  // 확인 전에는 폼을 그리지 않는다. 칸이 보이는 채로 막으면 "왜 저장이 안 되지" 가 된다.
  if (stage === 'verify') {
    return (
      <PasswordConfirm
        email={user.email}
        title="비밀번호를 한 번 더 입력해 주세요"
        description="회사 정보를 고치기 전에 본인인지 확인합니다."
        submitLabel="정보 수정으로"
        onCancel={() => setStage('view')}
        onConfirmed={() => setStage('edit')}
      />
    )
  }

  if (stage === 'edit' && profile) {
    return (
      // 둘을 나란히 둔다. 세로로 쌓으면 업체 정보 폼이 길어 비밀번호 칸이 화면 밖에 남는다.
      <div className="company-edit">
      <CompanyProfileForm
        profile={profile}
        isSubmitting={update.isPending}
        error={update.error}
        onCancel={() => {
          update.reset()
          setStage('view')
        }}
        onSubmit={(request) =>
          update.mutate(request, {
            // 저장이 성공할 때만 닫는다. 실패했는데 닫으면 사용자가 적은 것이 사라지고,
            // 무엇이 잘못됐는지도 볼 수 없다.
            onSuccess: () => setStage('view'),
          })
        }
      />

      {/*
        비밀번호는 업체 정보와 다른 요청이라 폼도 따로 선다. 한 번에 묶으면 비밀번호가
        틀렸을 때 주소 수정까지 되돌려야 하고, 반대의 어긋남도 생긴다.
      */}
      <PasswordChangeForm />
      </div>
    )
  }

  return (
    <CompanyAccountView
        walletState={walletState}
        onRetryWallet={() => void wallets.refetch()}
        {...(profile ? { onEdit: () => setStage('verify') } : {})}
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
