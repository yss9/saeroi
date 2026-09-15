import { ArrowLeft, ArrowRight, Heart, ShoppingBag } from '@phosphor-icons/react'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../../features/auth/hooks/useAuth.ts'
import { useShop } from '../../../features/shop/ShopContext.tsx'
import { homepageProducts } from '../content.ts'

const priceFormatter = new Intl.NumberFormat('ko-KR')

export function ShopShowcase() {
  const [activeIndex, setActiveIndex] = useState(0)
  const [isWishlistHovered, setIsWishlistHovered] = useState(false)
  const { wishlist, toggleWishlist, addToCart } = useShop()
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const product = homepageProducts[activeIndex]
  const isWished = wishlist.has(product.id)

  function requireLogin(action: () => void) {
    if (isAuthenticated) {
      action()
      return
    }

    navigate('/login', {
      state: { from: { pathname: `${location.pathname}#shop` } },
    })
  }

  function move(direction: -1 | 1) {
    setActiveIndex((current) => (
      (current + direction + homepageProducts.length) % homepageProducts.length
    ))
  }

  return (
    <section id="shop" className="shop-showcase" aria-labelledby="shop-title">
      <header className="editorial-heading editorial-heading--light">
        <div data-reveal>
          <h2 id="shop-title">SHOP</h2>
          <p className="editorial-heading__subtitle">New Life, New Form</p>
        </div>
        <a className="text-link" href="#shop-all" data-reveal>
          VIEW ALL PRODUCTS <ArrowRight aria-hidden="true" />
        </a>
      </header>

      <div className="shop-showcase__stage">
        <div className="shop-showcase__media" aria-live="polite" data-reveal>
          <img
            key={product.id}
            src={product.image}
            alt={`${product.brand} ${product.name}`}
            style={{ objectPosition: product.imagePosition }}
          />
          <p className="shop-showcase__material">{product.material}</p>
        </div>

        <article
          className="shop-showcase__product"
          aria-label={`${product.name} 상품`}
          data-reveal
        >
          <p className="shop-showcase__brand">{product.brand}</p>
          <h3>{product.name}</h3>
          <p className="shop-showcase__price">₩ {priceFormatter.format(product.price)}</p>
          <div className="shop-showcase__actions">
            <button
              type="button"
              className="shop-showcase__bag"
              aria-label={`${product.name} 장바구니 담기`}
              onClick={() => requireLogin(() => addToCart(product.id))}
            >
              <ShoppingBag aria-hidden="true" />
            </button>
            <button
              type="button"
              className="shop-showcase__wishlist"
              aria-label={`${product.name} ${isWished ? '찜 해제' : '찜하기'}`}
              aria-pressed={isWished}
              onClick={() => requireLogin(() => toggleWishlist(product.id))}
              onPointerEnter={() => setIsWishlistHovered(true)}
              onPointerLeave={() => setIsWishlistHovered(false)}
              onFocus={() => setIsWishlistHovered(true)}
              onBlur={() => setIsWishlistHovered(false)}
            >
              <Heart
                aria-hidden="true"
                weight={isWished || isWishlistHovered ? 'fill' : 'regular'}
              />
            </button>
          </div>

          <div className="shop-showcase__controls">
            <div className="shop-showcase__progress" aria-label={`${homepageProducts.length}개 상품 중 ${activeIndex + 1}번째`}>
              {homepageProducts.map((item, index) => (
                <span key={item.id} className={index === activeIndex ? 'is-active' : undefined} />
              ))}
            </div>
            <div>
              <button type="button" aria-label="이전 상품" onClick={() => move(-1)}>
                <ArrowLeft aria-hidden="true" />
              </button>
              <button type="button" aria-label="다음 상품" onClick={() => move(1)}>
                <ArrowRight aria-hidden="true" />
              </button>
            </div>
          </div>
        </article>
      </div>
      <span id="shop-all" className="landing-page__anchor" aria-hidden="true" />
    </section>
  )
}
