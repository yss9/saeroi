import { ArrowLeft, ArrowRight, ShoppingBag } from '@phosphor-icons/react'
import { WishlistButton } from '../../../features/wishlist/WishlistButton.tsx'
import { useMemo, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { publicProductImageUrl } from '../../../features/product/api/publicProductImageUrl.ts'
import { useAuth } from '../../../features/auth/hooks/useAuth.ts'
import { usePublicProductModels } from '../../../features/product/hooks/usePublicProductModels.ts'
import type { PublicProductModel } from '../../../features/product/types/publicProduct.types.ts'
import type { ProductCategory } from '../../../features/product/types/product.types.ts'
import { useShop } from '../../../features/shop/ShopContext.tsx'
import type { HomepageProduct } from '../content.ts'

const priceFormatter = new Intl.NumberFormat('ko-KR')
const HOME_PRODUCT_COUNT = 4

const categoryMaterial: Record<ProductCategory, string> = {
  BAG: 'UPCYCLED BAG',
  WALLET: 'UPCYCLED WALLET',
  CLOTHING: 'UPCYCLED CLOTHING',
  ACCESSORY: 'UPCYCLED ACCESSORY',
  HOME: 'UPCYCLED HOME',
  ETC: 'UPCYCLED PRODUCT',
}

const imagePositions: Record<string, string> = {
  '/images/landing/product-denim-circle.webp': '50% 46%',
  '/images/landing/product-work-jacket.webp': '50% 44%',
  '/images/landing/product-saddle-bag.webp': '50% 50%',
  '/images/landing/product-bucket-hat.webp': '50% 35%',
}

function toHomepageProduct(product: PublicProductModel): HomepageProduct {
  return {
    id: String(product.productModelId),
    name: product.name,
    brand: product.brandName,
    price: product.price,
    material: categoryMaterial[product.category],
    image: publicProductImageUrl(product.thumbnailUrl),
    imagePosition: imagePositions[product.thumbnailUrl] ?? '50% 50%',
  }
}

export function ShopShowcase() {
  const [activeIndex, setActiveIndex] = useState(0)
  const catalog = usePublicProductModels({ page: 0, size: HOME_PRODUCT_COUNT })
  const { addToCart } = useShop()
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const products = useMemo(
    () => catalog.data?.content.map(toHomepageProduct) ?? [],
    [catalog.data],
  )
  const product = products.length > 0 ? products[activeIndex % products.length] : null

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
    if (products.length === 0) return
    setActiveIndex((current) => (current + direction + products.length) % products.length)
  }

  return (
    <section id="shop" className="shop-showcase" aria-labelledby="shop-title">
      <header className="editorial-heading editorial-heading--light">
        <div data-reveal>
          <h2 id="shop-title">SHOP</h2>
          <p className="editorial-heading__subtitle">New Life, New Form</p>
        </div>
        <Link className="text-link" to="/shop" data-reveal>
          VIEW ALL PRODUCTS <ArrowRight aria-hidden="true" />
        </Link>
      </header>

      <div className="shop-showcase__stage">
        <div className="shop-showcase__media" aria-live="polite" data-reveal>
          {product ? (
            <>
              <img
                key={product.id}
                src={product.image}
                alt={`${product.brand} ${product.name}`}
                style={{ objectPosition: product.imagePosition }}
              />
              <p className="shop-showcase__material">{product.material}</p>
            </>
          ) : (
            <div className="shop-showcase__media-placeholder" aria-hidden="true" />
          )}
        </div>

        <article
          className="shop-showcase__product"
          aria-label={product ? `${product.name} 상품` : '상품 안내'}
          aria-busy={catalog.isPending}
          data-reveal
        >
          {catalog.isPending ? (
            <CatalogState title="상품을 불러오는 중" />
          ) : catalog.isError ? (
            <CatalogState
              title="상품을 불러오지 못했습니다"
              description="잠시 후 다시 시도해 주세요."
              error
              onRetry={() => void catalog.refetch()}
            />
          ) : product ? (
            <>
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
                <WishlistButton productId={Number(product.id)} name={product.name} className="shop-showcase__wishlist" returnPath={`${location.pathname}#shop`} />
              </div>

              <div className="shop-showcase__controls">
                <div
                  className="shop-showcase__progress"
                  aria-label={`${products.length}개 상품 중 ${(activeIndex % products.length) + 1}번째`}
                >
                  {products.map((item, index) => (
                    <span
                      key={item.id}
                      className={index === activeIndex % products.length ? 'is-active' : undefined}
                    />
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
            </>
          ) : (
            <CatalogState
              title="판매 중인 상품이 없습니다"
              description="브랜드의 새로운 상품을 준비하고 있습니다."
            />
          )}
        </article>
      </div>
      <span id="shop-all" className="landing-page__anchor" aria-hidden="true" />
    </section>
  )
}

interface CatalogStateProps {
  title: string
  description?: string
  error?: boolean
  onRetry?: () => void
}

function CatalogState({ title, description, error = false, onRetry }: CatalogStateProps) {
  return (
    <div className="shop-showcase__state" role={error ? 'alert' : 'status'}>
      <strong>{title}</strong>
      {description ? <p>{description}</p> : null}
      {onRetry ? (
        <button type="button" onClick={onRetry}>
          다시 시도
        </button>
      ) : null}
    </div>
  )
}
