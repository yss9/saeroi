import { useState } from 'react'
import { Link } from 'react-router-dom'
import { publicProductImageUrl } from '../product/api/publicProductImageUrl.ts'
import type { PublicProductModel } from '../product/types/publicProduct.types.ts'
import { useConsumerOrderThumbnail } from '../user/hooks/useConsumerOrderThumbnail.ts'
import { WishlistButton } from '../wishlist/WishlistButton.tsx'
import { CartButton } from '../cart/CartButton.tsx'

const price = new Intl.NumberFormat('ko-KR')

/** 소비자 카탈로그와 찜 목록이 공유하는 상품 카드. 인증 이미지 모드는 기존 주문 로더를 쓴다. */
export function CatalogCard({ product, imageMode = 'public', purchasable, wishedInList = false }: { product: PublicProductModel; imageMode?: 'public' | 'authenticated'; purchasable?: boolean; wishedInList?: boolean }) {
  const canOpen = purchasable !== false
  const state = purchasable === false ? (product.status === 'DISCONTINUED' || product.status === 'DRAFT' ? '판매 종료' : product.status === 'SOLD_OUT' || product.stockQuantity === 0 ? '품절' : '구매 불가') : product.stockQuantity === 0 ? '품절' : null
  const image = <div className="shop-catalog__image">
    {imageMode === 'authenticated' ? <AuthenticatedImage product={product} /> : <CardImage src={publicProductImageUrl(product.thumbnailUrl)} name={product.name} brand={product.brandName} />}
    {state && <span className="shop-catalog__sold-out">{state}</span>}
  </div>
  return (
    <article className="shop-catalog__card" aria-label={`${product.name} 상품`}>
      {canOpen ? <Link to={`/products/${product.productModelId}`} aria-label={`${product.name} 이미지`}>{image}</Link> : image}
      <WishlistButton productId={product.productModelId} name={product.name} className="shop-catalog__heart" wishedInList={wishedInList} />
      <p className="shop-catalog__brand">{canOpen ? <Link to={`/brands/${product.brandId}`}>{product.brandName}</Link> : product.brandName}</p>
      {canOpen ? <Link to={`/products/${product.productModelId}`} aria-label={`${product.name} 상세 보기`}>
        <h2>{product.name}</h2>
        <p className="shop-catalog__price">₩ {price.format(product.price)}</p>
      </Link> : <><h2>{product.name}</h2><p className="shop-catalog__price">₩ {price.format(product.price)}</p></>}
      <CartButton productId={product.productModelId} name={product.name} className="shop-catalog__cart" disabled={purchasable === false || product.stockQuantity === 0} />
    </article>
  )
}

function AuthenticatedImage({ product }: { product: PublicProductModel }) {
  // Do not request a missing URL; the child owns the existing authenticated hook.
  return product.thumbnailUrl ? <AuthenticatedImageBody product={product} /> : <span className="shop-catalog__image-fallback">이미지 준비 중</span>
}
function AuthenticatedImageBody({ product }: { product: PublicProductModel }) {
  const image = useConsumerOrderThumbnail(product.thumbnailUrl)
  return <CardImage src={image.url ?? ''} name={product.name} brand={product.brandName} />
}
function CardImage({ src, name, brand }: { src: string; name: string; brand: string }) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  return src && failedSrc !== src ? <img src={src} alt={`${brand} ${name}`} loading="lazy" onError={() => setFailedSrc(src)} /> : <span className="shop-catalog__image-fallback">이미지 준비 중</span>
}
