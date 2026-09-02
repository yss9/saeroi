import { ArrowRight, Heart } from '@phosphor-icons/react'
import { useShop } from '../../../features/shop/ShopContext.tsx'
import { products } from '../content.ts'

export function CuratedPicks() {
  const { wishlist, toggleWishlist } = useShop()

  return (
    <section id="curated-picks" className="curated-picks" aria-labelledby="curated-picks-title">
      <div className="section-heading">
        <div>
          <p>CURATED PICKS</p>
          <h2 id="curated-picks-title">당신만을 위한 업사이클링 픽</h2>
        </div>
        <a href="#curated-picks">모든 제품 보기 <ArrowRight aria-hidden="true" /></a>
      </div>

      <div className="curated-picks__list">
        {products.map((product) => {
          const isWished = wishlist.has(product.id)

          return (
            <article key={product.id} className="product-card" aria-label={`${product.name} 상품`}>
              <div className="product-card__media">
                <img
                  src={product.image}
                  alt={`${product.brand} ${product.name}`}
                  style={{ objectPosition: product.imagePosition }}
                />
                <button
                  className="product-card__wishlist"
                  type="button"
                  aria-label={`${product.name} ${isWished ? '찜 해제' : '찜하기'}`}
                  aria-pressed={isWished}
                  onClick={() => toggleWishlist(product.id)}
                >
                  <Heart aria-hidden="true" weight={isWished ? 'fill' : 'regular'} />
                </button>
              </div>
              <h3>{product.name}</h3>
              <p className="product-card__brand">{product.brand}</p>
              <p className="product-card__price">{product.price}</p>
            </article>
          )
        })}
      </div>
    </section>
  )
}
