import {
  Buildings,
  List,
  MagnifyingGlass,
  ShieldCheck,
  ShoppingBag,
  SignIn,
  SignOut,
  UserCircle,
  UserPlus,
  X,
} from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../../features/auth/hooks/useAuth.ts'
import { useShop } from '../../../features/shop/ShopContext.tsx'
import type { UserRole } from '../../../types/role.types.ts'

const navigation = [
  { label: '신상품', href: '#curated-picks' },
  { label: '브랜드', href: '#featured-brands' },
  { label: '지속가능성', href: '#trust' },
  { label: '스토리', href: '#about' },
]

const businessRoles: readonly UserRole[] = ['SUPPLIER', 'BRAND', 'PROCESSOR']

function getAccountLink(role: UserRole) {
  if (role === 'CONSUMER') return { label: '마이페이지', to: '/mypage', icon: UserCircle }
  if (role === 'ADMIN') return { label: '관리자 페이지', to: '/admin', icon: ShieldCheck }
  return { label: '회사 대시보드', to: '/company/account', icon: Buildings }
}

function getAccountTypeLabel(role: UserRole) {
  if (role === 'CONSUMER') return '개인회원'
  if (role === 'ADMIN') return '관리자'
  if (businessRoles.includes(role)) return '사업자회원'
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

  const accountLink = user ? getAccountLink(user.role) : null

  return (
    <header className="site-header">
      <a className="site-header__logo" href="/" aria-label="SAEROI 홈">
        <img src="/img/logo.png" alt="" />
      </a>

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
            <div id="account-popover" className="site-header__account-popover">
              {isAuthenticated && user && accountLink ? (
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
                    <accountLink.icon aria-hidden="true" />
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
