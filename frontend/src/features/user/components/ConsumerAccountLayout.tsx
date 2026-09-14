import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { House, MapPin, Receipt, ShoppingBag, SignOut, User } from '@phosphor-icons/react'
import { toErrorMessage } from '../../../utils/errors.ts'
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
/**
 * 소비자가 쇼핑할 때의 껍데기.
 *
 * 머리말과 푸터만 있고 **사이드바가 없다.** 제품을 고르는 화면에서는 계정 메뉴가 할 일이
 * 없고, 사업자 대시보드 사이드바는 더더욱 그렇다 — 소비자에게 "보유 소재" 나 "회사 정보"
 * 옆에서 옷을 고르라고 하는 셈이다.
 *
 * 계정 화면(`ConsumerAccountLayout`)과 머리말·푸터를 공유한다. 쇼핑하다 마이페이지로
 * 넘어갈 때 위아래가 그대로 남아야 한 사이트로 읽힌다.
 */
export function ConsumerShopLayout({ children }: { children: ReactNode }) {
  return (
    <div className="consumer-mypage">
      <a className="consumer-skip-link" href="#consumer-main-content">본문으로 건너뛰기</a>
      <AccountHeader />
      <div id="consumer-main-content">{children}</div>
      <AccountFooter />
    </div>
  )
}

/** 계정 화면 셋. 사이드바가 이 순서로 서고, 현재 위치 표시도 이 값으로 한다. */
export type ConsumerAccountSection = 'home' | 'orders' | 'addresses'

export interface ConsumerAccountLayoutProps {
  children: ReactNode
  current: ConsumerAccountSection
  /** 사이드바와 머리말에 쓴다. 아직 모르면(조회 전) 생략한다 */
  profile?: { name: string; email: string } | null
  onSignOut?: () => void
  signOutPending?: boolean
  signOutError?: unknown
}

const SECTIONS: Record<
  ConsumerAccountSection,
  { to: string; label: string; title: (name: string) => string; lead: string; Icon: typeof User }
> = {
  home: {
    to: '/mypage',
    label: '마이페이지 홈',
    title: (name) => `${name}님, 반갑습니다.`,
    lead: '내 정보와 기본 배송지를 확인합니다.',
    Icon: User,
  },
  orders: {
    to: '/mypage/orders',
    label: '주문 내역',
    title: () => '주문 내역',
    lead: '주문한 제품과 배송 현황을 확인합니다.',
    Icon: Receipt,
  },
  addresses: {
    to: '/mypage/addresses',
    label: '배송지 관리',
    title: () => '배송지 관리',
    lead: '주문할 때 쓸 배송지를 등록하고 고칩니다.',
    Icon: MapPin,
  },
}

const SECTION_ORDER: ConsumerAccountSection[] = ['home', 'orders', 'addresses']

/**
 * 소비자 계정 화면의 껍데기.
 *
 * **사업자 대시보드 셸(`CompanyDashboardLayout`)과 겹치면 안 된다.** 이 껍데기는 로고·쇼핑
 * 메뉴·푸터를 스스로 갖고 있어서, 사이드바 셸 안에 들어가면 화면 안에 화면이 또 있는 모양이
 * 된다. 그래서 `/mypage` 계열은 `routes.tsx` 에서 `ProtectedRoute` 밖에 둔다.
 *
 * **머리말과 사이드바도 여기 있다.** 마이페이지·주문 내역·배송지 관리가 한 화면의 세 구역처럼
 * 보여야 하는데, 각 화면이 자기 사이드바를 그리면 오갈 때마다 그 자리가 미세하게 달라진다.
 * 로그아웃도 머리말에 있어 어느 구역에서든 나갈 수 있다.
 */
export function ConsumerAccountLayout({
  children,
  current,
  profile,
  onSignOut,
  signOutPending = false,
  signOutError,
}: ConsumerAccountLayoutProps) {
  const section = SECTIONS[current]

  return (
    <div className="consumer-mypage">
      <a className="consumer-skip-link" href="#consumer-main-content">본문으로 건너뛰기</a>
      <AccountHeader />

      <main id="consumer-main-content" className="consumer-mypage-main">
        <nav className="consumer-breadcrumb" aria-label="현재 위치">
          <Link to="/">
            <House aria-hidden="true" /> 홈
          </Link>
          <span aria-hidden="true">/</span>
          {current === 'home' ? (
            <span aria-current="page">마이페이지</span>
          ) : (
            <>
              <Link to="/mypage">마이페이지</Link>
              <span aria-hidden="true">/</span>
              <span aria-current="page">{section.label}</span>
            </>
          )}
        </nav>

        <section className="consumer-mypage-hero" aria-labelledby="consumer-mypage-title">
          <div>
            <p className="consumer-mypage-hero__eyebrow">MY SAEROI</p>
            <h1 id="consumer-mypage-title">{section.title(profile?.name ?? '고객')}</h1>
            <p>{section.lead}</p>
          </div>
          <div className="consumer-mypage-hero__account">
            {profile && <span>{profile.email}</span>}
            {onSignOut && (
              <button type="button" disabled={signOutPending} onClick={onSignOut}>
                <SignOut aria-hidden="true" /> {signOutPending ? '로그아웃 중…' : '로그아웃'}
              </button>
            )}
            {signOutError ? <p role="alert">{toErrorMessage(signOutError)}</p> : null}
          </div>
        </section>

        <div className="consumer-mypage-layout">
          <aside className="consumer-account-sidebar">
            <div>
              <p>MY ACCOUNT</p>
              <strong>{profile?.name ?? '내 계정'}</strong>
              {profile && <span>{profile.email}</span>}
            </div>
            {/*
              앵커(#recent-orders)가 아니라 실제 경로다. 한 화면에 전부 쌓아 두고 앵커로
              건너뛰게 하면, 주문이 쌓일수록 배송지를 보려고 주문 목록을 통째로 지나쳐야 한다.
            */}
            <nav aria-label="마이페이지 메뉴">
              {SECTION_ORDER.map((key) => {
                const item = SECTIONS[key]
                return (
                  <NavLink
                    key={key}
                    to={item.to}
                    // 홈은 하위 경로에서 같이 켜지면 안 된다 — /mypage 가 모든 구역의 접두사다.
                    // NavLink 가 활성일 때 aria-current="page" 를 붙인다. 사이드바 CSS 가
                    // 그 속성을 보고 있어 따로 class 를 주지 않는다.
                    end={key === 'home'}
                  >
                    <item.Icon aria-hidden="true" />
                    <span>{item.label}</span>
                  </NavLink>
                )
              })}
            </nav>
            <div className="consumer-account-sidebar__note">
              <span>SAEROI CIRCULAR CHOICE</span>
              <p>당신의 선택이 소재의 다음 여정을 만듭니다.</p>
            </div>
          </aside>

          <div className="consumer-mypage-content">{children}</div>
        </div>
      </main>

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
