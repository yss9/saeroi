import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, EmptyState, StatusBadge, type StatusTone } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import '../../features/user/components/consumer-cancellations.css'

type CancellationKind = 'CANCEL' | 'RETURN' | 'EXCHANGE' | 'REFUND'
type CancellationStatus = 'REQUESTED' | 'PROCESSING' | 'DONE' | 'REJECTED'

interface CancellationItem {
  id: string
  kind: CancellationKind
  status: CancellationStatus
  productName: string
  orderNumber: string
  orderedAt: string
  requestedAt: string
  itemCount: number
  reason: string
  amount: number
}

const KIND_LABEL: Record<CancellationKind, string> = {
  CANCEL: '취소',
  RETURN: '반품',
  EXCHANGE: '교환',
  REFUND: '환불',
}

const STATUS_META: Record<CancellationStatus, { label: string; tone: StatusTone }> = {
  REQUESTED: { label: '접수', tone: 'warning' },
  PROCESSING: { label: '처리 중', tone: 'info' },
  DONE: { label: '완료', tone: 'success' },
  REJECTED: { label: '거절', tone: 'danger' },
}

/** 접수일 라벨 위에 붙는 문구. kind 별로 "취소/반품/교환/환불접수일"처럼 달라진다. */
const RECEIVED_LABEL: Record<CancellationKind, string> = {
  CANCEL: '취소접수일',
  RETURN: '반품접수일',
  EXCHANGE: '교환접수일',
  REFUND: '환불접수일',
}

/** kind 별 상세/조회 버튼 라벨. 실제 핸들러는 없다 — 통합 조회 API가 아직 없는 목데이터 화면. */
const ACTION_LABELS: Record<CancellationKind, readonly [string, string]> = {
  CANCEL: ['취소상세', '취소사유확인'],
  RETURN: ['반품상세', '회수조회'],
  EXCHANGE: ['교환상세', '회수조회'],
  REFUND: ['환불상세', '환불계좌확인'],
}

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

/** 화면 확인용 목데이터. 백엔드가 아직 없다 — 취소/반품/교환/환불 통합 조회 API는 미착수. */
const MOCK_ITEMS: readonly CancellationItem[] = [
  {
    id: '1',
    kind: 'CANCEL',
    status: 'DONE',
    productName: '업사이클 데님 토트백',
    orderNumber: 'ORD-00000012',
    orderedAt: '2026-09-07T10:00:00',
    requestedAt: '2026-09-10T11:20:00',
    itemCount: 1,
    reason: '단순 변심',
    amount: 89000,
  },
  {
    id: '2',
    kind: 'RETURN',
    status: 'PROCESSING',
    productName: '재생 울 니트',
    orderNumber: 'ORD-00000015',
    orderedAt: '2026-09-11T09:00:00',
    requestedAt: '2026-09-13T09:05:00',
    itemCount: 1,
    reason: '사이즈가 맞지 않음',
    amount: 132000,
  },
  {
    id: '3',
    kind: 'EXCHANGE',
    status: 'REQUESTED',
    productName: '패치워크 파우치',
    orderNumber: 'ORD-00000017',
    orderedAt: '2026-09-14T18:30:00',
    requestedAt: '2026-09-15T16:40:00',
    itemCount: 2,
    reason: '색상 교환 요청',
    amount: 39000,
  },
  {
    id: '4',
    kind: 'REFUND',
    status: 'REJECTED',
    productName: '스컬그레이몬',
    orderNumber: 'ORD-00000009',
    orderedAt: '2026-09-05T13:00:00',
    requestedAt: '2026-09-08T14:00:00',
    itemCount: 1,
    reason: '단순 변심(교환·반품 기간 경과)',
    amount: 5000,
  },
]

const TABS: readonly { key: 'ALL' | CancellationKind; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'CANCEL', label: '취소' },
  { key: 'RETURN', label: '반품' },
  { key: 'EXCHANGE', label: '교환' },
  { key: 'REFUND', label: '환불' },
]

