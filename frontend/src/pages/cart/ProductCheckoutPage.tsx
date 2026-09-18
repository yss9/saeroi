import { useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { usePublicProductModel } from '../../features/product/hooks/usePublicProductModel.ts'
import { useConsumerAddresses } from '../../features/user/hooks/useConsumerAddresses.ts'
import { createProductOrder } from '../../features/user/api/createProductOrder.ts'
import { consumerOrderKeys } from '../../features/user/api/consumerOrderKeys.ts'
import { publicProductKeys } from '../../features/product/api/publicProductKeys.ts'
import { toErrorMessage } from '../../utils/errors.ts'
import { Header } from '../landing/components/Header.tsx'
import { Footer } from '../landing/components/Footer.tsx'
import '../landing/landing.css'
import '../shop/shop.css'
import './cart.css'

export function ProductCheckoutPage() {
  const { productId } = useParams()
  const id = Number(productId)
  const valid = Number.isSafeInteger(id) && id > 0
  const product = usePublicProductModel(valid ? id : null)
  const addresses = useConsumerAddresses()
  const [selected, setSelected] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const running = useRef(false)
  const navigate = useNavigate()
  const client = useQueryClient()
  const current = selected ?? addresses.data?.find(address => address.isDefault)?.addressId ?? addresses.data?.[0]?.addressId
  const canOrder = valid && product.data?.status === 'ON_SALE' && product.data.stockQuantity > 0 && addresses.data?.some(address => address.addressId === current)
  async function submit() {
    if (running.current || !canOrder || current === undefined) return
    running.current = true; setBusy(true); setMessage(null)
    try {
      const order = await createProductOrder({ productModelId: id, addressId: current })
      void client.invalidateQueries({ queryKey: consumerOrderKeys.all })
      void client.invalidateQueries({ queryKey: publicProductKeys.all })
      if (!Number.isSafeInteger(order.orderId) || order.orderId <= 0) throw new Error('주문은 생성되었지만 번호를 확인할 수 없습니다. 주문 내역에서 확인해 주세요.')
      navigate(`/orders/${order.orderId}/checkout`)
    } catch (error) { setMessage(toErrorMessage(error)) }
    finally { running.current = false; setBusy(false) }
  }
  return <div className="landing-page shop-page cart-page"><Header isLogoVisible /><main className="cart-content order-confirmation">
    <header className="order-confirmation__heading"><p className="order-confirmation__eyebrow">ORDER REVIEW</p><h1>주문·배송지 확인</h1><p>상품과 배송지를 확인한 뒤 주문을 진행해 주세요.</p></header>
    {!valid ? <p role="alert">올바른 상품이 아닙니다.</p> : product.isPending || addresses.isPending ? <p role="status">주문 정보를 불러오는 중</p> : <>
      {product.isError && <div role="alert"><p>상품을 불러오지 못했습니다.</p><button onClick={() => void product.refetch()}>다시 시도</button></div>}
      {addresses.isError && <div role="alert"><p>배송지를 불러오지 못했습니다.</p><button onClick={() => void addresses.refetch()}>다시 시도</button></div>}
      {product.data && <dl className="order-confirmation__summary"><div><dt>상품</dt><dd>{product.data.name} · 1개</dd></div><div><dt>상품 금액</dt><dd className="order-confirmation__total">{new Intl.NumberFormat('ko-KR').format(product.data.price)}원</dd></div></dl>}
      <section className="cart-checkout"><h2>배송지 선택</h2><fieldset disabled={busy}><legend>받을 곳</legend>{addresses.data?.map(address => <label key={address.addressId}><input type="radio" name="product-address" checked={current === address.addressId} onChange={() => setSelected(address.addressId)} /><span className="cart-checkout__address"><strong>{address.alias}</strong><span>{address.receiverName} · {address.receiverPhone}</span><span>[{address.zipCode}] {address.address}</span><span>{address.addressDetail}</span></span></label>)}</fieldset>
        {addresses.data?.length === 0 && <p>등록된 배송지가 없습니다.</p>}<Link to="/mypage/addresses">배송지 등록·관리</Link></section>
      {product.data && (product.data.status !== 'ON_SALE' || product.data.stockQuantity === 0) && <p>현재 구매할 수 없는 상품입니다.</p>}
      <p className="order-confirmation__notice">주문 확인 후 결제를 진행합니다. 아직 결제되지 않습니다.</p>
      <div className="order-confirmation__actions"><button className="order-confirmation__pay" disabled={busy || !canOrder} onClick={() => void submit()}>{busy ? '주문 확인 중' : '주문 확인'}</button></div>
    </>}
    {message && <p role="alert">{message}</p>}
  </main><Footer /></div>
}
