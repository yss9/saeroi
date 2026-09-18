import { Link, useNavigate, useParams } from 'react-router-dom'
import { EmptyState, StatusBadge } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import type { CancellationItem, CancellationKind } from '../../features/user/types/consumerCancellation.types.ts'
import {
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

/** kind별 두 번째 버튼(취소사유확인/회수조회/환불계좌확인)이 보여주는 칸의 제목. */
const SECOND_SECTION_TITLE: Record<CancellationKind, string> = {
  CANCEL: '취소 사유',
  RETURN: '회수 정보',
  EXCHANGE: '회수 정보',
  REFUND: '환불 계좌',
}

const PICKUP_STATUS_LABEL: Record<CancellationItem['status'], string> = {
  REQUESTED: '회수 예정',
  PROCESSING: '회수 중',
  DONE: '회수 완료',
  REJECTED: '회수 안 함',
}

/** 회수 접수일 며칠 뒤로 환불 예정일을 잡는다 — 실제 결제망 연동 전까지의 목업 값. */
function mockRefundDate(requestedAt: string): string {
  const date = new Date(requestedAt)
  date.setDate(date.getDate() + 3)
  return dateFormatter.format(date)
}

/**
 * 취소/반품/교환/환불 상세.
 *
 * **목록의 두 버튼(상세/사유확인·회수조회·환불계좌확인)이 모두 여기로 온다(2026-09-17).**
 * 유형마다 확인하고 싶은 두 번째 정보가 달라(취소는 사유, 반품·교환은 회수 현황, 환불은
 * 계좌) 화면을 따로 쪼개는 대신, 한 상세 화면 안에서 유형에 맞는 칸 하나를 보여준다 —
 * 통합 조회 API가 아직 없는 목데이터 화면이라 실제 회수 현황·계좌 정보는 없다.
 */
export function ConsumerCancellationDetailPage() {
  const navigate = useNavigate()
  const { id } = useParams()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null

  const item = MOCK_CANCELLATION_ITEMS.find((candidate) => candidate.id === id)

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
      <section className="consumer-mypage-card" aria-labelledby="cancellation-detail-title">
        <div className="consumer-section-heading">
          <div className="return-detail__heading">
            <p className="consumer-section-heading__eyebrow">
              {item ? KIND_LABEL[item.kind].toUpperCase() : 'DETAIL'}
            </p>
            <h2 id="cancellation-detail-title">
              {item ? `${KIND_LABEL[item.kind]} 상세` : '내역 상세'}
            </h2>
          </div>
          <Link className="consumer-text-link" to="/mypage/cancellations">
            목록으로
          </Link>
        </div>

        {!item ? (
          <EmptyState
            title="해당 내역을 찾을 수 없습니다"
            description="목록으로 돌아가 다시 확인해 주세요."
            action={
              <Link className="consumer-text-link" to="/mypage/cancellations">
                목록으로
              </Link>
            }
          />
        ) : (
          <>
            <header className="return-card__header return-detail__dates">
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
                <time dateTime={item.orderedAt}>{dateFormatter.format(new Date(item.orderedAt))}</time>
              </strong>
              <span className="return-card__divider" aria-hidden="true">
                |
              </span>
              주문번호 : <strong>{item.orderNumber}</strong>
            </header>

            <div className="return-detail__badge">
              <StatusBadge tone={STATUS_META[item.status].tone}>
                {KIND_LABEL[item.kind]} {STATUS_META[item.status].label}
              </StatusBadge>
            </div>

            <dl className="consumer-profile-edit__field-list">
              <div className="consumer-profile-edit__field-row">
                <dt>상품명</dt>
                <dd>
                  <span>{item.productName}</span>
                </dd>
              </div>

              <div className="consumer-profile-edit__field-row">
                <dt>수량 / 금액</dt>
                <dd>
                  <span>
                    {item.itemCount}개 · {priceFormatter.format(item.amount)}
                  </span>
                </dd>
              </div>

              <div className="consumer-profile-edit__field-row">
                <dt>{SECOND_SECTION_TITLE[item.kind]}</dt>
                {item.kind === 'CANCEL' ? (
                  <dd>
                    <span>{item.reason}</span>
                  </dd>
                ) : item.kind === 'REFUND' ? (
                  <dd className="consumer-profile-edit__field-edit">
                    <span>새로이은행 110-456-****12</span>
                    <span>환불 예정일 {mockRefundDate(item.requestedAt)}</span>
                  </dd>
                ) : (
                  <dd className="consumer-profile-edit__field-edit">
                    <span>CJ대한통운 · RC-{item.orderNumber.replace('ORD-', '')}</span>
                    <span>{PICKUP_STATUS_LABEL[item.status]}</span>
                  </dd>
                )}
              </div>
            </dl>
          </>
        )}
      </section>
    </ConsumerAccountLayout>
  )
}
