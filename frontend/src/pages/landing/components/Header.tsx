import {
  MagnifyingGlass,
  ShoppingBag,
  SignIn,
  SignOut,
  UserCircle,
  UserPlus,
} from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import logoUrl from '../../../assets/saeroi-logo-vector.svg'
import { ROLE_HOME_MENU_ITEMS } from '../../../app/navigation/navigationMenu.ts'
import { useVisibleMenu } from '../../../app/navigation/useVisibleMenu.ts'
import { useAuth } from '../../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../../features/auth/hooks/useLogout.ts'
import { BUSINESS_ROLES } from '../../../features/company/types/companyAccount.types.ts'
import { useShop } from '../../../features/shop/ShopContext.tsx'
import type { UserRole } from '../../../types/role.types.ts'

// 소비자 페이지 공통 내비게이션. 목적지 페이지와 함께 공개 링크를 활성화한다.
const navigation = [
  { label: 'SHOP', homeHref: '/shop', route: '/shop' },
  { label: 'BRANDS', homeHref: '/brands', route: '/brands' },
  { label: 'ABOUT', homeHref: '/about', route: '/about' },
]

const predictionNavigation = {
  label: 'PREDICTION',
  homeHref: '/mypage/predictions',
  route: '/mypage/predictions',
}

interface HeaderProps {
  isLogoVisible: boolean
  scrollToneViewportRatio?: number
  // 랜딩 페이지의 휠 페이지네이션처럼 실제 document 스크롤이 없는 화면에서는
  // window.scrollY 감지 대신 호출부가 톤을 직접 넘긴다. undefined면 기존 스크롤 감지를 쓴다.
  forceScrolled?: boolean
}

function getAccountTypeLabel(role: UserRole) {
  if (role === 'CONSUMER') return ''
  if (role === 'ADMIN') return '관리자'
  if ((BUSINESS_ROLES as readonly UserRole[]).includes(role)) return '사업자회원'
  return ''
}

