import { Buildings, UserCircle } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { AuthLayout } from '../../features/auth/components/AuthLayout.tsx'

const accountTypes = [
  {
    title: '개인회원',
    description: '제품을 둘러보고 주문과 배송을 한곳에서 관리하세요.',
    benefits: ['제품 쇼핑', '주문 · 배송 관리'],
    to: '/signup/consumer',
    action: '개인회원으로 가입',
    Icon: UserCircle,
  },
  {
    title: '사업자회원',
    description: '소재 거래와 제품 생산을 위한 기업 회원입니다.',
    benefits: ['공급업체 · 브랜드 · 가공업체', '기업 정보 · 업무 관리'],
    to: '/signup/business',
    action: '사업자회원으로 가입',
    Icon: Buildings,
  },
] as const

export function SignupTypePage() {
  return (
    <AuthLayout
      eyebrow="JOIN SAEROI"
      title="회원 유형을 선택해 주세요"
      description="사용 목적에 맞는 계정을 선택해 주세요."
      wide
    >
      <div className="auth-type-list">
        {accountTypes.map(({ title, description, benefits, to, action, Icon }) => (
          <Link className="auth-type-card" to={to} key={title}>
            <span className="auth-type-card__icon">
              <Icon aria-hidden="true" />
            </span>
            <h2>{title}</h2>
            <p>{description}</p>
            <ul>
              {benefits.map((benefit) => <li key={benefit}>{benefit}</li>)}
            </ul>
            <span className="auth-type-card__action">{action} →</span>
          </Link>
        ))}
      </div>

      <p className="auth-entry__switch">
        이미 계정이 있으신가요? <Link to="/login">로그인</Link>
      </p>
    </AuthLayout>
  )
}
