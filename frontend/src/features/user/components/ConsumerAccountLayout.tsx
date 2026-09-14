import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ShoppingBag } from '@phosphor-icons/react'
import './consumer-mypage.css'

/**
 * 소비자 계정 화면의 껍데기.
 *
 * **사업자 대시보드 셸(`CompanyDashboardLayout`)과 겹치면 안 된다.** 이 껍데기는 로고·쇼핑
 * 메뉴·푸터를 스스로 갖고 있어서, 사이드바 셸 안에 들어가면 화면 안에 화면이 또 있는 모양이
 * 된다. 그래서 `/mypage` 계열은 `routes.tsx` 에서 `ProtectedRoute` 밖에 둔다.
 *
 * 마이페이지와 배송지 관리가 함께 쓴다 — 두 화면이 다른 껍데기를 쓰면 오갈 때마다 머리말과
 * 푸터가 바뀐다.
 */
export function ConsumerAccountLayout({ children }: { children: ReactNode }) {
  return (
    <div className="consumer-mypage">
      <a className="consumer-skip-link" href="#consumer-main-content">본문으로 건너뛰기</a>
      <AccountHeader />
      <div id="consumer-main-content">{children}</div>
      <AccountFooter />
    </div>
  )
}

function AccountHeader() {
  return (
    <header className="consumer-account-header">
      <div className="consumer-account-header__inner">
        <Link className="consumer-account-header__logo" to="/" aria-label="SAEROI 홈">
          {/* public/ 자산 경로라 vite base 가 자동으로 안 붙는다 — /test/ 에서 404 방지 */}
          <img src={`${import.meta.env.BASE_URL}img/logo.png`} alt="" />
        </Link>

        <nav className="consumer-account-header__navigation" aria-label="쇼핑 메뉴">
          <Link to="/products">쇼핑</Link>
          <Link to="/marketplace">소재 마켓</Link>
          <Link to="/#featured-brands">브랜드</Link>
          <Link to="/#about">스토리</Link>
        </nav>

        <Link className="consumer-account-header__shop-link" to="/products">
          <ShoppingBag aria-hidden="true" />
          <span>쇼핑 계속하기</span>
        </Link>
      </div>
    </header>
  )
}

function AccountFooter() {
  return (
    <footer className="consumer-account-footer">
      <div>
        {/* public/ 자산 경로라 vite base 가 자동으로 안 붙는다 — /test/ 에서 404 방지 */}
        <img src={`${import.meta.env.BASE_URL}img/logo.png`} alt="" />
        <p>쓰임을 다한 소재에 새로운 가치를 더합니다.</p>
      </div>
      <p>© 2026 SAEROI. 모든 권리 보유.</p>
    </footer>
  )
}
