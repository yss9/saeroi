import { ArrowRight } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Button,
  ConfirmModal,
  EmptyState,
  ErrorMessage,
  StatusBadge,
  type StatusTone,
} from '../../../components/common/index.ts'
import { useCancelProductOrder } from '../hooks/useCancelProductOrder.ts'
import { useConfirmProductOrderDelivery } from '../hooks/useConfirmProductOrderDelivery.ts'
import { useConsumerOrderThumbnail } from '../hooks/useConsumerOrderThumbnail.ts'
import type {
  ConsumerOrderViewStatus,
  ConsumerRecentOrder,
} from '../types/consumerMyPage.types.ts'
import './consumer-mypage.css'

const priceFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
})

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

const orderStatusMeta: Record<ConsumerOrderViewStatus, { label: string; tone: StatusTone }> = {
  PAYMENT_PENDING: { label: '결제 대기', tone: 'warning' },
  PREPARING: { label: '상품 준비', tone: 'warning' },
  SHIPPING: { label: '배송 중', tone: 'info' },
  DELIVERED: { label: '배송 완료', tone: 'success' },
}

/**
 * 주문 내역.
 *
 * **마이페이지 홈에서 떼어 낸 화면이다.** 홈에 주문을 통째로 쌓아 두면 주문이 늘어날수록
 * 배송지 같은 짧은 정보가 목록 아래로 밀려 내려간다.
 *
 * 주문마다 **배송 현황**을 함께 보여준다 — 상태 배지만 있으면 "배송 중" 이 어제부터인지
 * 방금부터인지 알 수 없어 사용자는 결국 브랜드에 묻게 된다.
 */
/** 확인을 거쳐야 하는 동작. 둘 다 되돌릴 수 없다. */
type PendingAction = { kind: 'confirm' | 'cancel'; order: ConsumerRecentOrder }

export function ConsumerOrderList({ orders }: { orders: readonly ConsumerRecentOrder[] }) {
  const confirm = useConfirmProductOrderDelivery()
  const cancel = useCancelProductOrder()

  // 모달은 한 번에 하나만 뜬다. 둘을 따로 들고 있으면 같은 자리에 두 개가 겹칠 수 있다.
  const [pending, setPending] = useState<PendingAction | null>(null)

  if (orders.length === 0) {
    return (
      <section className="consumer-mypage-card" aria-labelledby="orders-title">
        <div className="consumer-section-heading">
          <div>
            <p className="consumer-section-heading__eyebrow">ORDERS</p>
            <h2 id="orders-title">주문 내역</h2>
          </div>
        </div>

        <EmptyState
          title="주문 내역이 없습니다"
          description="새로운 쓰임을 발견하고 첫 주문을 시작해 보세요."
          action={
            <Link className="consumer-text-link" to="/products">
              상품 둘러보기
            </Link>
          }
        />
      </section>
    )
  }

  return (
    <section className="consumer-mypage-card" aria-labelledby="orders-title">
      <div className="consumer-section-heading">
        <div>
          <p className="consumer-section-heading__eyebrow">ORDERS</p>
          <h2 id="orders-title">주문 내역</h2>
        </div>
        <span className="consumer-section-heading__note">최신순</span>
      </div>

      {/* 실패는 목록 위에 둔다. 어느 줄에서 눌렀는지는 버튼이 제자리에 남아 안다. */}
      {confirm.isError && <ErrorMessage error={confirm.error} />}
      {cancel.isError && <ErrorMessage error={cancel.error} />}

      <div className="consumer-recent-orders">
        {orders.map((order) => (
          <OrderCard
            key={order.id}
            order={order}
            busy={
              (confirm.isPending && confirm.variables === order.orderId) ||
              (cancel.isPending && cancel.variables === order.orderId)
            }
            onConfirmDelivery={() => setPending({ kind: 'confirm', order })}
            onCancel={() => setPending({ kind: 'cancel', order })}
          />
        ))}
      </div>

      {/*
        둘 다 되돌릴 수 없다. 어느 주문인지 문장에 넣는다 — 주문에 제품이 여럿이면 한
        줄에서 눌러도 그 주문이 통째로 처리된다.
      */}
      <ConfirmModal
        open={pending !== null}
        title={pending?.kind === 'cancel' ? '주문을 취소할까요?' : '배송을 받으셨나요?'}
        message={pending === null ? '' : actionMessage(pending)}
        confirmLabel={pending?.kind === 'cancel' ? '주문 취소' : '수령 확인'}
        destructive={pending?.kind === 'cancel'}
        loading={confirm.isPending || cancel.isPending}
        onConfirm={() => {
          if (pending === null) return
          const done = { onSettled: () => setPending(null) }
          if (pending.kind === 'cancel') cancel.mutate(pending.order.orderId, done)
          else confirm.mutate(pending.order.orderId, done)
        }}
        onClose={() => setPending(null)}
      />
    </section>
  )
}

