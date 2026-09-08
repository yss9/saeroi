import { Link, useNavigate } from 'react-router-dom'
import { ErrorMessage } from '../../components/common/index.ts'
import { AuthLayout } from '../../features/auth/components/AuthLayout.tsx'
import { ConsumerSignupForm } from '../../features/auth/components/ConsumerSignupForm.tsx'
import { useConsumerSignup } from '../../features/auth/hooks/useSignup.ts'
import type { ConsumerSignupSubmitValues } from '../../features/auth/types/signup.types.ts'

export function ConsumerSignupPage() {
  const navigate = useNavigate()
  const { mutate, isPending, isError, error } = useConsumerSignup()

  function submitSignup(values: ConsumerSignupSubmitValues) {
    mutate(values, {
      onSuccess: () => navigate('/signup/complete', { state: { accountType: 'consumer' } }),
    })
  }

  return (
    <AuthLayout
      eyebrow="PERSONAL ACCOUNT"
      title="개인회원 가입"
      description="업사이클 제품을 만나고 나의 주문과 배송을 관리할 계정을 만들어 보세요."
      wide
    >
      <div className="consumer-signup__form-wrap">
        <ConsumerSignupForm onSubmit={submitSignup} isSubmitting={isPending} />
        {isError && <ErrorMessage error={error} />}
      </div>

      <p className="auth-entry__switch">
        회원 유형을 변경하시겠어요? <Link to="/signup">다시 선택하기</Link>
      </p>
    </AuthLayout>
  )
}
