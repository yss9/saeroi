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
import { useCancelProductOrder } from '../hooks/useCancelProductOrder.ts'
import { useConfirmProductOrderDelivery } from '../hooks/useConfirmProductOrderDelivery.ts'
import { useConsumerOrderThumbnail } from '../hooks/useConsumerOrderThumbnail.ts'
import type {
  ConsumerOrderViewStatus,
  ConsumerRecentOrder,
} from '../types/consumerMyPage.types.ts'
import { formatDate } from '../utils/formatDate.ts'
import { groupConsumerOrders, type ConsumerOrderGroup } from '../utils/groupConsumerOrders.ts'
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

type OrderSort = 'NEWEST' | 'OLDEST' | 'PRICE_HIGH' | 'PRICE_LOW'

const SORT_OPTIONS: readonly { value: OrderSort; label: string }[] = [
  { value: 'NEWEST', label: '최신순' },
  { value: 'OLDEST', label: '오래된순' },
  { value: 'PRICE_HIGH', label: '가격 높은순' },
  { value: 'PRICE_LOW', label: '가격 낮은순' },
]

/** 주문 총액은 주문 안 어느 제품이든 같은 값이다(order 단위 값이라 items[0] 으로 충분). */
function sortGroups(groups: readonly ConsumerOrderGroup[], sort: OrderSort): ConsumerOrderGroup[] {
  const sorted = [...groups]
  switch (sort) {
    case 'NEWEST':
      sorted.sort((a, b) => Date.parse(b.orderedAt) - Date.parse(a.orderedAt))
      break
    case 'OLDEST':
      sorted.sort((a, b) => Date.parse(a.orderedAt) - Date.parse(b.orderedAt))
      break
    case 'PRICE_HIGH':
      sorted.sort((a, b) => b.items[0].totalAmount - a.items[0].totalAmount)
      break
    case 'PRICE_LOW':
      sorted.sort((a, b) => a.items[0].totalAmount - b.items[0].totalAmount)
      break
  }
  return sorted
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
type PendingAction = { kind: 'confirm' | 'cancel'; order: ConsumerOrderGroup }

export function ConsumerOrderList({ orders }: { orders: readonly ConsumerRecentOrder[] }) {
  const confirm = useConfirmProductOrderDelivery()
  const cancel = useCancelProductOrder()

  // 모달은 한 번에 하나만 뜬다. 셋을 따로 들고 있으면 같은 자리에 여럿이 겹칠 수 있다.
  const [pending, setPending] = useState<PendingAction | null>(null)
  const [shippingItem, setShippingItem] = useState<ConsumerRecentOrder | null>(null)
  const [sort, setSort] = useState<OrderSort>('NEWEST')

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

  const groups = sortGroups(groupConsumerOrders(orders), sort)

  return (
    <section className="consumer-mypage-card" aria-labelledby="orders-title">
      <div className="consumer-section-heading">
        <div>
          <p className="consumer-section-heading__eyebrow">ORDERS</p>
          <h2 id="orders-title">주문 내역</h2>
        </div>
        <select
          className="consumer-sort-select"
          aria-label="정렬 기준"
          value={sort}
          onChange={(event) => {
            setSort(event.target.value as OrderSort)
            event.currentTarget.blur()
          }}
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      {/* 실패는 목록 위에 둔다. 어느 주문에서 눌렀는지는 버튼이 제자리에 남아 안다. */}
      {confirm.isError && <ErrorMessage error={confirm.error} />}
      {cancel.isError && <ErrorMessage error={cancel.error} />}

      <div className="consumer-recent-orders">
        {groups.map((group) => (
          <OrderGroupCard
            key={group.orderId}
            group={group}
            busy={
              (confirm.isPending && confirm.variables === group.orderId) ||
              (cancel.isPending && cancel.variables === group.orderId)
            }
            onConfirmDelivery={() => setPending({ kind: 'confirm', order: group })}
            onCancel={() => setPending({ kind: 'cancel', order: group })}
            onShowShipping={setShippingItem}
          />
        ))}
      </div>

      {/*
        둘 다 되돌릴 수 없다. 어느 주문인지 문장에 넣는다 — 주문에 제품이 여럿이면 큰 박스
        어디를 눌러도 그 주문이 통째로 처리된다.
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
          if (pending.kind === 'cancel') {
            if (!pending.order.orderGroupCode) cancel.mutate(pending.order.orderId, done)
          } else confirm.mutate(pending.order.orderId, done)
        }}
        onClose={() => setPending(null)}
      />

      <ShippingInfoModal item={shippingItem} onClose={() => setShippingItem(null)} />
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

/**
 * 큰 박스 — 주문 하나.
 *
 * 좌측 상단 주문 날짜 · 우측 상단 "주문 상세보기"(별도 페이지로 이동). 배송 완료 확인·주문
 * 취소는 제품이 아니라 **주문 단위 동작**이라 제품 카드 안이 아니라 여기(주문 전체를 감싸는
 * 큰 박스)에 한 번만 둔다 — 제품이 여럿이면 버튼도 여럿이 되어 몇 번을 눌러야 하는지
 * 헷갈린다.
 */
function OrderGroupCard({
  group,
  busy,
  onConfirmDelivery,
  onCancel,
  onShowShipping,
}: {
  group: ConsumerOrderGroup
  busy: boolean
  onConfirmDelivery: () => void
  onCancel: () => void
  onShowShipping: (item: ConsumerRecentOrder) => void
}) {
  const status = orderStatusMeta[group.status]

  return (
    <article className="consumer-order-group" aria-label={`${group.orderNumber} 주문`}>
      <header className="consumer-order-group__header">
        <div className="consumer-order-group__header-main">
          <time dateTime={group.orderedAt}>{formatDate(group.orderedAt, dateFormatter)}</time>
          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
        </div>
        <Link className="consumer-order-group__detail-link" to={`/mypage/orders/${group.orderId}`}>
          주문 상세보기 <ArrowRight aria-hidden="true" />
        </Link>
      </header>

      {group.orderGroupCode && <p className="consumer-order-group__reference">주문 번호 {group.orderGroupCode}</p>}

      <div className="consumer-order-group__items">
        {group.items.map((item) => (
          <OrderItemCard key={item.id} item={item} onShowShipping={() => onShowShipping(item)} />
        ))}
      </div>

      {/*
        **배송 중일 때만 나온다.** 받았다고 말하는 쪽은 구매자다 — 브랜드가 발송을
        처리해도 주문은 배송 중에서 멈추고, 여기서 눌러야 완료가 된다.

        **발송 전까지만 취소할 수 있다.** 물건이 나갔는데 주문만 없애면 그 개체가 어디에도
        속하지 않게 된다 — 서버도 그 뒤로는 거절한다.
      */}
      {(group.status === 'SHIPPING' ||
        group.status === 'PAYMENT_PENDING' ||
        group.status === 'PREPARING') && (
        <footer className="consumer-order-group__actions">
          <div className="consumer-order-group__actions-column">
            {group.status === 'SHIPPING' && (
              <Button size="sm" fullWidth loading={busy} onClick={onConfirmDelivery}>
                배송 완료 확인
              </Button>
            )}
            {!group.orderGroupCode && (group.status === 'PAYMENT_PENDING' || group.status === 'PREPARING') && (
              <Button variant="secondary" size="sm" fullWidth loading={busy} onClick={onCancel}>
                주문 취소
              </Button>
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
function OrderItemCard({
  item,
  onShowShipping,
}: {
  item: ConsumerRecentOrder
  onShowShipping: () => void
}) {
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

        <div className="consumer-order-item__actions">
          <Button variant="secondary" size="sm" fullWidth onClick={onShowShipping}>
            배송조회 <ArrowRight aria-hidden="true" />
          </Button>
          {item.journeyUrl ? (
            // 버튼처럼 보여야 "배송조회" 와 나란히 1열로 정렬된다 — 실제로는 링크라 새
            // 탭 열기·우클릭 복사 같은 링크 동작은 그대로 유지된다.
            <a className="ui-button ui-button--secondary ui-button--sm ui-button--full" href={item.journeyUrl}>
              제품 여정 확인 <ArrowRight aria-hidden="true" />
            </a>
          ) : (
            <span className="consumer-order-item__journey-disabled">결제 완료 후 확인 가능</span>
          )}
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
