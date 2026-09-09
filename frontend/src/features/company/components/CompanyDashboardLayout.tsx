import { House, List, SignOut, Wallet, X } from '@phosphor-icons/react'
import { useState, type ReactNode } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import {
  ACCOUNT_MENU_ITEMS,
  BUSINESS_MENU_ITEMS,
} from '../../../app/navigation/navigationMenu.ts'
import { useVisibleMenu } from '../../../app/navigation/useVisibleMenu.ts'
import { useAuth } from '../../auth/hooks/useAuth.ts'
import { ROLE_LABEL } from '../../../types/role.types.ts'
import { BUSINESS_ROLE_LABEL, isBusinessUserRole } from '../types/companyAccount.types.ts'
import './company-dashboard.css'

/**
 * 로그인한 사용자가 갈 수 있는 모든 곳.
 *
 * 두 배열을 합치는 것은 이 레이아웃이 사업자 대시보드만이 아니라
 * **보호 라우트 전체의 셸**이 되었기 때문이다. 역할별 필터는 useVisibleMenu 가 한다.
 *
 * <b>경로로 중복을 걸러야 한다.</b> {@code ACCOUNT_MENU_ITEMS} 는 헤더 팝오버의
 * "내 홈" 후보라 {@code /company/account} 를 사업자 메뉴와 <b>일부러</b> 겹쳐 들고 있다.
 * 그냥 이어 붙이면 같은 to 가 두 번 들어가 React 가 key 중복을 경고하고, 사이드바에도
 * 같은 곳으로 가는 항목이 "회사 정보"·"회사 대시보드" 두 개로 보인다.
 *
 * 먼저 온 쪽을 남긴다 — 사업자 메뉴의 이름이 사이드바 문맥에 맞고, 겹치지 않는
 * 마이페이지·관리자 페이지는 그대로 따라 들어온다.
 */
const MENU_ITEMS = [...BUSINESS_MENU_ITEMS, ...ACCOUNT_MENU_ITEMS].filter(
  (item, index, items) => items.findIndex((other) => other.to === item.to) === index,
)

interface CompanyDashboardLayoutProps {
  children: ReactNode
}

export function CompanyDashboardLayout({ children }: CompanyDashboardLayoutProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const navigate = useNavigate()
  const { user, signOut } = useAuth()
  // 역할→메뉴 매핑을 이 파일이 들고 있지 않다. routes.tsx 의 RoleGuard 정의에서
  // 파생된다 (app/navigation/navigationMenu.ts 머리말). 예전에는 여기에 역할별 배열이
  // 있어서 라우트를 추가해도 메뉴가 따라오지 않았다 — /supplier/trades 와
  // /brand/products/new 가 실제로 그렇게 빠져 있었다.
  //
  // 사업자 메뉴와 계정 메뉴를 함께 거른다. 이 레이아웃이 이제 **로그인한 모든 화면**을
  // 감싸므로(ProtectedRoute), 소비자에게는 사업자 항목이 하나도 남지 않고 마이페이지만
  // 남아야 한다. useVisibleMenu 가 routes.tsx 의 RoleGuard 로 걸러 주므로 두 배열을
  // 이어 붙여도 남의 메뉴가 새지 않는다.
  const { items: navigation, isResolving } = useVisibleMenu(MENU_ITEMS)

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
          {/*
            역할 판정 중에는 aria-busy 만 두고 링크를 그리지 않는다. 이때 잘못
            그리면(예: 공통 메뉴만) 역할이 도착하는 순간 메뉴가 번쩍 늘어난다.
            RoleGuard 가 같은 상태를 Loading 으로 처리하는 것과 같은 판단이다.
          */}
          <nav aria-label="사업자 대시보드 메뉴" aria-busy={isResolving}>
            {navigation.map(({ label, to, Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/brand/processing'}
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
