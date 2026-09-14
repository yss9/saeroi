import { ArrowRight, MapPin, PencilSimple } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading } from '../../../components/common/index.ts'
import type {
  ConsumerMyPageData,
  ConsumerOrderStage,
} from '../types/consumerMyPage.types.ts'
import { ConsumerAccountLayout } from './ConsumerAccountLayout.tsx'
import './consumer-mypage.css'

export type ConsumerMyPageViewState =
  | { status: 'loading' }
  | { status: 'error'; error: unknown; onRetry?: () => void }
  | { status: 'ready'; data: ConsumerMyPageData }

export interface ConsumerMyPageViewProps {
  state: ConsumerMyPageViewState
  onSignOut?: () => void
  signOutPending?: boolean
  signOutError?: unknown
}

const orderStageMeta: Record<ConsumerOrderStage, { label: string; description: string }> = {
  PAYMENT_PENDING: { label: '결제 대기', description: '결제를 기다리고 있어요' },
  PREPARING: { label: '상품 준비', description: '브랜드에서 준비 중이에요' },
  SHIPPING: { label: '배송 중', description: '새로운 쓰임이 오고 있어요' },
  DELIVERED: { label: '배송 완료', description: '배송이 완료됐어요' },
}

/**
 * 마이페이지 홈.
 *
 * **내 정보와 기본 배송지만 둔다.** 주문 목록은 `/mypage/orders` 로 나갔다 — 한 화면에
 * 전부 쌓아 두면 주문이 늘어날수록 배송지처럼 짧은 정보가 목록 아래로 밀려 내려간다.
 *
 * 주문 현황은 **주문이 있을 때만** 나온다. 한 건도 없는 사람에게 0 이 네 칸 늘어선 표는
 * 알려주는 것이 없고, 첫 화면의 절반을 차지한다.
 */
export function ConsumerMyPageView({
  state,
  onSignOut,
  signOutPending,
  signOutError,
}: ConsumerMyPageViewProps) {
  const profile = state.status === 'ready' ? state.data.profile : null

  return (
    <ConsumerAccountLayout
      current="home"
      profile={profile}
      onSignOut={onSignOut}
      signOutPending={signOutPending}
      signOutError={signOutError}
    >
      {state.status === 'ready' ? (
        <>
          <OrderOverview data={state.data} />
          <DefaultAddress data={state.data} />
        </>
      ) : (
        <PageStatus state={state} />
      )}
    </ConsumerAccountLayout>
  )
}

function PageStatus({ state }: { state: Exclude<ConsumerMyPageViewState, { status: 'ready' }> }) {
  return (
    <div className="consumer-mypage-status">
      {state.status === 'loading' ? (
        <Loading label="마이페이지 정보를 불러오는 중" variant="skeleton" rows={6} />
      ) : (
        <ErrorMessage error={state.error} onRetry={state.onRetry} />
      )}
    </div>
  )
}

/**
 * 주문 현황.
 *
 * **한 건도 없으면 그리지 않는다.** 0 이 네 칸 늘어선 표는 "아직 주문이 없다" 를 말하는
 * 가장 나쁜 방법이다 — 처음 온 사람은 그 표가 무엇을 세는 것인지조차 모른다.
 */
function OrderOverview({ data }: { data: ConsumerMyPageData }) {
  const total = data.orderStages.reduce((sum, stage) => sum + stage.count, 0)
  if (total === 0) return null

  return (
    <section className="consumer-mypage-card consumer-order-overview" aria-labelledby="order-overview-title">
      <div className="consumer-section-heading">
        <div>
          <p className="consumer-section-heading__eyebrow">ORDER STATUS</p>
          <h2 id="order-overview-title">주문 현황</h2>
        </div>
        <Link to="/mypage/orders">
          전체 주문 보기 <ArrowRight aria-hidden="true" />
        </Link>
      </div>

      <ol className="consumer-order-steps">
        {data.orderStages.map(({ stage, count }, index) => {
          const meta = orderStageMeta[stage]
          return (
            <li key={stage}>
              <span className="consumer-order-steps__number">{count}</span>
              <strong>{meta.label}</strong>
              <span>{meta.description}</span>
              {index < data.orderStages.length - 1 && (
                <ArrowRight className="consumer-order-steps__arrow" aria-hidden="true" />
              )}
            </li>
          )
        })}
      </ol>
    </section>
  )
}

/**
 * 기본 배송지.
 *
 * 카드는 **기본 배송지 하나만** 보여준다. 나머지를 보고 고치는 곳은 관리 화면이고,
 * "편집" 이 그리로 데려간다 — 주문할 때 실제로 쓰이는 주소가 무엇인지가 이 자리의 질문이다.
 */
function DefaultAddress({ data }: { data: ConsumerMyPageData }) {
  const address = data.defaultAddress

  return (
    <section className="consumer-mypage-card consumer-address-card" aria-labelledby="default-address-title">
      <div className="consumer-section-heading">
        <div>
          <p className="consumer-section-heading__eyebrow">DELIVERY</p>
          <h2 id="default-address-title">기본 배송지</h2>
        </div>
        <MapPin aria-hidden="true" />
      </div>

      {address ? (
        <div className="consumer-address-card__content">
          <div>
            <span className="consumer-address-card__label">기본 배송지</span>
            <strong>{address.recipientName}</strong>
          </div>
          <address>
            <span>
              [{address.postalCode}] {address.addressLine1}
            </span>
            {address.addressLine2 && <span>{address.addressLine2}</span>}
            <span>{address.phone}</span>
          </address>
          <p>
            <Link className="consumer-address-card__edit" to="/mypage/addresses">
              <PencilSimple aria-hidden="true" /> 편집
            </Link>
          </p>
        </div>
      ) : (
        <EmptyState
          title="등록된 배송지가 없습니다"
          description="주문한 제품을 받을 곳을 등록해 주세요."
          action={<Link to="/mypage/addresses">배송지 등록하기</Link>}
        />
      )}
    </section>
  )
}
