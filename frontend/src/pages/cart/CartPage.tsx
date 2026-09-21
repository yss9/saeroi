import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Header } from '../landing/components/Header.tsx'
import { Footer } from '../landing/components/Footer.tsx'
import { cartErrorMessage, useCart, useCartChange, type CartItem } from '../../features/cart/useCart.ts'
import { useConsumerOrderThumbnail } from '../../features/user/hooks/useConsumerOrderThumbnail.ts'
import '../landing/landing.css'
import '../shop/shop.css'
import './cart.css'
import { CartCheckout } from './CartCheckout.tsx'
import { selectedCart } from '../../features/cart/checkout.ts'

const money = (value: number) => `${new Intl.NumberFormat('ko-KR').format(value)}원`
export function CartPage() {
  const query = useCart()
  const action = useCartChange()
  const cart = query.data
  const [selection, setSelection] = useState<number[]>([])
  const [issues, setIssues] = useState<Record<number, string>>({})
  const [checkoutBusy, setCheckoutBusy] = useState(false)
  const chosen = selectedCart(cart?.items ?? [], selection)
  const locked = checkoutBusy || action.pending
  return <div className="landing-page shop-page cart-page"><Header isLogoVisible /><main className="cart-content">
    <div className="shop-catalog__heading"><p aria-hidden="true">&nbsp;</p><h1>CART</h1></div>
    {query.isPending ? <div className="shop-catalog__state" role="status">장바구니를 불러오는 중</div>
      : query.isError ? <div className="shop-catalog__state" role="alert"><h2>장바구니를 불러오지 못했습니다</h2><button type="button" onClick={() => void query.refetch()}>다시 시도</button></div>
      : !cart || cart.items.length === 0 ? <div className="shop-catalog__state" role="status"><h2>장바구니가 비어 있습니다</h2><Link to="/shop">상품 둘러보기</Link></div>
      : <>
        <div className="cart-toolbar"><p>담긴 상품 {cart.items.length}개</p><button type="button" disabled={locked} onClick={() => setSelection(cart.items.filter(item => item.purchasable).map(item => item.product.productModelId))}>구매 가능 상품 전체 선택</button><button type="button" disabled={locked} onClick={() => setSelection([])}>선택 해제</button><button type="button" disabled={locked} onClick={() => { if (window.confirm('장바구니의 모든 상품을 삭제할까요?')) action.changeCart({ kind: 'clear' }) }}>전체 비우기</button></div>
        <div className="cart-layout"><ul className="cart-items" aria-label="장바구니 상품 목록">{cart.items.map((item) => <CartRow key={item.product.productModelId} item={item} locked={checkoutBusy} selected={chosen.selected.some(row => row.product.productModelId === item.product.productModelId)} issue={issues[item.product.productModelId]} onSelect={() => setSelection(ids => ids.includes(item.product.productModelId) ? ids.filter(id => id !== item.product.productModelId) : [...ids, item.product.productModelId])} />)}</ul>
          <aside className="cart-summary" aria-label="장바구니 합계"><h2>선택 항목 합계</h2><dl><div><dt>선택한 수량</dt><dd>{chosen.quantity}개</dd></div><div><dt>상품 금액</dt><dd>{money(chosen.price)}</dd></div></dl>
            <p>선택한 구매 가능 상품의 합계입니다.</p>
            <CartCheckout items={chosen.selected} total={chosen.price} blocked={locked || query.isFetching} refresh={async () => { const result = await query.refetch(); if (result.isError) throw result.error; setSelection(ids => ids.filter(id => result.data?.items.some(item => item.product.productModelId === id && item.purchasable))) }} onIssues={setIssues} onBusy={setCheckoutBusy} /><Link to="/shop">쇼핑 계속하기</Link>
          </aside></div>
      </>}
    {action.isError && <p role="alert">{cartErrorMessage(action.error)}</p>}
  </main><Footer /></div>
}
function CartRow({ item, selected, onSelect, issue, locked }: { item: CartItem; selected: boolean; onSelect: () => void; issue?: string; locked: boolean }) {
  const { product, quantity, purchasable, priceChanged, addedPrice } = item
  const action = useCartChange()
  const max = Math.min(99, product.stockQuantity)
  const state = product.status === 'DISCONTINUED' || product.status === 'DRAFT' ? '판매 종료' : product.status === 'SOLD_OUT' || product.stockQuantity === 0 ? '품절' : '구매 불가'
  const image = product.thumbnailUrl ? <CartImage url={product.thumbnailUrl} name={product.name} /> : <span>이미지 준비 중</span>
  return <li><article className="cart-row" aria-label={`${product.name} 장바구니 상품`}>
    <div className="cart-row__image">{purchasable ? <Link to={`/products/${product.productModelId}`} aria-label={`${product.name} 상세 보기`}>{image}</Link> : image}</div>
    <div className="cart-row__copy"><label><input type="checkbox" aria-label={`${product.name} 주문 선택`} checked={selected} disabled={!purchasable || locked || action.pending} onChange={onSelect} /> 주문 선택</label><p className="cart-row__brand">{product.brandName}</p><h2>{product.name}</h2><p>{money(product.price)}</p>
      {issue && <p role="alert">{issue}</p>}
      {priceChanged && <p className="cart-row__price-change"><span>{addedPrice < product.price ? '가격 인상' : addedPrice > product.price ? '가격 인하' : '가격 변경'}</span> <del>{money(addedPrice)}</del> → <strong>{money(product.price)}</strong><small>주문 시 현재 가격 기준</small></p>}
      {!purchasable && <p className="cart-row__unavailable">{state}</p>}
      {purchasable && <p className="cart-row__stock">{product.stockQuantity}개 남음</p>}
      <div className="cart-row__controls"><div className="cart-quantity" role="group" aria-label={`${product.name} 수량`}>
        <button type="button" aria-label={`${product.name} 수량 줄이기`} disabled={!purchasable || locked || action.pending || quantity <= 1} onClick={() => action.changeCart({ kind: 'quantity', productModelId: product.productModelId, quantity: Math.min(quantity - 1, Math.max(1, max)) })}>−</button>
        <output aria-label={`${product.name} 수량 값`}>{quantity}</output>
        <button type="button" aria-label={`${product.name} 수량 늘리기`} disabled={!purchasable || locked || action.pending || quantity >= max} onClick={() => action.changeCart({ kind: 'quantity', productModelId: product.productModelId, quantity: quantity + 1 })}>+</button>
      </div><button type="button" disabled={locked || action.pending} onClick={() => action.changeCart({ kind: 'remove', productModelId: product.productModelId })}>제거</button></div>
      {action.isError && <p role="alert">{cartErrorMessage(action.error)}</p>}
    </div>
  </article></li>
}
function CartImage({ url, name }: { url: string; name: string }) {
  const image = useConsumerOrderThumbnail(url)
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  return image.url && !image.isError && image.url !== failedSrc ? <img src={image.url} alt={name} onError={() => setFailedSrc(image.url)} /> : <span>{image.isPending ? '이미지 로딩 중' : '이미지를 불러오지 못했습니다'}</span>
}
