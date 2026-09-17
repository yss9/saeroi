import { useMemo } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  Button,
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
import { useConsumerOrderThumbnail } from '../../features/user/hooks/useConsumerOrderThumbnail.ts'
import { useConsumerMyPage } from '../../features/user/hooks/useConsumerMyPage.ts'
import type {
  ConsumerOrderViewStatus,
  ConsumerRecentOrder,
} from '../../features/user/types/consumerMyPage.types.ts'
import { groupConsumerOrders } from '../../features/user/utils/groupConsumerOrders.ts'

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

/** 받는사람 이름 마스킹 — 개인정보 보호. 첫/끝 글자만 남기고 가운데는 `*`. */
function maskReceiverName(name: string): string {
  if (name.length <= 1) return name
  if (name.length === 2) return `${name[0]}*`
  return `${name[0]}${'*'.repeat(name.length - 2)}${name[name.length - 1]}`
}

/** 상세주소 마스킹 — 길이는 유지하되 전부 `*`로 치환해 존재 여부만 알 수 있게 한다. */
function maskAddressDetail(addressDetail: string): string {
  return '*'.repeat(addressDetail.length)
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
        <section className="consumer-mypage-card" aria-labelledby="order-detail-title">
          <div className="consumer-section-heading">
            <div>
              <p className="consumer-section-heading__eyebrow">ORDER DETAIL</p>
              <h2 id="order-detail-title">{group.orderNumber}</h2>
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
              {dateFormatter.format(new Date(group.orderedAt))}{' '}
              {timeFormatter.format(new Date(group.orderedAt))}
            </time>
          </p>

          {group.orderGroupCode && <p>묶음 번호 {group.orderGroupCode} · 같은 번호의 브랜드별 주문은 한 번에 결제되었습니다. 개별 결제·취소 불가</p>}

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
                <dd>{maskReceiverName(group.items[0].receiverName)}</dd>
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

            제품 여정 확인도 같은 줄·같은 크기로 둔다(2026-09-17) — 둘 다 이 주문에 대한
            "더 볼 것" 액션이라 한 자리에 모으는 편이 낫다.
          */}
          <div className="consumer-order-detail__receipt-action">
            {group.items
              .filter((item) => item.journeyUrl)
              .map((item) => (
                <a
                  key={item.id}
                  className="ui-button ui-button--secondary ui-button--md"
                  href={item.journeyUrl as string}
                >
                  제품 여정 확인
                </a>
              ))}
            <Button variant="secondary" onClick={() => window.print()}>
              영수증 출력
            </Button>
          </div>
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
        </div>
      </div>

      <div className="consumer-order-detail__progress">
        <OrderProgress order={item} />
      </div>
      <TrackingLine order={item} />
    </div>
  )
}
