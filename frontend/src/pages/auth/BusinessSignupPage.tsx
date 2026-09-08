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
      onSuccess: () => navigate('/signup/complete', { state: { accountType: 'business' } }),
    })
  }

  return (
    <AuthLayout
      eyebrow="BUSINESS ACCOUNT"
      title="사업자회원 가입"
      description="업체 유형과 회사 정보를 등록하고 SAEROI의 순환 소재 생태계에 참여해 보세요."
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
