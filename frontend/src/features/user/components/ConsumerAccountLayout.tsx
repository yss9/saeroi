import { useEffect, type ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import {
  ArrowsClockwise,
  ChatCircleText,
  ChatText,
  ClockCounterClockwise,
  ChartBar,
  Heart,
  MapPin,
  Receipt,
  ShoppingBag,
  SignOut,
  Star,
  User,
  UserCircle,
} from '@phosphor-icons/react'
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

/**
 * 실제 화면이 있는 계정 구역. 히어로 제목·breadcrumb 표시에 쓴다.
 *
 * 2026-09-16 — 사이드바에 "준비 중"으로 남아 있던 8개 화면을 프론트엔드 UI로 채웠다
 * (백엔드는 아직 없음, 목데이터로 채운 화면). `NAV_GROUPS` 의 `to` 도 함께 채웠다.
 */
export type ConsumerAccountSection =
  | 'home'
  | 'orders'
  | 'addresses'
  | 'cancellations'
  | 'wishlist'
  | 'recentlyViewed'
  | 'askInquiry'
  | 'inquiries'
  | 'reviews'
  | 'profile'
  | 'predictions'

export interface ConsumerAccountLayoutProps {
  children: ReactNode
  current: ConsumerAccountSection
  /** 사이드바와 머리말에 쓴다. 아직 모르면(조회 전) 생략한다 */
  profile?: { name: string; email: string } | null
  onSignOut?: () => void
  signOutPending?: boolean
  signOutError?: unknown
}

// breadcrumb 의 마지막 칸에 적을 이름. 나머지(경로·아이콘)는 NAV_GROUPS 가 따로 갖는다.
const SECTIONS: Record<ConsumerAccountSection, { label: string }> = {
  home: { label: '내 프로필' },
  orders: { label: '주문 내역' },
  addresses: { label: '배송지 관리' },
  cancellations: { label: '취소/반품/교환/환불내역' },
  wishlist: { label: '찜한 상품' },
  recentlyViewed: { label: '최근 본 상품' },
  askInquiry: { label: '문의하기' },
  inquiries: { label: '문의내역' },
  reviews: { label: '리뷰관리' },
  profile: { label: '개인정보확인/수정' },
  predictions: { label: '판매량 예측시장' },
}

/** 사이드바 메뉴. My쇼핑·My활동·My정보 세 분류로 묶는다(2026-09-16 개편). */
interface ConsumerNavItem {
  label: string
  to: string
  Icon: typeof User
  /**
   * 정확히 이 경로일 때만 활성화한다.
   *
   * **"문의내역"(`/mypage/inquiries`)이 "문의하기"(`/mypage/inquiries/new`)의 접두사라서
   * 필요하다.** `NavLink` 는 기본적으로 하위 경로도 활성으로 보므로, 문의하기 화면에서
   * 문의내역까지 함께 진한 배경으로 표시되는 문제가 있었다.
   */
  end?: boolean
}

interface ConsumerNavGroup {
  label: string
  items: ConsumerNavItem[]
}

const NAV_GROUPS: readonly ConsumerNavGroup[] = [
  {
    label: 'My쇼핑',
    items: [
      { label: '주문목록/배송조회', to: '/mypage/orders', Icon: Receipt },
      { label: '취소/반품/교환/환불내역', to: '/mypage/cancellations', Icon: ArrowsClockwise },
      { label: '찜한 상품', to: '/mypage/wishlist', Icon: Heart },
      { label: '최근 본 상품', to: '/mypage/recently-viewed', Icon: ClockCounterClockwise },
    ],
  },
  {
    label: 'My활동',
    items: [
      { label: '판매량 예측시장', to: '/mypage/predictions', Icon: ChartBar },
      { label: '문의하기', to: '/mypage/inquiries/new', Icon: ChatCircleText },
      { label: '문의내역', to: '/mypage/inquiries', Icon: ChatText, end: true },
      { label: '리뷰관리', to: '/mypage/reviews', Icon: Star },
    ],
  },
  {
    label: 'My정보',
    items: [
      { label: '개인정보확인/수정', to: '/mypage/profile', Icon: UserCircle },
      { label: '배송지 관리', to: '/mypage/addresses', Icon: MapPin },
    ],
  },
]

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
  const location = useLocation()

  // 사이드바로 다른 화면으로 넘어가도 이전 화면에서 내려가 있던 스크롤 위치가 그대로
  // 남는다 — 새 화면은 맨 위부터 보여야 한다.
  useEffect(() => {
    // { top: 0, left: 0 } 만 넘기면 전역 `scroll-behavior: smooth`(index.css)를 타서
    // 애니메이션으로 서서히 올라간다 — 화면이 막 바뀐 자리에서는 그 몇 백 ms 동안
    // 이전 화면 스크롤 위치가 그대로 남아 있는 것처럼 보인다. `instant` 로 즉시 올린다.
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  }, [location.pathname])

  return (
    <div className="consumer-mypage">
      <a className="consumer-skip-link" href="#consumer-main-content">본문으로 건너뛰기</a>
      <AccountHeader
        onSignOut={onSignOut}
        signOutPending={signOutPending}
        signOutError={signOutError}
      />

      <main id="consumer-main-content" className="consumer-mypage-main">
        <nav className="consumer-breadcrumb" aria-label="현재 위치">
          {/* 아이콘 없이 글자로만 시작한다 — 아이콘이 있으면 글자가 로고보다 오른쪽으로
              밀려 시작점이 어긋난다. */}
          <Link to="/">홈</Link>
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
            <nav aria-label="마이페이지 메뉴" className="consumer-account-sidebar__nav">
              {NAV_GROUPS.map((group) => (
                <div className="consumer-account-sidebar__group" key={group.label}>
                  <p className="consumer-account-sidebar__group-label">{group.label}</p>
                  {group.items.map((item) => (
                    // NavLink 가 활성일 때 aria-current="page" 를 붙인다. 사이드바 CSS 가
                    // 그 속성을 보고 있어 따로 class 를 주지 않는다.
                    <NavLink key={item.label} to={item.to} end={item.end}>
                      <item.Icon aria-hidden="true" />
                      <span>{item.label}</span>
                    </NavLink>
                  ))}
                </div>
              ))}
            </nav>
          </aside>

          <div className="consumer-mypage-content">{children}</div>
        </div>
      </main>

      <AccountFooter />
    </div>
  )
}

