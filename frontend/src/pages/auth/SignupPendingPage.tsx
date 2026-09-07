import { Link } from 'react-router-dom'
import { AuthLayout } from '../../features/auth/components/AuthLayout.tsx'

interface SignupPendingPageProps {
  accountType: 'consumer' | 'business'
}

const copy = {
  consumer: {
    eyebrow: 'PERSONAL ACCOUNT',
    title: '개인회원 가입',
    description: '업사이클 제품을 구매하고 주문과 배송을 관리할 계정을 만듭니다.',
  },
  business: {
    eyebrow: 'BUSINESS ACCOUNT',
    title: '사업자회원 가입',
    description: '공급업체, 브랜드, 가공업체를 위한 기업 계정을 만듭니다.',
  },
} as const

export function SignupPendingPage({ accountType }: SignupPendingPageProps) {
  const content = copy[accountType]

  return (
    <AuthLayout {...content}>
      <div className="auth-entry__pending">
        <p className="auth-entry__pending-note">
          가입 정보 입력 화면은 다음 구현 단계에서 연결됩니다.
        </p>
        <Link className="auth-entry__back-link" to="/signup">회원 유형 다시 선택하기</Link>
      </div>
    </AuthLayout>
  )
}
