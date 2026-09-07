import { List, MagnifyingGlass, ShoppingBag, UserCircle, X } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { useShop } from '../../../features/shop/ShopContext.tsx'
import { WalletButton } from '../../../features/wallet/components/WalletButton.tsx'

const navigation = [
  { label: '신상품', href: '#curated-picks' },
  { label: '브랜드', href: '#featured-brands' },
  { label: '지속가능성', href: '#trust' },
  { label: '스토리', href: '#about' },
]

export function Header() {
  const { cartCount } = useShop()
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [isWalletOpen, setIsWalletOpen] = useState(false)
  const menuTriggerRef = useRef<HTMLButtonElement>(null)
  const shouldRestoreMenuFocusRef = useRef(false)

  useEffect(() => {
    if (!isMenuOpen && shouldRestoreMenuFocusRef.current) {
      shouldRestoreMenuFocusRef.current = false
      menuTriggerRef.current?.focus()
    }
  }, [isMenuOpen])

  function closeMobileMenuFromLink() {
    shouldRestoreMenuFocusRef.current = true
    setIsMenuOpen(false)
  }

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

        <div className="site-header__wallet">
          <button
            type="button"
            aria-label="계정 및 지갑"
            aria-expanded={isWalletOpen}
            aria-controls="wallet-popover"
            onClick={() => setIsWalletOpen((isOpen) => !isOpen)}
          >
            <UserCircle aria-hidden="true" />
          </button>
          {isWalletOpen && (
            <div id="wallet-popover" className="site-header__wallet-popover">
              <WalletButton />
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
          onClick={() => setIsMenuOpen((isOpen) => !isOpen)}
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
