import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AuthLayout } from '../../features/auth/components/AuthLayout.tsx'
import { ConsumerSignupForm } from '../../features/auth/components/ConsumerSignupForm.tsx'

export function ConsumerSignupPage() {
  const [isFormValidated, setIsFormValidated] = useState(false)

  function prepareSignup() {
    // 실제 계정 생성과 완료 페이지 이동은 Backend 회원가입 DTO 확정 후 이 경계에 연결한다.
    setIsFormValidated(true)
  }

  return (
    <AuthLayout
      eyebrow="PERSONAL ACCOUNT"
      title="개인회원 가입"
      description="업사이클 제품을 만나고 나의 주문과 배송을 관리할 계정을 만들어 보세요."
      wide
    >
      <div className="consumer-signup__form-wrap">
        <ConsumerSignupForm onSubmit={prepareSignup} />
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
