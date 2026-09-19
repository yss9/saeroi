import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  Button,
  ConfirmModal,
  EmptyState,
  ErrorMessage,
  Loading,
  StatusBadge,
  type StatusTone,
} from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { OrderProgress, TrackingLine } from '../../features/user/components/ConsumerOrderList.tsx'
import { useCancelProductOrder } from '../../features/user/hooks/useCancelProductOrder.ts'
import { useConsumerOrderThumbnail } from '../../features/user/hooks/useConsumerOrderThumbnail.ts'
import { useConsumerMyPage } from '../../features/user/hooks/useConsumerMyPage.ts'
import type {
  ConsumerOrderViewStatus,
  ConsumerRecentOrder,
} from '../../features/user/types/consumerMyPage.types.ts'
import { formatDate } from '../../features/user/utils/formatDate.ts'
import { groupConsumerOrders } from '../../features/user/utils/groupConsumerOrders.ts'
import { maskAddressDetail, maskName as maskReceiverName } from '../../utils/mask.ts'

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

const timeFormatter = new Intl.DateTimeFormat('ko-KR', {
  hour: '2-digit',
  minute: '2-digit',
})

const orderStatusMeta: Record<ConsumerOrderViewStatus, { label: string; tone: StatusTone }> = {
  PAYMENT_PENDING: { label: '결제 대기', tone: 'warning' },
  PREPARING: { label: '상품 준비', tone: 'warning' },
  SHIPPING: { label: '배송 중', tone: 'info' },
  DELIVERED: { label: '배송 완료', tone: 'success' },
}

/**
 * 주문 상세보기.
 *
 * **별도 API 가 없다.** 주문 내역 목록(`useConsumerMyPage`)이 이미 이 주문의 제품·배송
 * 정보를 전부 갖고 있어서, 여기서 다시 요청하지 않고 같은 Query 캐시에서 주문 번호로
 * 찾아 쓴다 — 목록에서 "주문 상세보기" 를 눌러 들어오면 캐시가 이미 있어 즉시 뜨고,
 * 새로고침·직접 링크로 들어와도 목록과 같은 요청 한 번으로 채워진다.
 */
