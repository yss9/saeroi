import { ArrowRight } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Button,
  ConfirmModal,
  EmptyState,
  ErrorMessage,
  Modal,
  StatusBadge,
  type StatusTone,
} from '../../../components/common/index.ts'
import { useConfirmProductOrderDelivery } from '../hooks/useConfirmProductOrderDelivery.ts'
import { useConsumerOrderThumbnail } from '../hooks/useConsumerOrderThumbnail.ts'
import { usePaymentCountdown } from '../hooks/usePaymentCountdown.ts'
import type {
  ConsumerOrderViewStatus,
  ConsumerRecentOrder,
} from '../types/consumerMyPage.types.ts'
import { formatDate } from '../utils/formatDate.ts'
import {
  bundleConsumerOrders,
  groupConsumerOrders,
  type ConsumerOrderBundle,
  type ConsumerOrderGroup,
} from '../utils/groupConsumerOrders.ts'
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
 * **큰 박스(주문) 안에 작은 박스(제품)를 두는 2단 구조다(2026-09-16 개편).** 원본 데이터의
 * 한 줄은 제품이라(주문 하나에 제품이 여럿일 수 있다) `groupConsumerOrders` 로 주문 단위로
 * 다시 묶는다. 배송 단계·운송장 같은 상세 정보는 목록에서 빼고 "배송조회" 모달로 옮겼다 —
 * 카드 하나에 다 넣으면 제품이 여럿인 주문에서 같은 정보(택배사·주소)가 제품 수만큼
 * 반복된다.
 */
export function ConsumerOrderList({ orders }: { orders: readonly ConsumerRecentOrder[] }) {
  const confirm = useConfirmProductOrderDelivery()

  const [pending, setPending] = useState<ConsumerOrderGroup | null>(null)
  const [shippingItem, setShippingItem] = useState<ConsumerRecentOrder | null>(null)
  const [journeyNoticeOpen, setJourneyNoticeOpen] = useState(false)

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
            <Link className="consumer-text-link" to="/shop">
              상품 둘러보기
            </Link>
          }
        />
      </section>
    )
  }

  // 서버가 주문을 최신순으로 보내므로 그 순서를 그대로 유지한다. 페이지 안에서 다시
  // 정렬하면 전체 주문 기준 정렬처럼 보이지만 실제로는 현재 20건 안에서만 맞게 된다.
  const bundles = bundleConsumerOrders(groupConsumerOrders(orders))

  return (
    <section className="consumer-mypage-card" aria-labelledby="orders-title">
      <div className="consumer-section-heading">
        <div>
          <p className="consumer-section-heading__eyebrow">ORDERS</p>
          <h2 id="orders-title">주문 내역</h2>
        </div>
      </div>

      {/* 실패는 목록 위에 둔다. 어느 주문에서 눌렀는지는 버튼이 제자리에 남아 안다. */}
      {confirm.isError && <ErrorMessage error={confirm.error} />}

      <div className="consumer-recent-orders">
        {bundles.map((bundle) => (
          <OrderBundleCard
            key={bundle.key}
            bundle={bundle}
            confirmingOrderId={confirm.isPending ? confirm.variables : undefined}
            onConfirmDelivery={setPending}
            onShowShipping={setShippingItem}
            onUnavailableJourney={() => setJourneyNoticeOpen(true)}
          />
        ))}
      </div>

      <ConfirmModal
        open={pending !== null}
        title="배송을 받으셨나요?"
        message={pending === null ? '' : `${pending.orderNumber} 주문을 수령 완료로 표시합니다. 되돌릴 수 없습니다.`}
        confirmLabel="수령 확인"
        loading={confirm.isPending}
        onConfirm={() => {
          if (pending === null) return
          confirm.mutate(pending.orderId, { onSettled: () => setPending(null) })
        }}
        onClose={() => setPending(null)}
      />

      <ShippingInfoModal item={shippingItem} onClose={() => setShippingItem(null)} />

      <Modal
        open={journeyNoticeOpen}
        onClose={() => setJourneyNoticeOpen(false)}
        title="PRODUCT JOURNEY"
      >
        <p className="consumer-order-item__journey-notice">
          결제 완료 후 확인 가능합니다.
        </p>
      </Modal>
    </section>
  )
}

