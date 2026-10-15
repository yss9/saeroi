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
      onSuccess: () => navigate('/signup/complete', {
          // 가입은 끝났다. replace 를 쓰지 않으면 뒤로 가기가 채워진 가입 폼으로
          // 돌아가고, 다시 제출하면 "이미 가입된 이메일" 오류만 본다.
          replace: true,
          state: { accountType: 'consumer' },
        }),
    })
  }

  return (
    <AuthLayout
      eyebrow="PERSONAL ACCOUNT"
      title="개인회원 가입"
      description="SAEROI를 시작할 기본 정보를 입력해 주세요."
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
