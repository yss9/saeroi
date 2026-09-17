import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, EmptyState, StatusBadge } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import type { CancellationKind } from '../../features/user/types/consumerCancellation.types.ts'
import {
  ACTION_LABELS,
  KIND_LABEL,
  MOCK_CANCELLATION_ITEMS,
  RECEIVED_LABEL,
  STATUS_META,
} from '../../features/user/utils/consumerCancellationMock.ts'
import '../../features/user/components/consumer-cancellations.css'

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
  const items = MOCK_CANCELLATION_ITEMS.filter((item) => tab === 'ALL' || item.kind === tab)

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
                    {/*
                      유형(취소/반품/교환) + 진행 상태를 한 배지로 합쳐 카드 왼쪽에 둔다
                      (2026-09-17) — "완료"만 보고는 취소가 끝난 건지 반품이 끝난 건지 알 수
                      없어, 처음부터 "취소 완료"처럼 한 문구로 적는다. 상태별 색은 그대로
                      STATUS_META의 tone을 따른다.
                    */}
                    <div className="return-card__kind-status">
                      <StatusBadge tone={status.tone}>
                        {KIND_LABEL[item.kind]} {status.label}
                      </StatusBadge>
                    </div>

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

                    <div className="return-card__actions-col">
                      <div className="return-card__actions">
                        {/* 두 버튼 모두 같은 상세 화면으로 간다 — 그 화면 안에서 유형별로
                            사유/회수 정보/환불 계좌 섹션을 나눠 보여준다(2026-09-17). */}
                        <Button
                          variant="secondary"
                          size="sm"
                          fullWidth
                          onClick={() => navigate(`/mypage/cancellations/${item.id}`)}
                        >
                          {detailLabel}
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          fullWidth
                          onClick={() => navigate(`/mypage/cancellations/${item.id}`)}
                        >
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
