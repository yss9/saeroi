import { useLocation } from 'react-router-dom'
import { AuthLayout } from '../../features/auth/components/AuthLayout.tsx'
import {
  SignupCompleteContent,
  type SignupAccountType,
} from '../../features/auth/components/SignupCompleteContent.tsx'

interface SignupCompleteLocationState {
  accountType?: SignupAccountType
}

function readAccountType(state: unknown): SignupAccountType | undefined {
  if (!state || typeof state !== 'object' || !('accountType' in state)) return undefined
  const { accountType } = state as SignupCompleteLocationState
  return accountType === 'consumer' || accountType === 'business' ? accountType : undefined
}

export function SignupCompletePage() {
  const location = useLocation()
  const accountType = readAccountType(location.state)
  const accountLabel = accountType === 'consumer'
    ? '개인회원'
    : accountType === 'business'
      ? '사업자회원'
      : '회원'

  return (
    <AuthLayout
      eyebrow="WELCOME TO SAEROI"
      title={`${accountLabel} 가입이 완료되었습니다`}
      description="새로운 순환의 시작을 SAEROI와 함께해 주셔서 감사합니다."
    >
      <SignupCompleteContent accountType={accountType} />
    </AuthLayout>
  )
}
