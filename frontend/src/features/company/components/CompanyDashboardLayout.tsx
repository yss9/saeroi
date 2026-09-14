import { House, List, SignOut, UserCircle, X } from '@phosphor-icons/react'
import { useState, type ReactNode } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import {
  ACCOUNT_MENU_ITEMS,
  BUSINESS_MENU_ITEMS,
} from '../../../app/navigation/navigationMenu.ts'
import { useVisibleMenu } from '../../../app/navigation/useVisibleMenu.ts'
import { useAuth } from '../../auth/hooks/useAuth.ts'
import { useLogout } from '../../auth/hooks/useLogout.ts'
import { getRoleHomePath } from '../../auth/utils/roleHomePath.ts'
import { NotificationCenter } from '../../notification/components/NotificationCenter.tsx'
import { BusinessWalletStatus } from '../../wallet/components/BusinessWalletStatus.tsx'
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
const UNIQUE_MENU_ITEMS = [...BUSINESS_MENU_ITEMS, ...ACCOUNT_MENU_ITEMS].filter(
  (item, index, items) => items.findIndex((other) => other.to === item.to) === index,
)

/** 계정 항목의 경로. 중복 제거가 끝난 뒤 순서를 잡는 데 쓴다. */
const ACCOUNT_PATHS = new Set(ACCOUNT_MENU_ITEMS.map((item) => item.to))

/**
 * 계정 항목을 맨 위로.
 *
 * **소비자에게 이 사이드바는 거의 계정 메뉴다.** 사업자 항목이 역할 필터에 거의 다 걸러져
 * "제품 둘러보기" 정도만 남는데, 그 아래에 마이페이지가 붙어 있으면 자기 계정으로 돌아가는
 * 길이 목록 끝에 숨는다.
 *
 * 중복 제거는 위에서 이미 끝났다 — 순서를 바꾸려고 이어 붙이는 차례를 뒤집으면, 같은
 * 경로가 양쪽에 있을 때 남는 **이름**까지 바뀐다(사업자 메뉴의 이름이 사이드바 문맥에 맞다).
 * 그래서 거르기와 줄 세우기를 나눈다.
 */
const MENU_ITEMS = [
  ...UNIQUE_MENU_ITEMS.filter((item) => ACCOUNT_PATHS.has(item.to)),
  ...UNIQUE_MENU_ITEMS.filter((item) => !ACCOUNT_PATHS.has(item.to)),
]

interface CompanyDashboardLayoutProps {
  children: ReactNode
}

export function CompanyDashboardLayout({ children }: CompanyDashboardLayoutProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const isBusinessUser = Boolean(user && isBusinessUserRole(user.role))
  // 역할→메뉴 매핑을 이 파일이 들고 있지 않다. routes.tsx 의 RoleGuard 정의에서
  // 파생된다 (app/navigation/navigationMenu.ts 머리말). 예전에는 여기에 역할별 배열이
  // 있어서 라우트를 추가해도 메뉴가 따라오지 않았다 — /supplier/trades 와
  // /brand/products/new 가 실제로 그렇게 빠져 있었다.
  //
  // 사업자 메뉴와 계정 메뉴를 함께 거른다. 이 레이아웃이 이제 **로그인한 모든 화면**을
  // 감싸므로(ProtectedRoute), 소비자에게는 사업자 항목이 하나도 남지 않고 마이페이지만
  // 남아야 한다. useVisibleMenu 가 routes.tsx 의 RoleGuard 로 걸러 주므로 두 배열을
  // 이어 붙여도 남의 메뉴가 새지 않는다.
  const { items: visibleNavigation, isResolving } = useVisibleMenu(MENU_ITEMS)
  // BUSINESS_MENU_ITEMS 의 "회사 정보"와 ACCOUNT_MENU_ITEMS 의 사업자 홈은 같은
  // 경로다. 사업자에게는 앞의 구체적인 메뉴만 남기고 중복 링크를 그리지 않는다.
  const navigation = visibleNavigation.filter(
    (item, index, items) => items.findIndex((candidate) => candidate.to === item.to) === index,
  )

  function handleSignOut() {
    logout.mutate(undefined, {
      onSuccess: () => navigate('/', { replace: true }),
    })
  }

  // 역할마다 대시보드 홈이 다르다. 역할을 아직 모르면(판정 보류) 회사 정보로 보낸다 —
  // 어느 사업자 역할이든 들어갈 수 있는 화면이다.
  const homePath = user ? getRoleHomePath(user.role) : '/company/account'

  return (
    <div className="company-dashboard">
      <a className="company-dashboard__skip-link" href="#company-dashboard-content">본문으로 이동</a>

      <header className="company-dashboard__header">
        {/*
          로고는 <b>대시보드 안</b>으로 돌아간다. 예전에는 `/`(쇼핑몰 랜딩)로 갔는데,
          그 화면은 사업자 셸 밖이라 누르는 순간 사이드바가 사라졌다 — 작업 중에 로고를
          누른 사용자가 메뉴를 잃고 마케팅 페이지에 서 있게 된다.

          쇼핑몰로 나가는 길은 옆의 "쇼핑몰" 버튼이 따로 맡는다. 그쪽은 의도한 이탈이다.
        */}
        <Link className="company-dashboard__logo" to={homePath} aria-label="대시보드 홈">
          {/* public/ 자산 경로라 vite base 가 자동으로 안 붙는다 — /test/ 에서 404 방지 */}
          <img src={`${import.meta.env.BASE_URL}img/logo.png`} alt="" />
        </Link>
        <div className="company-dashboard__identity">
          <span>{user?.name ?? '회사 계정'}</span>
          {user ? (
            <small>{isBusinessUserRole(user.role) ? BUSINESS_ROLE_LABEL[user.role] : ROLE_LABEL[user.role]}</small>
          ) : null}
        </div>
        <div className="company-dashboard__header-actions">
          <Link className="company-dashboard__shop-link" to="/">
            <House aria-hidden="true" />
            <span>쇼핑몰</span>
          </Link>
          {isBusinessUser ? (
            <>
              <NotificationCenter enabled={isBusinessUser} />
              <BusinessWalletStatus />
              <Link className="company-dashboard__account-link" to="/company/account" aria-label="마이페이지">
                <UserCircle aria-hidden="true" />
                <span>마이페이지</span>
              </Link>
            </>
          ) : null}
          <button
            className="company-dashboard__signout-button"
            type="button"
            disabled={logout.isPending}
            onClick={handleSignOut}
          >
            <SignOut aria-hidden="true" />
            <span>{logout.isPending ? '로그아웃 중…' : '로그아웃'}</span>
          </button>
          {logout.error ? <span role="alert">{logout.error.message}</span> : null}
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
