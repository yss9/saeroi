import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useConsumerAddresses } from '../../features/user/hooks/useConsumerAddresses.ts'
import { checkoutCart, checkoutIssues } from '../../features/cart/checkout.ts'
import { isAppError, toErrorMessage } from '../../utils/errors.ts'
import type { CartItem } from '../../features/cart/useCart.ts'

export function CartCheckout({ items, total, blocked, refresh, onIssues, onBusy }: {
  items: CartItem[]; total: number; blocked: boolean; refresh: () => Promise<unknown>
  onIssues: (issues: Record<number, string>) => void; onBusy: (busy: boolean) => void
}) {
  const [open, setOpen] = useState(false)
  const [addressId, setAddressId] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [review, setReview] = useState(false)
  const running = useRef(false)
  const navigate = useNavigate()
  async function submit(id: number) {
    if (blocked || running.current || review || !items.length) return
    running.current = true; setBusy(true); onBusy(true); setMessage(null); onIssues({})
    try {
      const group = await checkoutCart(items.map(item => item.product.productModelId), id, total)
      navigate(`/order-groups/${group.orderGroupId}`)
    } catch (error) {
      const missing = isAppError(error) && error.code === 'CART_ITEM_NOT_FOUND' && Array.isArray(error.details)
        ? error.details.filter((id): id is number => typeof id === 'number').map(id => items.find(item => item.product.productModelId === id)?.product.name ?? `상품 ${id}`).join(', ') : ''
      const summary = `${toErrorMessage(error)}${missing ? ` 장바구니에서 사라진 상품: ${missing}.` : ''}`
      onIssues(checkoutIssues(error)); setMessage(summary)
      if (isAppError(error) && ['CART_ITEM_NOT_FOUND', 'CART_CHECKOUT_PRICE_CHANGED', 'CART_CHECKOUT_ITEMS_UNAVAILABLE', 'PRODUCT_ORDER_ITEMS_OUT_OF_STOCK'].includes(error.code ?? '')) {
        setReview(true)
        try {
          await refresh()
          setMessage(`${summary} 장바구니를 다시 불러왔습니다. 선택 항목·수량·새 합계를 확인해 주세요.`)
        } catch { setMessage('장바구니 재조회에 실패했습니다. 다시 불러온 뒤 금액을 확인해 주세요.') }
      }
    } finally { running.current = false; setBusy(false); onBusy(false) }
  }
  return <section className="cart-checkout">
    {!open && <button type="button" disabled={blocked || busy || !items.length} onClick={() => setOpen(true)}>주문하기</button>}
    {open && <>
      <h3>배송지 선택</h3>
      <p>배송지를 선택한 뒤 주문 내용을 확인해 주세요.</p>
      <AddressPicker selected={addressId} onSelect={setAddressId} disabled={busy || blocked} onSubmit={submit} review={review} />
      {review && <button type="button" disabled={busy || blocked} onClick={() => { setReview(false); setMessage(null); onIssues({}) }}>변경된 항목과 합계 확인</button>}
    </>}
    {message && <p role="alert">{message}</p>}
  </section>
}
function AddressPicker({ selected, onSelect, disabled, onSubmit, review }: { selected: number | null; onSelect: (id: number) => void; disabled: boolean; onSubmit: (id: number) => void; review: boolean }) {
  const addresses = useConsumerAddresses()
  const current = selected ?? addresses.data?.find(address => address.isDefault)?.addressId ?? addresses.data?.[0]?.addressId
  if (addresses.isPending) return <p role="status">배송지를 불러오는 중</p>
  if (addresses.isError) return <div role="alert"><p>배송지를 불러오지 못했습니다.</p><button type="button" onClick={() => void addresses.refetch()}>다시 시도</button></div>
  return <>
    <fieldset disabled={disabled}><legend>모든 주문의 배송지</legend>{addresses.data?.map(address => <label key={address.addressId}>
      <input type="radio" name="checkout-address" checked={address.addressId === current} onChange={() => onSelect(address.addressId)} />
      <span className="cart-checkout__address">
        <strong>{address.alias}</strong>
        <span>{address.receiverName} · {address.receiverPhone}</span>
        <span>[{address.zipCode}] {address.address}</span>
        {address.addressDetail && <span>{address.addressDetail}</span>}
      </span>
    </label>)}</fieldset>
    <Link to="/mypage/addresses">배송지 등록·관리</Link>
    <button type="button" disabled={disabled || review || !addresses.data?.some(address => address.addressId === current)} onClick={() => { if (current !== undefined) onSubmit(current) }}>선택한 배송지로 결제 진행</button>
  </>
}