export function Header({ isLogoVisible, scrollToneViewportRatio, forceScrolled }: HeaderProps) {
  const { cartCount } = useShop()
  const { user, isAuthenticated } = useAuth()
  const logout = useLogout()
  const navigate = useNavigate()
  const location = useLocation()
  const [isAccountOpen, setIsAccountOpen] = useState(false)
  const [scrollTone, setScrollTone] = useState(false)
  const accountRef = useRef<HTMLDivElement>(null)
  const accountTriggerRef = useRef<HTMLButtonElement>(null)
  const isScrolled = forceScrolled ?? scrollTone

  useEffect(() => {
    if (forceScrolled !== undefined) return

    function updateHeaderTone() {
      const threshold = scrollToneViewportRatio
        ? window.innerHeight * scrollToneViewportRatio
        : 32
      setScrollTone(window.scrollY > threshold)
    }

    updateHeaderTone()
    window.addEventListener('scroll', updateHeaderTone, { passive: true })
    window.addEventListener('resize', updateHeaderTone)
    return () => {
      window.removeEventListener('scroll', updateHeaderTone)
      window.removeEventListener('resize', updateHeaderTone)
    }
  }, [scrollToneViewportRatio, forceScrolled])

  useEffect(() => {
    if (!isAccountOpen) return

    function closeOnOutsidePointer(event: PointerEvent) {
      if (!accountRef.current?.contains(event.target as Node)) {
        setIsAccountOpen(false)
      }
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsAccountOpen(false)
        accountTriggerRef.current?.focus()
      }
    }

    document.addEventListener('pointerdown', closeOnOutsidePointer)
    document.addEventListener('keydown', closeOnEscape)

    return () => {
      document.removeEventListener('pointerdown', closeOnOutsidePointer)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [isAccountOpen])

  function toggleAccountMenu() {
    setIsAccountOpen((isOpen) => !isOpen)
  }

  function handleSignOut() {
    logout.mutate(undefined, {
      onSuccess: () => {
        setIsAccountOpen(false)
        navigate('/', { replace: true })
      },
    })
  }

  function handleCartEntry() {
    navigate('/cart')
  }

  // 역할별 "내 홈" 링크. 어느 역할이 어디로 가는지를 이 파일이 정하지 않는다 —
  // routes.tsx 의 RoleGuard 가 정하고 useVisibleMenu 가 걸러 준다. 그래서
  // /mypage 나 /admin 의 allow 가 바뀌면 여기 손대지 않아도 따라온다.
  const { items: accountItems, isResolving: isRoleResolving } = useVisibleMenu(ROLE_HOME_MENU_ITEMS)
  const accountLink = accountItems[0] ?? null
  const primaryNavigation = user?.role === 'CONSUMER'
    ? [...navigation, predictionNavigation]
    : navigation

  return (
    <header className={`site-header${isScrolled ? ' site-header--scrolled' : ''}${isLogoVisible ? ' site-header--logo-visible' : ''}`}>
      {/* 일반 <a href="/">는 basename(/test/)을 무시하고 도메인 루트로 풀 네비게이션한다 —
          test 스택에서는 이게 운영 메인으로 튀어나가는 것으로 드러난다. Link 로 바꿔야
          basename 이 자동으로 반영된다. */}
      <Link
        className="site-header__logo"
        to="/"
        aria-label="SAEROI 홈"
        aria-hidden={!isLogoVisible}
        tabIndex={isLogoVisible ? undefined : -1}
      >
        <img src={logoUrl} alt="" />
      </Link>

      <nav className="site-header__navigation" aria-label="주요 메뉴">
        {primaryNavigation.map((item) => location.pathname === '/' && item.homeHref.startsWith('#') ? (
          <a key={item.label} href={item.homeHref}>{item.label}</a>
        ) : (
          <Link key={item.label} to={item.route}>{item.label}</Link>
        ))}
      </nav>

      <div className="site-header__actions">
        <Link to="/search" aria-label="상품 검색">
          <MagnifyingGlass aria-hidden="true" />
        </Link>

        <div className="site-header__account" ref={accountRef}>
          <button
            ref={accountTriggerRef}
            type="button"
            aria-label="계정 메뉴"
            aria-expanded={isAccountOpen}
            aria-controls="account-popover"
            onClick={toggleAccountMenu}
          >
            <UserCircle aria-hidden="true" />
          </button>
          {isAccountOpen && (
            <div id="account-popover" className="site-header__account-popover" aria-busy={isRoleResolving}>
              {isRoleResolving ? (
                // 로그인은 됐지만 역할을 아직 모르는 구간. 여기서 비회원 안내를 그리면
                // 로그인한 사용자에게 "로그인" 버튼이 잠깐 보였다가 바뀐다.
                <div className="site-header__account-summary">
                  <strong>계정 정보를 확인하는 중입니다.</strong>
                </div>
              ) : isAuthenticated && user && accountLink ? (
                <>
                  <div className="site-header__account-summary">
                    <strong>{user.name}</strong>
                    {getAccountTypeLabel(user.role) && <span>{getAccountTypeLabel(user.role)}</span>}
                  </div>
                  <Link
                    className="site-header__account-link"
                    to={accountLink.to}
                    onClick={() => setIsAccountOpen(false)}
                  >
                    <accountLink.Icon aria-hidden="true" />
                    {accountLink.label}
                  </Link>
                  <button
                    className="site-header__account-link site-header__account-link--muted"
                    type="button"
                    disabled={logout.isPending}
                    onClick={handleSignOut}
                  >
                    <SignOut aria-hidden="true" />
                    {logout.isPending ? '로그아웃 중…' : '로그아웃'}
                  </button>
                  {logout.error ? <p role="alert">{logout.error.message}</p> : null}
                </>
              ) : (
                <>
                  <Link
                    className="site-header__account-link site-header__account-link--primary"
                    to="/login"
                    onClick={() => setIsAccountOpen(false)}
                  >
                    <SignIn aria-hidden="true" />
                    로그인
                  </Link>
                  <Link
                    className="site-header__account-link"
                    to="/signup"
                    onClick={() => setIsAccountOpen(false)}
                  >
                    <UserPlus aria-hidden="true" />
                    회원가입
                  </Link>
                </>
              )}
            </div>
          )}
        </div>

        <button
          className="site-header__cart"
          type="button"
          aria-label={`장바구니, 상품 ${cartCount}개`}
          onClick={handleCartEntry}
        >
          <ShoppingBag aria-hidden="true" />
          {cartCount > 0 ? (
            <span className="site-header__cart-count" aria-hidden="true">{cartCount > 99 ? '99+' : cartCount}</span>
          ) : null}
        </button>

      </div>
    </header>
  )
}
