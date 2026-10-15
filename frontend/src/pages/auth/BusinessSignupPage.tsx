import { Link, useNavigate } from 'react-router-dom'
import { ErrorMessage } from '../../components/common/index.ts'
import { AuthLayout } from '../../features/auth/components/AuthLayout.tsx'
import { BusinessSignupForm } from '../../features/auth/components/BusinessSignupForm.tsx'
import { useBusinessSignup } from '../../features/auth/hooks/useSignup.ts'
import type { BusinessSignupSubmitValues } from '../../features/auth/types/signup.types.ts'

export function BusinessSignupPage() {
  const navigate = useNavigate()
  const { mutate, isPending, isError, error } = useBusinessSignup()

  function submitSignup(values: BusinessSignupSubmitValues) {
    mutate(values, {
      onSuccess: () => navigate('/signup/complete', {
          // 가입은 끝났다. replace 를 쓰지 않으면 뒤로 가기가 채워진 가입 폼으로
          // 돌아가고, 다시 제출하면 "이미 가입된 이메일" 오류만 본다.
          replace: true,
          state: { accountType: 'business' },
        }),
    })
  }

  return (
    <AuthLayout
      eyebrow="BUSINESS ACCOUNT"
      title="사업자회원 가입"
      description="기업 정보와 이용할 업무 유형을 입력해 주세요."
      wide
    >
      <div className="consumer-signup__form-wrap">
        <BusinessSignupForm onSubmit={submitSignup} isSubmitting={isPending} />
        {isError && <ErrorMessage error={error} />}
      </div>
      <p className="auth-entry__switch">
        회원 유형을 변경하시겠어요? <Link to="/signup">다시 선택하기</Link>
      </p>
    </AuthLayout>
  )
}
