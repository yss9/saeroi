import { Storefront, User } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { AuthLayout } from '../../features/auth/components/AuthLayout.tsx'

const accountTypes = [
  {
    title: '개인회원',
    description: '업사이클 제품을 구매하고 나의 주문과 배송을 관리해요.',
    benefits: ['업사이클 제품 쇼핑', '주문·배송 내역 관리'],
    to: '/signup/consumer',
    action: '개인회원으로 가입',
    Icon: User,
  },
  {
    title: '사업자회원',
    description: '순환 소재의 거래와 생산 과정에 참여할 기업 계정을 만들어요.',
    benefits: ['공급업체 · 브랜드 · 가공업체', '기업 정보 및 역할별 업무 관리'],
    to: '/signup/business',
    action: '사업자회원으로 가입',
    Icon: Storefront,
  },
] as const

export function SignupTypePage() {
  return (
    <AuthLayout
      eyebrow="JOIN SAEROI"
      title="회원 유형을 선택해 주세요"
      description="가입 목적에 맞는 유형을 선택하면 필요한 정보만 안내해 드립니다."
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