/**
 * 로그아웃은 여기(머리말)에 둔다.
 *
 * **파란 소개 배너(2026-09-16 삭제)가 있던 자리다.** 화면마다 제목·설명을 큰 박스로
 * 반복해 보여줬는데, 사이드바·breadcrumb 로 이미 "어디 있는지" 는 충분히 드러나 로그아웃
 * 버튼 하나 두려고 그 박스 전체를 유지할 이유가 없었다. 로그아웃은 어느 계정 화면에서든
 * 화면 맨 위에서 바로 눌러야 해 머리말로 옮겼다.
 */
function AccountHeader({
  onSignOut,
  signOutPending = false,
  signOutError,
}: {
  onSignOut?: () => void
  signOutPending?: boolean
  signOutError?: unknown
} = {}) {
  return (
    <header className="consumer-account-header">
      <div className="consumer-account-header__inner">
        <Link className="consumer-account-header__logo" to="/" aria-label="SAEROI 홈">
          {/* public/ 자산 경로라 vite base 가 자동으로 안 붙는다 — /test/ 에서 404 방지 */}
          <img src={`${import.meta.env.BASE_URL}img/logo.png`} alt="" />
        </Link>

        <nav className="consumer-account-header__navigation" aria-label="쇼핑 메뉴">
          <Link to="/shop">쇼핑</Link>
          {/*
            소재 마켓은 사업자끼리 소재를 사고파는 곳이다. 소비자 머리말에 두면 눌러서
            들어갔다가 권한 없음 화면을 만난다 — /marketplace 는 사업자 전용이다.
          */}
          <Link to="/#featured-brands">브랜드</Link>
          <Link to="/#about">스토리</Link>
        </nav>

        <div className="consumer-account-header__actions">
          <Link className="consumer-account-header__shop-link" to="/shop">
            <ShoppingBag aria-hidden="true" />
            <span>쇼핑 계속하기</span>
          </Link>
          {onSignOut && (
            <button
              type="button"
              className="consumer-account-header__signout"
              disabled={signOutPending}
              onClick={onSignOut}
            >
              <SignOut aria-hidden="true" />
              <span>{signOutPending ? '로그아웃 중…' : '로그아웃'}</span>
            </button>
          )}
        </div>
      </div>
      {signOutError ? (
        <p className="consumer-account-header__signout-error" role="alert">
          {toErrorMessage(signOutError)}
        </p>
      ) : null}
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