/**
 * 큰 박스 — 결제 묶음 하나.
 *
 * 결제 버튼·마감·결제번호는 묶음에 한 번만 둔다. 배송 상태·배송조회·상세보기·수령 확인은
 * 브랜드별 주문 단위라 묶음 안의 각 주문에 따로 둔다.
 */
function OrderBundleCard({
  bundle,
  confirmingOrderId,
  onConfirmDelivery,
  onShowShipping,
  onUnavailableJourney,
}: {
  bundle: ConsumerOrderBundle
  confirmingOrderId?: number
  onConfirmDelivery: (order: ConsumerOrderGroup) => void
  onShowShipping: (item: ConsumerRecentOrder) => void
  onUnavailableJourney: () => void
}) {
  const firstOrder = bundle.orders[0]
  const paymentOrder = bundle.orders.find((order) => order.status === 'PAYMENT_PENDING')
  const paymentCountdown = usePaymentCountdown(paymentOrder?.remainingSeconds, paymentOrder?.paymentDeadline)
  const orderReference = bundle.orderGroupCode ?? firstOrder.orderNumber

  return (
    <article className="consumer-order-group" aria-label={`${orderReference} 주문`}>
      <header className="consumer-order-group__header">
        <div className="consumer-order-group__header-main">
          <time dateTime={firstOrder.orderedAt}>{formatDate(firstOrder.orderedAt, dateFormatter)}</time>
          <p className="consumer-order-group__reference">주문 번호 {orderReference}</p>
          <Link
            className="consumer-order-group__detail-link"
            to={bundle.orderGroupId != null
              ? `/mypage/order-groups/${bundle.orderGroupId}`
              : `/mypage/orders/${firstOrder.orderId}`}
          >
            주문 상세보기 <ArrowRight aria-hidden="true" />
          </Link>
        </div>
      </header>

      <div className="consumer-order-group__orders">
        {bundle.orders.map((order) => {
          const status = orderStatusMeta[order.status]
          const firstItem = order.items[0]
          return (
            <section className="consumer-order-group__brand-order" key={order.orderId} aria-label={`${firstItem.brandName} 주문`}>
              <div className="consumer-order-group__items">
                {order.items.map((item, index) => (
                  <div className="consumer-order-group__item-shell" key={item.id}>
                    {index === 0 && (
                      <header className="consumer-order-group__brand-header">
                        <div className="consumer-order-group__brand-status">
                          <p>{firstItem.brandName}</p>
                          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                        </div>
                      </header>
                    )}

                    <OrderItemCard item={item} />

                    <div className="consumer-order-group__item-actions">
                      {item.journeyUrl ? (
                        <a
                          className="ui-button ui-button--secondary ui-button--sm ui-button--full consumer-order-item__passport"
                          href={item.journeyUrl}
                        >
                          PRODUCT JOURNEY
                        </a>
                      ) : (
                        <button
                          className="ui-button ui-button--secondary ui-button--sm ui-button--full consumer-order-item__passport"
                          type="button"
                          onClick={onUnavailableJourney}
                        >
                          PRODUCT JOURNEY
                        </button>
                      )}
                      <div className="consumer-order-group__shipping-action">
                        <Button variant="secondary" size="sm" onClick={() => onShowShipping(item)}>
                          배송조회 <ArrowRight aria-hidden="true" />
                        </Button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              {order.status === 'SHIPPING' && (
                <footer className="consumer-order-group__brand-actions">
                  <Button
                    size="sm"
                    loading={confirmingOrderId === order.orderId}
                    onClick={() => onConfirmDelivery(order)}
                  >
                    배송 완료 확인
                  </Button>
                </footer>
              )}
            </section>
          )
        })}
      </div>

      {/* 주문 취소처럼 파괴적인 동작은 목록에서 빼고 주문 상세에서만 제공한다. */}
      {paymentOrder && (
        <footer className="consumer-order-group__actions">
          <div className="consumer-order-group__actions-column">
            {paymentCountdown.label && (
              <p className="consumer-order-group__deadline" role="timer">
                {paymentCountdown.label}
              </p>
            )}
            {paymentCountdown.expired ? (
              <button className="consumer-order-group__pay" type="button" disabled>
                결제하기
              </button>
            ) : (
              <Link
                className="consumer-order-group__pay"
                to={bundle.orderGroupId != null
                  ? `/order-groups/${bundle.orderGroupId}`
                  : `/orders/${paymentOrder.orderId}/checkout`}
              >
                결제하기
              </Link>
            )}
          </div>
        </footer>
      )}
    </article>
  )
}

/**
 * 작은 박스 — 주문에 속한 제품 하나.
 *
 * 좌측 상단에 배송상태·기준일을 두고 그 아래 제품 정보를 둔다. 배송 단계 전체(4단계)와
 * 운송장·배송지처럼 자세한 정보는 여기 다 늘어놓지 않는다 — "배송조회" 버튼을 눌러야
 * 뜨는 모달로 옮겼다.
 */
function OrderItemCard({ item }: { item: ConsumerRecentOrder }) {
  return (
    <div className="consumer-order-item">
      <div className="consumer-order-item__body">
        <OrderThumbnail item={item} />
        <div className="consumer-order-item__info">
          <p className="consumer-order-item__brand">{item.brandName}</p>
          <h3>{item.productName}</h3>
          <p className="consumer-order-item__price">
            {priceFormatter.format(item.itemPrice)}
            <span> · {item.itemCount}개</span>
          </p>
        </div>
      </div>

    </div>
  )
}

/**
 * 배송조회 모달.
 *
 * **배경색을 바꾸는 대신 모달로 뺐다.** 4단계 배송 현황·운송장·받는 곳(수령인·주소)을 작은
 * 박스 안에 다 펼치면, 제품이 여럿인 주문에서 같은 배송지·운송장이 제품 수만큼 반복된다.
 *
 * 받는 곳은 **주문 시점에 확정된 값**이라 배송 전이어도 항상 보여줄 수 있다(백엔드
 * `ProductOrderShippingResponse`, 2026-09-16 추가) — 결제 대기 중에도 "내가 이 주소로
 * 시켰지" 를 확인할 수 있어야 한다.
 */
function ShippingInfoModal({
  item,
  onClose,
}: {
  item: ConsumerRecentOrder | null
  onClose: () => void
}) {
  return (
    <Modal open={item !== null} onClose={onClose} title="배송조회">
      {item && (
        <div className="consumer-shipping-modal">
          <OrderProgress order={item} />
          <TrackingLine order={item} />

          <div className="consumer-shipping-modal__address">
            <p className="consumer-shipping-modal__address-title">받는 곳</p>
            <p>{item.receiverName}</p>
            <p>
              [{item.zipCode}] {item.address}
              {item.addressDetail ? ` ${item.addressDetail}` : ''}
            </p>
          </div>
        </div>
      )}
    </Modal>
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
export function OrderProgress({ order }: { order: ConsumerRecentOrder }) {
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
            <time dateTime={step.at}>{formatDate(step.at, dateFormatter)}</time>
          ) : (
            <span className="consumer-order-progress__waiting">대기</span>
          )}
        </li>
      ))}
    </ol>
  )
}

