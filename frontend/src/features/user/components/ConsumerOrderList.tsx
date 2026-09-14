import { ArrowRight } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { EmptyState, StatusBadge, type StatusTone } from '../../../components/common/index.ts'
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
export function ConsumerOrderList({ orders }: { orders: readonly ConsumerRecentOrder[] }) {
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

      <div className="consumer-recent-orders">
        {orders.map((order) => (
          <OrderCard key={order.id} order={order} />
        ))}
      </div>
    </section>
  )
}

function OrderCard({ order }: { order: ConsumerRecentOrder }) {
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
