import { useState } from 'react'
import {
  Button,
  EmptyState,
  ErrorMessage,
  Loading,
  Modal,
  Pagination,
  StatusBadge,
  type StatusTone,
} from '../../../components/common/index.ts'
import { formatWon } from '../../../utils/quantity.ts'
import { useBrandOrders } from '../hooks/useBrandOrders.ts'
import { useShipBrandOrder } from '../hooks/useShipBrandOrder.ts'
import type { BrandOrder } from '../types/brandOrder.types.ts'
import type { ProductOrderStatus } from '../types/product.types.ts'
import { BrandOrderShipForm } from './BrandOrderShipForm.tsx'
import './product.css'

const STATUS_META: Record<ProductOrderStatus, { label: string; tone: StatusTone }> = {
  CREATED: { label: '결제 대기', tone: 'warning' },
  PAID: { label: '발송 대기', tone: 'warning' },
  SHIPPED: { label: '배송 중', tone: 'info' },
  DELIVERED: { label: '배송 완료', tone: 'success' },
}

/**
 * 탭.
 *
 * **기본이 "발송 대기" 다.** 이 화면에 오는 이유가 보낼 물건을 찾는 것이라, 거르지 않으면
 * 이미 배송 완료된 주문까지 섞여 나와 할 일이 묻힌다.
 */
const TABS: readonly { label: string; status?: ProductOrderStatus }[] = [
  { label: '발송 대기', status: 'PAID' },
  { label: '배송 중', status: 'SHIPPED' },
  { label: '배송 완료', status: 'DELIVERED' },
  { label: '전체' },
]

const PAGE_SIZE = 20

/**
 * 브랜드에게 들어온 주문.
 *
 * **소비자 주문이 여기서 멈춰 있었다.** 결제까지 끝난 주문을 보낼 화면이 없어 `PAID` 에
 * 머물렀고, 그러면 소비자의 수령 확인도 열리지 않는다.
 *
 *   CREATED ─결제─▶ PAID ─여기─▶ SHIPPED ─소비자 수령 확인─▶ DELIVERED
 */
export function BrandOrderList() {
  const [status, setStatus] = useState<ProductOrderStatus | undefined>('PAID')
  const [page, setPage] = useState(0)
  const [shipping, setShipping] = useState<BrandOrder | null>(null)

  const { data, isPending, isError, error, refetch } = useBrandOrders({
    status,
    page,
    size: PAGE_SIZE,
  })
  const ship = useShipBrandOrder()

  return (
    <section className="brand-orders">
      <div className="brand-orders__tabs" role="group" aria-label="주문 상태 필터">
        {TABS.map((tab) => {
          const selected = tab.status === status
          return (
            <Button
              key={tab.label}
              size="sm"
              variant={selected ? 'primary' : 'secondary'}
              aria-pressed={selected}
              onClick={() => {
                setStatus(tab.status)
                // 탭을 바꾸면 결과가 통째로 달라진다. 3페이지에 머물면 빈 목록이 나온다.
                setPage(0)
              }}
            >
              {tab.label}
            </Button>
          )
        })}
      </div>

      {isPending ? (
        <Loading variant="skeleton" rows={3} label="주문을 불러오는 중" />
      ) : isError ? (
        <ErrorMessage error={error} onRetry={() => void refetch()} />
      ) : data.content.length === 0 ? (
        <EmptyState
          title={status === 'PAID' ? '발송할 주문이 없습니다' : '주문이 없습니다'}
          description={
            status === 'PAID'
              ? '결제가 끝난 주문이 들어오면 여기에 표시됩니다.'
              : '이 상태의 주문이 아직 없습니다.'
          }
        />
      ) : (
        <>
          <ul className="brand-orders__list">
            {data.content.map((order) => (
              <li key={order.orderId}>
                <OrderRow order={order} onShip={() => setShipping(order)} />
              </li>
            ))}
          </ul>

          <Pagination page={data} onChange={(next) => setPage(next)} />
        </>
      )}

      {/*
        발송은 되돌릴 수 없다 — 그 뒤로는 소비자도 취소할 수 없다. 그래서 목록에서 바로
        처리하지 않고 받는 사람과 개체를 한 번 더 보여주고 확인받는다.
      */}
      {shipping !== null && (
        <Modal
          open
          onClose={() => {
            ship.reset()
            setShipping(null)
          }}
          title={`발송 처리 · ${shipping.orderCode}`}
          dismissible={!ship.isPending}
        >
          <BrandOrderShipForm
            order={shipping}
            isSubmitting={ship.isPending}
            error={ship.error}
            onCancel={() => {
              ship.reset()
              setShipping(null)
            }}
            onSubmit={(request) => {
              ship.mutate(
                { orderId: shipping.orderId, request },
                { onSuccess: () => setShipping(null) },
              )
            }}
          />
        </Modal>
      )}
    </section>
  )
}

function OrderRow({ order, onShip }: { order: BrandOrder; onShip: () => void }) {
  const meta = STATUS_META[order.status]

  return (
    <article className="brand-order" aria-label={`${order.orderCode} 주문`}>
      <div className="brand-order__main">
        <div className="brand-order__head">
          <span className="brand-order__code">{order.orderCode}</span>
          <StatusBadge tone={meta.tone}>{meta.label}</StatusBadge>
        </div>

        {/* 개체 코드와 일련번호를 지우지 않는다. 같은 모델이라도 개체마다 다른 물건이다. */}
        <ul className="brand-order__items">
          {order.items.map((item) => (
            <li key={item.itemCode}>
              <strong>{item.productName}</strong>
              <code>{item.itemCode}</code>
              <span>#{item.serialNumber}</span>
            </li>
          ))}
        </ul>

        <p className="brand-order__price">{formatWon(order.totalPrice)}</p>
      </div>

      <div className="brand-order__shipping">
        {/* 받는 사람이 주문자와 다를 수 있다. 송장에 쓰는 이름은 이쪽이다. */}
        <span className="brand-order__label">받는 분</span>
        <strong>{order.shipping.receiverName}</strong>
        <span>{order.shipping.receiverPhone}</span>
        <address>
          [{order.shipping.zipCode}] {order.shipping.address}
          {order.shipping.addressDetail ? ` ${order.shipping.addressDetail}` : ''}
        </address>
        <p className="brand-order__buyer">주문자 {order.buyerName}</p>
      </div>

      <div className="brand-order__actions">
        {order.status === 'PAID' && <Button size="sm" onClick={onShip}>발송 처리</Button>}

        {/* 고객 문의가 오면 이 화면에서 송장을 읽는다. */}
        {order.shipping.trackingNumber && (
          <p className="brand-order__tracking">
            {order.shipping.carrier} {order.shipping.trackingNumber}
          </p>
        )}
        {order.status === 'SHIPPED' && (
          <p className="brand-order__hint">소비자가 수령을 확인하면 완료됩니다.</p>
        )}
      </div>
    </article>
  )
}
