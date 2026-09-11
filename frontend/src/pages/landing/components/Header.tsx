import {
  List,
  MagnifyingGlass,
  ShoppingBag,
  SignIn,
  SignOut,
  UserCircle,
  UserPlus,
  X,
} from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ROLE_HOME_MENU_ITEMS } from '../../../app/navigation/navigationMenu.ts'
import { useVisibleMenu } from '../../../app/navigation/useVisibleMenu.ts'
import { useAuth } from '../../../features/auth/hooks/useAuth.ts'
import { BUSINESS_ROLES } from '../../../features/company/types/companyAccount.types.ts'
import { useShop } from '../../../features/shop/ShopContext.tsx'
import type { UserRole } from '../../../types/role.types.ts'

// 랜딩 섹션 앵커. 로그인·역할과 무관한 공개 정보라 필터를 걸지 않는다
// (routes.tsx 의 Public 구역과 같은 성격이다).
const navigation = [
  { label: '신상품', href: '#curated-picks' },
  { label: '브랜드', href: '#featured-brands' },
  { label: '지속가능성', href: '#trust' },
  { label: '스토리', href: '#about' },
]

function getAccountTypeLabel(role: UserRole) {
  if (role === 'CONSUMER') return '개인회원'
  if (role === 'ADMIN') return '관리자'
  if ((BUSINESS_ROLES as readonly UserRole[]).includes(role)) return '사업자회원'
  return ''
}

export function Header() {
  const { cartCount } = useShop()
  const { user, isAuthenticated, signOut } = useAuth()
  const navigate = useNavigate()
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [isAccountOpen, setIsAccountOpen] = useState(false)
  const accountRef = useRef<HTMLDivElement>(null)
  const accountTriggerRef = useRef<HTMLButtonElement>(null)
  const menuTriggerRef = useRef<HTMLButtonElement>(null)
  const shouldRestoreMenuFocusRef = useRef(false)

  useEffect(() => {
    if (!isMenuOpen && shouldRestoreMenuFocusRef.current) {
      shouldRestoreMenuFocusRef.current = false
      menuTriggerRef.current?.focus()
    }
  }, [isMenuOpen])

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

  function closeMobileMenuFromLink() {
    shouldRestoreMenuFocusRef.current = true
    setIsMenuOpen(false)
  }

  function toggleAccountMenu() {
    setIsMenuOpen(false)
    setIsAccountOpen((isOpen) => !isOpen)
  }

  function handleSignOut() {
    signOut()
    setIsAccountOpen(false)
    navigate('/', { replace: true })
  }

  // 역할별 "내 홈" 링크. 어느 역할이 어디로 가는지를 이 파일이 정하지 않는다 —
  // routes.tsx 의 RoleGuard 가 정하고 useVisibleMenu 가 걸러 준다. 그래서
  // /mypage 나 /admin 의 allow 가 바뀌면 여기 손대지 않아도 따라온다.
  const { items: accountItems, isResolving: isRoleResolving } = useVisibleMenu(ROLE_HOME_MENU_ITEMS)
  const accountLink = accountItems[0] ?? null

  return (
    <header className="site-header">
      {/* 일반 <a href="/">는 basename(/test/)을 무시하고 도메인 루트로 풀 네비게이션한다 —
          test 스택에서는 이게 운영 메인으로 튀어나가는 것으로 드러난다. Link 로 바꿔야
          basename 이 자동으로 반영된다. */}
      <Link className="site-header__logo" to="/" aria-label="SAEROI 홈">
        <img src="/img/logo.png" alt="" />
      </Link>

      <nav className="site-header__navigation" aria-label="주요 메뉴">
        {navigation.map((item) => (
          <a key={item.label} href={item.href}>{item.label}</a>
        ))}
      </nav>

      <div className="site-header__actions">
        <button type="button" aria-label="상품 검색">
          <MagnifyingGlass aria-hidden="true" />
        </button>

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
                    <span>{getAccountTypeLabel(user.role)}</span>
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
                    onClick={handleSignOut}
                  >
                    <SignOut aria-hidden="true" />
                    로그아웃
                  </button>
                </>
              ) : (
                <>
                  <div className="site-header__account-summary">
                    <strong>SAEROI에 오신 것을 환영합니다.</strong>
                    <span>로그인하고 새로이의 서비스를 이용해 보세요.</span>
                  </div>
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

        <a className="site-header__cart" href="#cart" aria-label={`장바구니, 상품 ${cartCount}개`}>
          <ShoppingBag aria-hidden="true" />
          <span className="site-header__cart-count" aria-hidden="true">{cartCount}</span>
        </a>

        <button
          ref={menuTriggerRef}
          className="site-header__menu-button"
          type="button"
          aria-label={isMenuOpen ? '메뉴 닫기' : '메뉴 열기'}
          aria-expanded={isMenuOpen}
          aria-controls="mobile-navigation"
          onClick={() => {
            setIsAccountOpen(false)
            setIsMenuOpen((isOpen) => !isOpen)
          }}
        >
          {isMenuOpen ? <X aria-hidden="true" /> : <List aria-hidden="true" />}
        </button>
      </div>

      <nav
        id="mobile-navigation"
        className="site-header__mobile-navigation"
        aria-label="모바일 메뉴"
        hidden={!isMenuOpen}
      >
        {navigation.map((item) => (
          <a key={item.label} href={item.href} onClick={closeMobileMenuFromLink}>
            {item.label}
          </a>
        ))}
      </nav>
    </header>
  )
}
