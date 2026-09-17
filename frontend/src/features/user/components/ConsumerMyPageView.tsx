import { PencilSimple } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { ErrorMessage, Loading } from '../../../components/common/index.ts'
import type { ConsumerMyPageData } from '../types/consumerMyPage.types.ts'
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

/**
 * 마이페이지 홈.
 *
 * **내 프로필만 둔다.** 주문 목록도 주문 현황도 `/mypage/orders` 로 나갔다 — 주문이
 * 늘어날수록 내 정보와 배송지처럼 짧고 자주 보는 것이 목록 아래로 밀려 내려간다.
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
          <ProfileCard data={state.data} />
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
 * 내 프로필.
 *
 * **계정 정보와 기본 배송지를 한 장에 둔다.** 배송지만 덩그러니 있으면 이 화면이 무엇을
 * 보여주는 곳인지 읽히지 않는다 — 배송지를 다루는 곳은 따로 있고(`/mypage/addresses`),
 * 여기서의 질문은 "내 계정이 지금 어떤 상태인가" 다.
 *
 * 연락처와 주소는 **기본 배송지의 값**이다. 계정에 적은 전화번호와 다를 수 있어 그렇게
 * 적는다 — 물건이 실제로 가는 곳과 받을 사람이 여기 보여야 할 값이다.
 */
function ProfileCard({ data }: { data: ConsumerMyPageData }) {
  const address = data.defaultAddress

  return (
    <section className="consumer-mypage-card consumer-profile-card" aria-labelledby="profile-title">
      <div className="consumer-section-heading">
        <div>
          <p className="consumer-section-heading__eyebrow">MY PROFILE</p>
          <h2 id="profile-title">내 프로필</h2>
        </div>
        <Link className="consumer-text-link" to="/mypage/profile">
          <PencilSimple aria-hidden="true" /> 수정
        </Link>
      </div>

      <dl className="consumer-profile-card__fields">
        <div>
          <dt>이름</dt>
          <dd>{data.profile.name}</dd>
        </div>
        <div>
          <dt>이메일</dt>
          <dd>{data.profile.email}</dd>
        </div>
        <div>
          <dt>연락처</dt>
          <dd>{address ? address.phone : <span className="consumer-profile-card__empty">등록된 배송지가 없습니다</span>}</dd>
        </div>
        <div>
          <dt>기본 배송지</dt>
          <dd>
            {address ? (
              <address>
                <span>{address.recipientName}</span>
                <span>
                  [{address.postalCode}] {address.addressLine1}
                </span>
                {address.addressLine2 && <span>{address.addressLine2}</span>}
              </address>
            ) : (
              <span className="consumer-profile-card__empty">
                주문한 제품을 받을 곳을 등록해 주세요.
              </span>
            )}
          </dd>
        </div>
      </dl>
    </section>
  )
}
