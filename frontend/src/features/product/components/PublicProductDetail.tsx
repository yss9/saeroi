import { CartButton } from '../../cart/CartButton.tsx'
import { WishlistButton } from '../../wishlist/WishlistButton.tsx'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/hooks/useAuth.ts'
import { shopPath } from '../../shop/catalog.ts'
import { isAppError } from '../../../utils/errors.ts'
import { publicProductImageUrl } from '../api/publicProductImageUrl.ts'
import { usePublicProductModel } from '../hooks/usePublicProductModel.ts'
import type { PublicProductDetail as Product } from '../types/publicProduct.types.ts'
import { PRODUCT_CATEGORY_LABEL } from '../types/product.types.ts'

export function PublicProductDetail({ productId }: { productId: number | null }) {
  const query = usePublicProductModel(productId)
  if (productId === null || (query.isError && isAppError(query.error) && query.error.status === 404)) {
    return <section className="public-product__state"><p>PRODUCT NOT FOUND</p><h1>상품을 찾을 수 없습니다</h1><p>판매가 종료되었거나 공개되지 않은 상품입니다.</p><Link to="/shop">전체 상품 보기</Link></section>
  }
  if (query.isPending) return <section className="public-product__state" role="status"><p>상품을 불러오는 중</p><div className="public-product__skeleton" aria-hidden="true" /></section>
  if (query.isError) return <section className="public-product__state" role="alert"><h1>상품을 불러오지 못했습니다</h1><p>잠시 후 다시 시도해 주세요.</p><button type="button" onClick={() => void query.refetch()}>다시 시도</button></section>
  return <ProductBody key={query.data.productModelId} product={query.data} />
}

function ProductBody({ product }: { product: Product }) {
  const { isAuthenticated, role } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [notice, setNotice] = useState('')
  const soldOut = product.stockQuantity === 0

  function requireLogin(action: () => void) {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: `${location.pathname}${location.search}${location.hash}` } } })
      return
    }
    action()
  }

  return (
    <section className="public-product" aria-labelledby="public-product-title">
      <nav className="public-product__breadcrumb" aria-label="상품 경로">
        <Link to="/shop">SHOP</Link><span>/</span><Link to={shopPath(product.category)}>{product.category}</Link>
        {product.subcategory && <><span>/</span><Link to={shopPath(product.category, product.subcategory)}>{product.subcategory}</Link></>}
      </nav>
      <div className="public-product__grid">
        <ProductGallery product={product} />
        <div className="public-product__info">
          <p className="public-product__eyebrow">NEW LIFE, NEW FORM</p>
          <p className="public-product__brand"><Link to={`/brands/${product.brandId}`}>{product.brandName}</Link></p>
          <h1 id="public-product-title">{product.name}</h1>
          <p className="public-product__price">₩ {new Intl.NumberFormat('ko-KR').format(product.price)}</p>
          <p className="public-product__category">{PRODUCT_CATEGORY_LABEL[product.category]}{product.subcategory ? ` · ${product.subcategory}` : ''}</p>
          <p className={`public-product__stock${soldOut ? ' public-product__stock--empty' : ''}`}>{soldOut ? '품절' : `남은 수량 ${product.stockQuantity}개`}</p>
          <div className="public-product__actions">
            <button className="public-product__buy" type="button" disabled={soldOut} onClick={() => requireLogin(() => setNotice(role === 'CONSUMER' ? '구매 기능 연결을 준비하고 있습니다. 현재 주문이나 결제는 진행되지 않습니다.' : '구매는 소비자 계정에서 이용할 수 있습니다.'))}>{soldOut ? '품절' : '구매하기'}</button>
            <WishlistButton productId={product.productModelId} name={product.name} text="찜하기" />
            <CartButton productId={product.productModelId} name={product.name} text="장바구니 담기" disabled={soldOut} />
          </div>
          <p className="public-product__notice" role="status">{notice}</p>
          <p className="public-product__memory">찜과 장바구니는 계정에 저장됩니다.</p>
          <section className="public-product__description" aria-labelledby="product-description-title"><h2 id="product-description-title">상품 이야기</h2><p>{product.description || '등록된 상품 설명이 없습니다.'}</p></section>
          <Link className="public-product__back" to="/shop">전체 상품 보기 →</Link>
        </div>
      </div>
    </section>
  )
}

function ProductGallery({ product }: { product: Product }) {
  const images = [...product.images].sort((a, b) => a.sortOrder - b.sortOrder)
  const slides = images.length ? images.map((image) => ({ key: String(image.productImageId), src: publicProductImageUrl(image.url), label: image.type })) : product.thumbnailUrl ? [{ key: 'thumbnail', src: publicProductImageUrl(product.thumbnailUrl), label: '대표 이미지' }] : []
  const [selected, setSelected] = useState<string | null>(null)
  const [failed, setFailed] = useState<ReadonlySet<string>>(new Set())
  const active = slides.find((slide) => slide.key === selected) ?? slides[0]
  function fail(src: string) { setFailed((current) => new Set([...current, src])) }
  return (
    <section className="public-product__gallery" aria-label="상품 이미지 갤러리">
      <div className="public-product__image">
        {active && active.src && !failed.has(active.src) ? <img src={active.src} alt={`${product.name} ${active.label}`} onError={() => fail(active.src)} /> : <p role="status">{active ? '이미지를 불러오지 못했습니다' : '이미지 준비 중'}</p>}
      </div>
      {slides.length > 1 && <div className="public-product__thumbnails" aria-label="갤러리 이미지 선택">{slides.map((slide, index) => <button type="button" key={slide.key} aria-label={`${index + 1}번째 상품 이미지`} aria-pressed={slide.key === active?.key} onClick={() => setSelected(slide.key)}>{slide.src && !failed.has(slide.src) ? <img src={slide.src} alt="" onError={() => fail(slide.src)} /> : <span>{index + 1}</span>}</button>)}</div>}
      {slides.length > 0 && <p className="public-product__image-count">{slides.findIndex((slide) => slide.key === active?.key) + 1} / {slides.length}</p>}
    </section>
  )
}
