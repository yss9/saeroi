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
  return (
    <AuthLayout
      eyebrow="THANK YOU FOR JOINING US"
      title="가입이 완료되었습니다"
      description="이제 SAEROI를 시작할 준비가 되었어요."
      compact
    >
      <SignupCompleteContent accountType={accountType} />
    </AuthLayout>
  )
}