export function ConsumerOrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const cancel = useCancelProductOrder()
  const [cancelOpen, setCancelOpen] = useState(false)
  const profile = user ? { id: user.id, name: user.name, email: user.email } : null
  const myPage = useConsumerMyPage(profile)

  const group = useMemo(() => {
    if (!myPage.data) return null
    return groupConsumerOrders(myPage.data.recentOrders).find(
      (candidate) => String(candidate.orderId) === orderId,
    )
  }, [myPage.data, orderId])

  return (
    <ConsumerAccountLayout
      current="orders"
      profile={profile}
      onSignOut={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
      signOutPending={logout.isPending}
      signOutError={logout.error}
    >
      {myPage.isPending ? (
        <div className="consumer-mypage-status">
          <Loading label="주문 정보를 불러오는 중" variant="skeleton" rows={6} />
        </div>
      ) : myPage.isError || !myPage.data ? (
        <div className="consumer-mypage-status">
          <ErrorMessage
            error={myPage.error ?? new Error('주문 정보를 확인할 수 없습니다.')}
            onRetry={() => void myPage.refetch()}
          />
        </div>
      ) : !group ? (
        <section className="consumer-mypage-card">
          <EmptyState
            title="주문을 찾을 수 없습니다"
            description="주문 내역 목록으로 돌아가 다시 확인해 주세요."
            action={
              <Link className="consumer-text-link" to="/mypage/orders">
                주문 내역으로
              </Link>
            }
          />
        </section>
      ) : (
        <section className="consumer-mypage-card consumer-order-detail" aria-labelledby="order-detail-title">
          <div className="consumer-section-heading">
            <div>
              <p className="consumer-section-heading__eyebrow">ORDER DETAIL</p>
              <h2 id="order-detail-title">주문번호 {group.orderNumber}</h2>
              {group.orderGroupCode && <p className="consumer-order-detail__payment-code">결제번호 {group.orderGroupCode}</p>}
            </div>
            <Link className="consumer-text-link" to="/mypage/orders">
              주문 내역으로
            </Link>
          </div>

          {/* 날짜·시간을 따로 두 줄로 쪼개지 않고 한 줄로 합친다(2026-09-17)
              — "주문 : 2026. 09. 16. 오후 03:39" 형태. */}
          <p className="consumer-order-detail__ordered-at">
            주문 :{' '}
            <time dateTime={group.orderedAt}>
              {formatDate(group.orderedAt, dateFormatter) === '-'
                ? '-'
                : `${formatDate(group.orderedAt, dateFormatter)} ${formatDate(group.orderedAt, timeFormatter)}`}
            </time>
          </p>

          <div className="consumer-order-group__items">
            {group.items.map((item) => (
              <OrderDetailItemRow key={item.id} item={item} />
            ))}
          </div>

          {/* 배송지와 결제 정보를 하나의 정렬 기준으로 표시한다. */}
          <div className="consumer-order-detail__section">
            <p className="consumer-order-detail__section-title">결제 정보</p>
            <dl className="consumer-order-detail__info-grid">
              <div>
                <dt>상품 수</dt>
                <dd>{group.items.length}개</dd>
              </div>
              <div>
                <dt>총 결제금액</dt>
                <dd className="consumer-order-detail__total">
                  {priceFormatter.format(group.items[0].totalAmount)}
                </dd>
              </div>

              <div>
                <dt>받는사람</dt>
                <dd>{maskReceiverName(group.items[0]?.receiverName ?? '')}</dd>
              </div>
              <div>
                <dt>주소</dt>
                <dd>
                  [{group.items[0].zipCode}] {group.items[0].address}
                  {group.items[0].addressDetail
                    ? ` ${maskAddressDetail(group.items[0].addressDetail)}`
                    : ''}
                </dd>
              </div>
            </dl>
          </div>

          {/*
            영수증은 별도 메뉴 없이 여기서 바로 출력한다(2026-09-16 개편) — 방금 위에서 본
            결제 정보·받는 곳이 곧 영수증 내용이라, 새 화면으로 옮겨 다시 찾을 이유가 없다.
            실제 영수증 양식(사업자 정보 등)은 백엔드 연동 뒤에 정한다 — 지금은
            `window.print()` 로 브라우저 인쇄 대화상자만 연다.

          */}
          <div className="consumer-order-detail__receipt-action">
            {!group.orderGroupCode &&
              (group.status === 'PAYMENT_PENDING' || group.status === 'PREPARING') && (
                <Button
                  variant="secondary"
                  loading={cancel.isPending}
                  onClick={() => setCancelOpen(true)}
                >
                  주문 취소
                </Button>
              )}
            {group.orderGroupCode && group.status === 'PAYMENT_PENDING' && (
              <Link
                className="consumer-order-detail__cancel-link"
                to={`/orders/${group.orderId}/checkout?action=cancel`}
              >
                주문 전체 취소
              </Link>
            )}
            <Button variant="secondary" onClick={() => window.print()}>
              영수증 출력
            </Button>
          </div>

          {cancel.isError && <ErrorMessage error={cancel.error} />}
          <ConfirmModal
            open={cancelOpen}
            title="주문을 취소할까요?"
            message={`${group.orderNumber} 주문을 취소합니다.${group.status === 'PREPARING' ? ' 결제하신 금액은 환불됩니다.' : ''} 취소한 주문은 목록에서 사라지고 되돌릴 수 없습니다.`}
            confirmLabel="주문 취소"
            destructive
            loading={cancel.isPending}
            onConfirm={() =>
              cancel.mutate(group.orderId, {
                onSuccess: () => navigate('/mypage/orders', { replace: true }),
                onSettled: () => setCancelOpen(false),
              })
            }
            onClose={() => setCancelOpen(false)}
          />
        </section>
      )}
    </ConsumerAccountLayout>
  )
}

function OrderDetailItemRow({ item }: { item: ConsumerRecentOrder }) {
  const status = orderStatusMeta[item.status]
  const thumbnail = useConsumerOrderThumbnail(item.thumbnailUrl)

  return (
    <div className="consumer-order-item consumer-order-item--detail">
      <div className="consumer-order-detail__status-row">
        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
      </div>

      <div className="consumer-order-item__body">
        <div className="consumer-order-item__thumbnail">
          {thumbnail.url ? (
            <img src={thumbnail.url} alt={item.thumbnailAlt} />
          ) : (
            <span aria-hidden="true" />
          )}
        </div>
        <div className="consumer-order-item__info">
          <p className="consumer-order-item__brand">{item.brandName}</p>
          <h3>{item.productName}</h3>
          <p className="consumer-order-item__price">
            {priceFormatter.format(item.itemPrice)}
            <span> · {item.itemCount}개</span>
          </p>
          {item.journeyUrl && <a className="consumer-text-link consumer-order-detail__journey" href={item.journeyUrl}>PRODUCT PASSPORT</a>}
        </div>
      </div>

      <div className="consumer-order-detail__progress">
        <OrderProgress order={item} />
      </div>
      <TrackingLine order={item} />
    </div>
  )
}