/**
 * 확인 문구.
 *
 * 취소는 **결과를 미리 말해야 한다** — 목록에서 사라지고(서버가 행을 지운다), 결제했다면
 * 환불이 함께 나간다. "되돌릴 수 없습니다" 만으로는 무엇이 없어지는지 알 수 없다.
 */
function actionMessage({ kind, order }: PendingAction): string {
  if (kind === 'confirm') {
    return `${order.orderNumber} 주문을 수령 완료로 표시합니다. 되돌릴 수 없습니다.`
  }
  const refund = order.status === 'PREPARING' ? ' 결제하신 금액은 환불됩니다.' : ''
  return `${order.orderNumber} 주문을 취소합니다.${refund} 취소한 주문은 목록에서 사라지고 되돌릴 수 없습니다.`
}

function OrderCard({
  order,
  busy,
  onConfirmDelivery,
  onCancel,
}: {
  order: ConsumerRecentOrder
  busy: boolean
  onConfirmDelivery: () => void
  onCancel: () => void
}) {
  const status = orderStatusMeta[order.status]

  return (
    <article className="consumer-order-item" aria-label={`${order.productName} 주문`}>
      <OrderThumbnail order={order} />

      <div className="consumer-order-item__content">
        <div className="consumer-order-item__meta">
          <time dateTime={order.orderedAt}>
            {dateFormatter.format(new Date(order.orderedAt))}
          </time>
          <span>{order.orderNumber}</span>
        </div>
        <p className="consumer-order-item__brand">{order.brandName}</p>
        <h3>{order.productName}</h3>
        <span className="consumer-order-item__code">{order.itemCode}</span>
        <p className="consumer-order-item__price">
          {priceFormatter.format(order.itemPrice)}
          {order.itemCount > 1 && (
            <small>
              주문 총 {order.itemCount}개 · {priceFormatter.format(order.totalAmount)}
            </small>
          )}
        </p>

        <OrderProgress order={order} />
      </div>

      <div className="consumer-order-item__status">
        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
        {/*
          **배송 중일 때만 나온다.** 받았다고 말하는 쪽은 구매자다 — 브랜드가 발송을
          처리해도 주문은 배송 중에서 멈추고, 여기서 눌러야 완료가 된다.
        */}
        {order.status === 'SHIPPING' && (
          <Button size="sm" loading={busy} onClick={onConfirmDelivery}>
            배송 완료 확인
          </Button>
        )}

        {/*
          **발송 전까지만 취소할 수 있다.** 물건이 나갔는데 주문만 없애면 그 개체가
          어디에도 속하지 않게 된다 — 서버도 그 뒤로는 거절한다.
        */}
        {(order.status === 'PAYMENT_PENDING' || order.status === 'PREPARING') && (
          <Button variant="secondary" size="sm" loading={busy} onClick={onCancel}>
            주문 취소
          </Button>
        )}

        {order.journeyUrl ? (
          <a className="consumer-journey-link" href={order.journeyUrl}>
            제품 여정 보기 <ArrowRight aria-hidden="true" />
          </a>
        ) : (
          <span>결제 완료 후 제품 여정 확인 가능</span>
        )}
      </div>
    </article>
  )
}

/**
 * 배송 현황.
 *
 * **지나온 단계에는 날짜를 적는다.** 상태 배지는 "지금 어디쯤" 만 말하고 "언제" 를 말하지
 * 않는다 — 배송이 오래 걸린다고 느끼는 사용자가 확인하고 싶은 것이 정확히 그 날짜다.
 *
 * 아직 오지 않은 단계도 지우지 않고 흐리게 남긴다. 남은 단계가 보이지 않으면 지금이
 * 마지막인지 알 수 없다.
 */
function OrderProgress({ order }: { order: ConsumerRecentOrder }) {
  const steps = [
    { label: '주문', at: order.orderedAt },
    { label: '결제', at: order.paidAt },
    { label: '배송 시작', at: order.shippedAt },
    { label: '배송 완료', at: order.deliveredAt },
  ]

  return (
    <ol className="consumer-order-progress" aria-label="배송 현황">
      {steps.map((step) => (
        <li key={step.label} className={step.at ? 'is-done' : undefined}>
          <span className="consumer-order-progress__label">{step.label}</span>
          {step.at ? (
            <time dateTime={step.at}>{dateFormatter.format(new Date(step.at))}</time>
          ) : (
            <span className="consumer-order-progress__waiting">대기</span>
          )}
        </li>
      ))}
    </ol>
  )
}

function OrderThumbnail({ order }: { order: ConsumerRecentOrder }) {
  const thumbnail = useConsumerOrderThumbnail(order.thumbnailUrl)

  return (
    <div className="consumer-order-item__thumbnail">
      {thumbnail.url ? (
        <img src={thumbnail.url} alt={order.thumbnailAlt} />
      ) : (
        <span aria-hidden="true" />
      )}
    </div>
  )
}