/**
 * 송장.
 *
 * **브랜드가 발송 처리하며 적은 값을 그대로 보여준다.** 이게 없으면 "배송 중" 배지만 남아,
 * 어디쯤 왔는지 알려면 브랜드에 물어보는 수밖에 없다.
 *
 * 없을 때는 줄 자체를 그리지 않는다. 송장 없이 보내는 방식(직접 전달·퀵)이 정상 경로라
 * "운송장 없음" 이라고 적으면 빠뜨린 것처럼 읽힌다.
 *
 * 택배사만 있고 번호가 없는 경우도 있다 — 서버가 번호만 있는 조합은 막지만 그 반대는
 * 허용한다.
 */
export function TrackingLine({ order }: { order: ConsumerRecentOrder }) {
  if (!order.carrier && !order.trackingNumber) return null

  return (
    <p className="consumer-order-tracking">
      <span className="consumer-order-tracking__label">운송장 :</span>
      {order.carrier && <span>{order.carrier}</span>}
      {order.trackingNumber && <code>{order.trackingNumber}</code>}
    </p>
  )
}

function OrderThumbnail({ item }: { item: ConsumerRecentOrder }) {
  const thumbnail = useConsumerOrderThumbnail(item.thumbnailUrl)

  return (
    <div className="consumer-order-item__thumbnail">
      {thumbnail.url ? (
        <img src={thumbnail.url} alt={item.thumbnailAlt} />
      ) : (
        <span aria-hidden="true" />
      )}
    </div>
  )
}
