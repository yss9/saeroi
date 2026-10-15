import { Buildings, UserCircle } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'

export type SignupAccountType = 'consumer' | 'business'

interface SignupCompleteContentProps {
  accountType?: SignupAccountType
}

const accountCopy = {
  consumer: {
    label: '개인회원',
    description: '로그인하고 SAEROI를 둘러보세요.',
    Icon: UserCircle,
  },
  business: {
    label: '사업자회원',
    description: '로그인 후 기업 계정 설정을 이어갈 수 있어요.',
    Icon: Buildings,
  },
} as const

export function SignupCompleteContent({ accountType }: SignupCompleteContentProps) {
  const content = accountType ? accountCopy[accountType] : null
  const AccountIcon = content?.Icon

  return (
    <div className="signup-complete">
      {content && AccountIcon ? (
        <div className="signup-complete__account">
          <AccountIcon aria-hidden="true" />
          <span>{content.label}</span>
        </div>
      ) : null}

      <p className="signup-complete__description">
        {content?.description ?? '로그인하고 SAEROI를 시작해 보세요.'}
      </p>

      {accountType === 'business' ? (
        <aside className="signup-complete__wallet-note">
          <strong>지갑 연결 안내</strong>
          <p>로그인 후 마이페이지에서 지갑을 등록해 주세요. 관리자 승인이 완료되면 파트너 기능을 사용할 수 있습니다.</p>
        </aside>
      ) : null}

      <div className="signup-complete__actions">
        <Link className="signup-complete__button signup-complete__button--primary" to="/login">
          로그인하기
        </Link>
        <Link className="signup-complete__button signup-complete__button--secondary" to="/">
          홈으로 이동
        </Link>
      </div>
    </div>
  )
}
