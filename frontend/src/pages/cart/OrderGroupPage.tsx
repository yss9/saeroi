import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Header } from '../landing/components/Header.tsx'
import { Footer } from '../landing/components/Footer.tsx'
import { errorLines, type OrderGroup } from '../../features/cart/checkout.ts'
import { orderCheckout } from '../../features/cart/orderCheckout.ts'
import { orderGroupRedirect } from '../../features/cart/orderGroupRedirect.ts'
import { consumerOrderKeys } from '../../features/user/api/consumerOrderKeys.ts'
import { publicProductKeys } from '../../features/product/api/publicProductKeys.ts'
import { productKeys } from '../../features/product/api/productKeys.ts'
import { isAppError, toErrorMessage } from '../../utils/errors.ts'
import '../landing/landing.css'
import '../shop/shop.css'
import './cart.css'
import { ConfirmModal } from '../../components/common/ConfirmModal.tsx'
import { CheckCircle } from '@phosphor-icons/react'
import { formatDate } from '../../features/user/utils/formatDate.ts'
import { usePaymentCountdown } from '../../features/user/hooks/usePaymentCountdown.ts'

const money = (n: number) => new Intl.NumberFormat('ko-KR').format(n) + '원'
export function OrderGroupPage({ returning = false, single = false }: { returning?: boolean; single?: boolean }) {
  const { orderGroupId, orderId } = useParams()
  const id = Number(single ? orderId : orderGroupId)
  const payment = orderCheckout(single)
  const navigate = useNavigate()
  const [missing, setMissing] = useState(false)
  const [search] = useSearchParams()
  const token = search.get('pg_token')
  const cancelRequested = search.get('action') === 'cancel'
  const client = useQueryClient()
  const [group, setGroup] = useState<OrderGroup | null>(null)
  const [busy, setBusy] = useState(true)
  const [message, setMessage] = useState<string | null>(null)
  const [canceled, setCanceled] = useState(false)
  const [mock, setMock] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const running = useRef(false)
  const paymentCountdown = usePaymentCountdown(group?.remainingSeconds, group?.paymentDeadline)
  function refresh() {
    try { if (sessionStorage.getItem(payment.storageKey) === String(id)) sessionStorage.removeItem(payment.storageKey) } catch { /* Storage may be unavailable. */ }
    for (const key of [consumerOrderKeys.all, productKeys.all, publicProductKeys.all, ['consumer-cart']]) void client.invalidateQueries({ queryKey: key })
  }
  useEffect(() => {
    let active = true
    async function load() {
      setBusy(true); setGroup(null); setMissing(false); setMessage(null); setMock(false); setConfirm(false); setCanceled(false)
      if (!Number.isSafeInteger(id) || id <= 0) { setMessage('올바른 주문이 아닙니다.'); setBusy(false); return }
      try {
        let current = await payment.get(id)
        if (!active) return
        setGroup(current)
        if (cancelRequested && current.cancelable) setConfirm(true)
        // A token-less return keeps the order pending; cancellation is always explicit.
        if (returning && attempt === 0 && current.status === 'CREATED') {
          if (token) {
            try { current = await payment.approve(id, token); if (active) setGroup(current) }
            catch (error) {
              const latest = await payment.get(id)
              if (active) setGroup(latest)
              if (latest.status !== 'PAID') throw error
            }
          }
          refresh()
        }
        if (returning && current.status === 'PAID') refresh()
      } catch (error) { if (active) handleError(error) }
      finally { if (active) setBusy(false) }
    }
    void load()
    return () => { active = false }
    // Requests use the route ID and token. Query client is stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, token, returning, single, cancelRequested, attempt, client])
  function handleError(error: unknown) {
    const target = orderGroupRedirect(error)
    if (target) { navigate(confirm || cancelRequested ? `${target}?action=cancel` : target, { replace: true }); return }
    const paymentExpired = isAppError(error) && error.code === 'PRODUCT_ORDER_PAYMENT_EXPIRED'
    if (isAppError(error) && (error.status === 404 || error.code === 'CHECKOUT_ORDER_NOT_FOUND' || paymentExpired)) {
      setGroup(null); setMissing(true); setMock(false); setConfirm(false)
      setMessage(paymentExpired
        ? '결제 마감 시간이 지났습니다. 주문 내역에서 상태를 확인해 주세요.'
        : '주문을 확인할 수 없습니다. 결제 마감으로 정리되었을 수 있으니 주문 내역에서 상태를 확인해 주세요.')
      refresh()
    } else setMessage(toErrorMessage(error))
  }
  async function act(kind: 'pay' | 'mock' | 'cancel') {
    if (running.current || !group) return
    if (kind !== 'cancel' && paymentCountdown.expired) {
      setMessage('결제 마감 시간이 지났습니다. 주문 내역에서 상태를 확인해 주세요.')
      return
    }
    running.current = true; setBusy(true); setMessage(null)
    try {
      const latest = await payment.get(id)
      setGroup(latest)
      if (kind === 'cancel') {
        if (!latest.cancelable) { setMessage('발송된 상품이 있어 주문 전체를 취소할 수 없습니다.'); return }
        await payment.cancel(id, single ? '구매자 주문 취소' : '구매자 묶음 전체 취소'); setGroup(null); setCanceled(true); setConfirm(false); refresh()
      } else if (latest.status === 'PAID') { setMock(false); refresh() }
      else if (latest.remainingSeconds !== null && latest.remainingSeconds <= 0) {
        setMessage('결제 마감 시간이 지났습니다. 주문 내역에서 상태를 확인해 주세요.')
      }
      else if (kind === 'mock') {
        try { setGroup(await payment.approve(id, 'MOCK-PG-TOKEN')) }
        catch (error) { const recovered = await payment.get(id); setGroup(recovered); if (recovered.status !== 'PAID') throw error }
        setMock(false); refresh()
      } else {
        const ready = await payment.ready(id)
        if (!ready.live) setMock(true)
        else if (ready.redirectUrl) {
          try { sessionStorage.setItem(payment.storageKey, String(id)) } catch { /* ID is also in the return route. */ }
          window.location.assign(ready.redirectUrl)
        } else throw new Error('결제창 주소를 확인할 수 없습니다. 다시 시도해 주세요.')
      }
    } catch (error) {
      const shipped = errorLines(error).filter(line => typeof line.brandName === 'string' && typeof line.orderCode === 'string').map(line => `${line.brandName} (${line.orderCode})`).join(', ')
      if (shipped) setMessage(`${toErrorMessage(error)} 발송된 주문: ${shipped}`)
      else handleError(error)
    } finally { running.current = false; setBusy(false) }
  }
  return <div className="landing-page shop-page cart-page"><Header isLogoVisible /><main className="cart-content order-confirmation">
    <header className="order-confirmation__heading">
      <p className="order-confirmation__eyebrow">{canceled ? 'ORDER CANCELLED' : group?.status === 'PAID' ? 'ORDER COMPLETE' : 'ORDER REVIEW'}</p>
      {group?.status === 'PAID' && <CheckCircle className="order-confirmation__success-icon" size={40} weight="fill" aria-hidden="true" />}
      <h1>{canceled ? '주문이 취소되었습니다' : group?.status === 'PAID' ? '주문·결제 완료' : '주문 확인'}</h1>
      <p>{group?.status === 'PAID' ? '주문이 완료되었습니다. 상품을 준비해 보내드릴게요.' : canceled ? '주문이 취소되었습니다.' : '상품과 배송지를 확인한 뒤 결제를 진행해 주세요.'}</p>
    </header>
    {busy && <p role="status">주문·결제 상태를 확인하는 중</p>}
    {message && <div role="alert"><p>{message}</p>{!missing && <button type="button" disabled={busy} onClick={() => { setMessage(null); setBusy(true); setAttempt(n => n + 1) }}>상태 다시 확인</button>}</div>}
    {group && <>
      <dl className="order-confirmation__summary"><div><dt>주문 번호</dt><dd>{group.orderGroupCode}</dd></div><div><dt>총 결제 금액</dt><dd className="order-confirmation__total">{money(group.totalPrice)}</dd></div>
        {group.paidAt && <div><dt>결제 일시</dt><dd>{formatDate(group.paidAt, new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }))}</dd></div>}
      </dl>
      <p className="order-confirmation__notice">배송은 브랜드별로 진행됩니다.</p>
      {group.status === 'CREATED' && paymentCountdown.label && <p className="order-confirmation__notice" role="timer">{paymentCountdown.label}</p>}
      {group.status === 'CREATED' && returning && <p className="order-confirmation__interrupted" role="status">결제가 완료되지 않았습니다. 다시 결제하거나 주문을 취소할 수 있습니다.</p>}
      {group.orders[0]?.shipping && <section className="order-confirmation__shipping"><h2>배송지</h2><p>{group.orders[0].shipping.receiverName}</p><address>[{group.orders[0].shipping.zipCode}] {group.orders[0].shipping.address}{group.orders[0].shipping.addressDetail && <><br />{group.orders[0].shipping.addressDetail}</>}</address></section>}
      {group.orders.map(order => <section className="group-order" key={order.orderId}><div className="group-order__heading"><h2>{order.brandName}</h2><span className="group-order__status">{{ CREATED: '결제 대기', PAID: '상품 준비', SHIPPED: '배송 중', DELIVERED: '배송 완료', CANCELLED: '취소' }[order.status] ?? order.status}</span></div><p className="group-order__code">{order.orderCode}</p><ul>{order.items.map(item => <li key={item.itemCode}><span>{item.productName}</span><strong>{money(item.price)}</strong></li>)}</ul><p className="group-order__total">브랜드 합계 <strong>{money(order.totalPrice)}</strong></p></section>)}
      <div className="order-confirmation__actions">
      {group.status === 'CREATED' && <>
        <button className="order-confirmation__pay" type="button" disabled={busy || paymentCountdown.expired} onClick={() => void act('pay')}>결제하기</button>
        <ConfirmModal open={mock} title="시연 결제를 진행할까요?" message="실제 금액은 청구되지 않습니다. 승인하면 주문 완료 화면으로 이동합니다." confirmLabel="결제 승인 (시연)" cancelLabel="돌아가기" loading={busy} onConfirm={() => void act('mock')} onClose={() => setMock(false)} />
      </>}
      <button className="order-confirmation__cancel" type="button" disabled={busy || !group.cancelable} onClick={() => setConfirm(true)}>{single ? '주문 취소' : '주문 전체 취소'}</button>
      </div>
      {!group.cancelable && <p className="order-confirmation__notice">발송된 상품이 있어 전체 취소가 불가합니다.</p>}
      <ConfirmModal open={confirm} title={single ? '주문을 취소할까요?' : '주문을 모두 취소할까요?'} message={`${group.orders.map(order => `${order.brandName} · ${money(order.totalPrice)}`).join('\n')}\n총 취소 금액 ${money(group.totalPrice)}${single ? '' : '\n브랜드별 부분 취소 불가'}`} confirmLabel={single ? '주문 취소 확인' : '전체 취소 확인'} cancelLabel="주문 유지" destructive loading={busy} onConfirm={() => void act('cancel')} onClose={() => setConfirm(false)} />
    </>}
    <nav className="order-confirmation__links" aria-label="주문 후 이동">{group?.status === 'PAID' && <Link to="/mypage/orders">주문 내역 보기</Link>}<Link to="/cart">장바구니</Link><Link to="/shop">쇼핑 계속하기</Link></nav>
  </main><Footer /></div>
}
