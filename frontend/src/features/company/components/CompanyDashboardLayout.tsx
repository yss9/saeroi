import {
  Buildings,
  Factory,
  House,
  List,
  Package,
  SignOut,
  Storefront,
  Swap,
  Wallet,
  X,
} from '@phosphor-icons/react'
import { useState, type ReactNode } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/hooks/useAuth.ts'
import { ROLE_LABEL } from '../../../types/role.types.ts'
import {
  BUSINESS_ROLE_LABEL,
  isBusinessUserRole,
  type BusinessUserRole,
} from '../types/companyAccount.types.ts'
import './company-dashboard.css'

interface CompanyDashboardLayoutProps {
  children: ReactNode
}

interface NavigationItem {
  label: string
  to: string
  Icon: typeof Buildings
}

const commonNavigation: NavigationItem[] = [
  { label: '회사 정보', to: '/company/account', Icon: Buildings },
]

const roleNavigation: Record<BusinessUserRole, NavigationItem[]> = {
  SUPPLIER: [
    { label: '소재 등록', to: '/supplier/materials/new', Icon: Package },
    { label: '판매 관리', to: '/supplier/listings', Icon: Storefront },
    { label: '거래 내역', to: '/trades', Icon: Swap },
  ],
  BRAND: [
    { label: '보유 소재', to: '/inventory', Icon: Package },
    { label: '가공 요청', to: '/brand/processing', Icon: Factory },
    { label: '생산 관리', to: '/brand/production', Icon: Buildings },
    { label: '거래 내역', to: '/trades', Icon: Swap },
  ],
  PROCESSOR: [
    { label: '가공 작업', to: '/processor/jobs', Icon: Factory },
  ],
}

function getNavigation(role: string | undefined): NavigationItem[] {
  if (role === 'SUPPLIER' || role === 'BRAND' || role === 'PROCESSOR') {
    return [...commonNavigation, ...roleNavigation[role]]
  }
  return commonNavigation
}

export function CompanyDashboardLayout({ children }: CompanyDashboardLayoutProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const navigate = useNavigate()
  const { user, signOut } = useAuth()
  const navigation = getNavigation(user?.role)

  function handleSignOut() {
    signOut()
    navigate('/', { replace: true })
  }

  return (
    <div className="company-dashboard">
      <a className="company-dashboard__skip-link" href="#company-dashboard-content">본문으로 이동</a>

      <header className="company-dashboard__header">
        <Link className="company-dashboard__logo" to="/" aria-label="SAEROI 쇼핑몰 홈">
          <img src="/img/logo.png" alt="" />
        </Link>
        <div className="company-dashboard__identity">
          <span>{user?.name ?? '회사 계정'}</span>
          {user ? (
            <small>{isBusinessUserRole(user.role) ? BUSINESS_ROLE_LABEL[user.role] : ROLE_LABEL[user.role]}</small>
          ) : null}
        </div>
        <div className="company-dashboard__header-actions">
          <Link to="/">
            <House aria-hidden="true" />
            <span>쇼핑몰</span>
          </Link>
          <button type="button" onClick={handleSignOut}>
            <SignOut aria-hidden="true" />
            <span>로그아웃</span>
          </button>
          <button
            className="company-dashboard__menu-button"
            type="button"
            aria-label={isMenuOpen ? '대시보드 메뉴 닫기' : '대시보드 메뉴 열기'}
            aria-expanded={isMenuOpen}
            aria-controls="company-dashboard-sidebar"
            onClick={() => setIsMenuOpen((open) => !open)}
          >
            {isMenuOpen ? <X aria-hidden="true" /> : <List aria-hidden="true" />}
          </button>
        </div>
      </header>

      <div className="company-dashboard__body">
        <aside
          id="company-dashboard-sidebar"
          className={`company-dashboard__sidebar${isMenuOpen ? ' company-dashboard__sidebar--open' : ''}`}
        >
          <div className="company-dashboard__sidebar-heading">
            <span>BUSINESS WORKSPACE</span>
            <strong>{user?.name ?? '회사 계정'}</strong>
          </div>
          <nav aria-label="사업자 대시보드 메뉴">
            {navigation.map(({ label, to, Icon }) => (
              <NavLink
                key={to}
                to={to}
                onClick={() => setIsMenuOpen(false)}
                className={({ isActive }) => isActive ? 'company-dashboard__nav-link--active' : undefined}
              >
                <Icon aria-hidden="true" />
                <span>{label}</span>
              </NavLink>
            ))}
          </nav>
          <div className="company-dashboard__wallet-summary">
            <Wallet aria-hidden="true" />
            <div>
              <strong>지갑 연결</strong>
              <span>{user?.walletAddress ? '연결됨' : '연결 전'}</span>
            </div>
          </div>
        </aside>

        {isMenuOpen ? (
          <button
            className="company-dashboard__overlay"
            type="button"
            aria-label="대시보드 메뉴 닫기"
            onClick={() => setIsMenuOpen(false)}
          />
        ) : null}

        <main id="company-dashboard-content" className="company-dashboard__content">
          {children}
        </main>
      </div>
    </div>
  )
}
