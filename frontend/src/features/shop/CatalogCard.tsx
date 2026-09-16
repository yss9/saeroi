import { useState } from 'react'
import { Link } from 'react-router-dom'
import { publicProductImageUrl } from '../product/api/publicProductImageUrl.ts'
import type { PublicProductModel } from '../product/types/publicProduct.types.ts'

const price = new Intl.NumberFormat('ko-KR')

/** SHOP과 검색이 공유하는 공개 상품 카드. */
export function CatalogCard({ product }: { product: PublicProductModel }) {
  const src = publicProductImageUrl(product.thumbnailUrl)
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  return (
    <article className="shop-catalog__card" aria-label={`${product.name} 상품`}>
      <Link to={`/products/${product.productModelId}`}>
        <div className="shop-catalog__image">
          {src && failedSrc !== src ? <img src={src} alt={`${product.brandName} ${product.name}`} loading="lazy" onError={() => setFailedSrc(src)} /> : <span className="shop-catalog__image-fallback">이미지 준비 중</span>}
          {product.stockQuantity === 0 ? <span className="shop-catalog__sold-out">품절</span> : null}
        </div>
        <p className="shop-catalog__brand">{product.brandName}</p>
        <h2>{product.name}</h2>
        <p className="shop-catalog__price">₩ {price.format(product.price)}</p>
      </Link>
    </article>
  )
}
