import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AuthLayout } from '../../features/auth/components/AuthLayout.tsx'
import { BusinessSignupForm } from '../../features/auth/components/BusinessSignupForm.tsx'

export function BusinessSignupPage() {
  const [isFormValidated, setIsFormValidated] = useState(false)

  function prepareSignup() {
    // 실제 계정 생성과 완료 페이지 이동은 Backend 회원가입 DTO 확정 후 이 경계에 연결한다.
    setIsFormValidated(true)
  }

  return (
    <AuthLayout
      eyebrow="BUSINESS ACCOUNT"
      title="사업자회원 가입"
      description="업체 유형과 회사 정보를 등록하고 SAEROI의 순환 소재 생태계에 참여해 보세요."
      wide
    >
      <div className="consumer-signup__form-wrap">
        <BusinessSignupForm onSubmit={prepareSignup} />
        {isFormValidated ? (
          <p className="consumer-signup__ready" role="status">
            입력 정보 확인이 완료되었습니다. 계정 생성은 회원가입 API 연결 후 진행됩니다.
          </p>
        ) : null}
      </div>
      <p className="auth-entry__switch">
        회원 유형을 변경하시겠어요? <Link to="/signup">다시 선택하기</Link>
      </p>
    </AuthLayout>
  )
}
