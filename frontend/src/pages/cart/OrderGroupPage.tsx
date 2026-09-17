import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Header } from '../landing/components/Header.tsx'
import { Footer } from '../landing/components/Footer.tsx'
import { approveGroup, cancelGroup, errorLines, getGroup, readyGroup, type OrderGroup } from '../../features/cart/checkout.ts'
import { consumerOrderKeys } from '../../features/user/api/consumerOrderKeys.ts'
import { publicProductKeys } from '../../features/product/api/publicProductKeys.ts'
import { productKeys } from '../../features/product/api/productKeys.ts'
import { isAppError, toErrorMessage } from '../../utils/errors.ts'
import '../landing/landing.css'
import '../shop/shop.css'
import './cart.css'
import { ConfirmModal } from '../../components/common/ConfirmModal.tsx'

const money = (n: number) => new Intl.NumberFormat('ko-KR').format(n) + '원'
export function OrderGroupPage({ returning = false }: { returning?: boolean }) {
  const { orderGroupId } = useParams()
  const id = Number(orderGroupId)
  const [search] = useSearchParams()
  const token = search.get('pg_token')
  const client = useQueryClient()
  const [group, setGroup] = useState<OrderGroup | null>(null)
  const [busy, setBusy] = useState(true)
  const [message, setMessage] = useState<string | null>(null)
  const [canceled, setCanceled] = useState(false)
  const [mock, setMock] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const running = useRef(false)
  function refresh() {
    try { if (sessionStorage.getItem('payment.orderGroupId') === String(id)) sessionStorage.removeItem('payment.orderGroupId') } catch { /* Storage may be unavailable. */ }
    for (const key of [consumerOrderKeys.all, productKeys.all, publicProductKeys.all, ['consumer-cart']]) void client.invalidateQueries({ queryKey: key })
  }
  useEffect(() => {
    let active = true
    async function load() {
      if (!Number.isSafeInteger(id) || id <= 0) { setMessage('올바른 주문 묶음이 아닙니다.'); setBusy(false); return }
      try {
        let current = await getGroup(id)
        if (!active) return
        setGroup(current)
        // Read before either approve or cancel: revisiting a paid return URL is harmless.
        if (returning && attempt === 0 && current.status === 'CREATED') {
          if (token) {
            try { current = await approveGroup(id, token); if (active) setGroup(current) }
            catch (error) {
              const latest = await getGroup(id)
              if (active) setGroup(latest)
              if (latest.status !== 'PAID') throw error
            }
          } else {
            await cancelGroup(id)
            if (active) { setGroup(null); setCanceled(true) }
          }
          refresh()
        }
        if (returning && current.status === 'PAID') refresh()
      } catch (error) { if (active) setMessage(isAppError(error) && error.status === 404 ? '주문 묶음을 찾을 수 없습니다. 취소 또는 만료 여부를 주문 내역에서 확인해 주세요.' : toErrorMessage(error)) }
      finally { if (active) setBusy(false) }
    }
    void load()
    return () => { active = false }
    // Requests use the route ID and token. Query client is stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, token, returning, attempt, client])
  async function act(kind: 'pay' | 'mock' | 'cancel') {
    if (running.current || !group) return
    running.current = true; setBusy(true); setMessage(null)
    try {
      const latest = await getGroup(id)
      setGroup(latest)
      if (kind === 'cancel') {
        if (!latest.cancelable) { setMessage('발송된 주문이 있어 묶음 전체를 취소할 수 없습니다.'); return }
        await cancelGroup(id, '구매자 묶음 전체 취소'); setGroup(null); setCanceled(true); setConfirm(false); refresh()
      } else if (latest.status === 'PAID') { setMock(false); refresh() }
      else if (kind === 'mock') {
        try { setGroup(await approveGroup(id, 'MOCK-PG-TOKEN')) }
        catch (error) { const recovered = await getGroup(id); setGroup(recovered); if (recovered.status !== 'PAID') throw error }
        setMock(false); refresh()
      } else {
        const ready = await readyGroup(id)
        if (!ready.live) setMock(true)
        else if (ready.redirectUrl) {
          try { sessionStorage.setItem('payment.orderGroupId', String(id)) } catch { /* ID is also in the return route. */ }
          window.location.assign(ready.redirectUrl)
        } else throw new Error('결제창 주소를 확인할 수 없습니다. 다시 시도해 주세요.')
      }
    } catch (error) {
      const shipped = errorLines(error).filter(line => typeof line.brandName === 'string' && typeof line.orderCode === 'string').map(line => `${line.brandName} (${line.orderCode})`).join(', ')
      setMessage(shipped ? `${toErrorMessage(error)} 발송된 주문: ${shipped}` : toErrorMessage(error))
    } finally { running.current = false; setBusy(false) }
  }
  return <div className="landing-page shop-page cart-page"><Header isLogoVisible /><main className="cart-content">
    <h1>{canceled ? '결제가 취소되었습니다' : group?.status === 'PAID' ? '주문·결제 완료' : '묶음 주문'}</h1>
    {busy && <p role="status">주문·결제 상태를 확인하는 중</p>}
    {message && <div role="alert"><p>{message}</p><button type="button" disabled={busy} onClick={() => { setMessage(null); setBusy(true); setAttempt(n => n + 1) }}>상태 다시 확인</button></div>}
    {group && <>
      <p>{group.orderGroupCode} · 전체 결제 금액 {money(group.totalPrice)}</p>
      <p>한 번의 결제로 브랜드별 주문이 생성되며, 배송은 브랜드별로 진행됩니다.</p>
      {group.paidAt && <p>결제 시각: {group.paidAt.replace('T', ' ')}</p>}
      {group.orders[0]?.shipping && <section><h2>배송지</h2><p>{group.orders[0].shipping.receiverName} · [{group.orders[0].shipping.zipCode}] {group.orders[0].shipping.address} {group.orders[0].shipping.addressDetail}</p></section>}
      {group.orders.map(order => <section className="group-order" key={order.orderId}><h2>{order.brandName}</h2><p>{order.orderCode} · {money(order.totalPrice)} · {{ CREATED: '결제 대기', PAID: '상품 준비', SHIPPED: '배송 중', DELIVERED: '배송 완료', CANCELLED: '취소' }[order.status] ?? order.status}</p><ul>{order.items.map(item => <li key={item.itemCode}>{item.productName} · {money(item.price)}</li>)}</ul></section>)}
      {group.status === 'CREATED' && <p>결제 전 재고가 예약된 상태입니다. 진행하지 않으려면 묶음 전체 취소를 선택해 주세요.</p>}
      {group.status === 'CREATED' && !returning && <>
        {mock ? <><p>결제 키가 없는 시연 환경입니다. 실제 청구 없이 승인을 진행합니다.</p><button type="button" disabled={busy} onClick={() => void act('mock')}>결제 승인 (시연)</button></> : <button type="button" disabled={busy} onClick={() => void act('pay')}>결제하기</button>}
      </>}
      {group.status === 'CREATED' && returning && !busy && <p><Link to={`/order-groups/${id}`}>묶음 주문에서 결제 상태 확인·다시 준비</Link></p>}
      <button type="button" disabled={busy || !group.cancelable} onClick={() => setConfirm(true)}>묶음 전체 취소</button>
      {!group.cancelable && <p>발송된 주문이 있어 묶음 전체 취소 불가</p>}
      <ConfirmModal open={confirm} title="모든 브랜드 주문을 취소할까요?" message={`${group.orders.map(order => `${order.brandName} · ${money(order.totalPrice)}`).join(' / ')}. 전체 ${money(group.totalPrice)} · 브랜드별 부분 취소 불가`} confirmLabel="전체 취소 확인" cancelLabel="돌아가기" destructive loading={busy} onConfirm={() => void act('cancel')} onClose={() => setConfirm(false)} />
    </>}
    <p><Link to="/mypage/orders">주문 내역 보기</Link> · <Link to="/cart">장바구니</Link> · <Link to="/shop">쇼핑 계속하기</Link></p>
  </main><Footer /></div>
}