/**
 * 취소/반품/교환/환불내역.
 *
 * **프론트엔드 UI만 먼저 만든 화면이다(2026-09-16).** 통합 조회 API가 아직 없어 목데이터로
 * 채운다 — 실제 데이터 연동은 백엔드에 취소/반품/교환/환불 통합 API가 생긴 뒤 진행한다.
 * 지금 실제로 동작하는 것은 주문 취소뿐이고(주문 내역 화면), 그 결과가 여기 함께 보이는
 * 통합 화면은 아직 없다.
 */
export function ConsumerCancellationsPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null

  const [tab, setTab] = useState<'ALL' | CancellationKind>('ALL')
  const items = MOCK_ITEMS.filter((item) => tab === 'ALL' || item.kind === tab)

  return (
    <ConsumerAccountLayout
      current="cancellations"
      profile={profile}
      onSignOut={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
      signOutPending={logout.isPending}
      signOutError={logout.error}
    >
      <section className="consumer-mypage-card" aria-labelledby="cancellations-title">
        <div className="consumer-section-heading">
          <div>
            <p className="consumer-section-heading__eyebrow">CANCELLATIONS</p>
            <h2 id="cancellations-title">취소/반품/교환/환불내역</h2>
          </div>
        </div>

        <div className="consumer-tabs" role="tablist" aria-label="처리 유형">
          {TABS.map((item) => (
            <button
              key={item.key}
              type="button"
              role="tab"
              aria-selected={tab === item.key}
              className={`consumer-tabs__tab${tab === item.key ? ' is-active' : ''}`}
              onClick={() => setTab(item.key)}
            >
              {item.label}
            </button>
          ))}
        </div>

        <div className="return-toolbar">
          <p className="return-toolbar__note">
            - 취소/반품/교환 신청한 내역을 확인할 수 있습니다.
            <br />
            - 하단 목록에 없는 상품은 1:1문의 또는 고객센터로 문의주세요.
          </p>
          <a className="return-toolbar__link" href="#">
            안내 &gt;
          </a>
        </div>

        {items.length === 0 ? (
          <EmptyState
            title="해당 유형의 내역이 없습니다"
            description="다른 탭을 확인해 보세요."
          />
        ) : (
          <div className="return-list">
            {items.map((item) => {
              const status = STATUS_META[item.status]
              const [detailLabel, trackingLabel] = ACTION_LABELS[item.kind]
              return (
                <article className="return-card" key={item.id}>
                  <header className="return-card__header">
                    {RECEIVED_LABEL[item.kind]} :{' '}
                    <strong>
                      <time dateTime={item.requestedAt}>
                        {dateFormatter.format(new Date(item.requestedAt))}
                      </time>
                    </strong>
                    <span className="return-card__divider" aria-hidden="true">
                      |
                    </span>
                    주문일 :{' '}
                    <strong>
                      <time dateTime={item.orderedAt}>
                        {dateFormatter.format(new Date(item.orderedAt))}
                      </time>
                    </strong>
                    <span className="return-card__divider" aria-hidden="true">
                      |
                    </span>
                    주문번호 : <strong>{item.orderNumber}</strong>
                  </header>

                  <div className="return-card__body">
                    <div className="return-card__thumbnail">
                      <span aria-hidden="true" />
                    </div>

                    <div className="return-card__product">
                      <p className="return-card__title">{item.productName}</p>
                      <p className="return-card__subtitle">{item.reason}</p>
                    </div>

                    <div className="return-card__qty-price">
                      <p className="return-card__qty">{item.itemCount}개</p>
                      <p className="return-card__price">{priceFormatter.format(item.amount)}</p>
                    </div>

                    <div className="return-card__status-actions">
                      <StatusBadge tone="neutral">{KIND_LABEL[item.kind]}</StatusBadge>
                      <p className="return-card__status">{status.label}</p>
                      <div className="return-card__actions">
                        <Button variant="secondary" size="sm" fullWidth>
                          {detailLabel}
                        </Button>
                        <Button variant="secondary" size="sm" fullWidth>
                          {trackingLabel}
                        </Button>
                      </div>
                    </div>
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </section>
    </ConsumerAccountLayout>
  )
}
