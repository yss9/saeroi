import { CheckCircle, Storefront, User } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'

export type SignupAccountType = 'consumer' | 'business'

interface SignupCompleteContentProps {
  accountType?: SignupAccountType
}

const accountCopy = {
  consumer: {
    label: '개인회원',
    description: '로그인하고 업사이클 제품 쇼핑과 주문 관리를 시작해 보세요.',
    Icon: User,
  },
  business: {
    label: '사업자회원',
    description: '로그인하면 선택한 업체 유형에 맞는 업무 공간을 이용할 수 있습니다.',
    Icon: Storefront,
  },
} as const

export function SignupCompleteContent({ accountType }: SignupCompleteContentProps) {
  const content = accountType ? accountCopy[accountType] : null
  const AccountIcon = content?.Icon

  return (
    <div className="signup-complete">
      <span className="signup-complete__check" aria-hidden="true">
        <CheckCircle weight="fill" />
      </span>

      {content && AccountIcon ? (
        <div className="signup-complete__account">
          <AccountIcon aria-hidden="true" />
          <span>{content.label}</span>
        </div>
      ) : null}

      <p className="signup-complete__description">
        {content?.description ?? '로그인하고 SAEROI의 서비스를 시작해 보세요.'}
      </p>

      {accountType === 'business' ? (
        <aside className="signup-complete__wallet-note">
          <strong>지갑 연결 안내</strong>
          <p>지갑 연결은 로그인 후 마이페이지에서 진행해주세요.</p>
        </aside>
      ) : null}

      <div className="signup-complete__actions">
        <Link className="signup-complete__button signup-complete__button--primary" to="/login">
          로그인하기
        </Link>
        <Link className="signup-complete__button signup-complete__button--secondary" to="/">
          쇼핑몰 홈으로
        </Link>
      </div>
    </div>
  )
}
