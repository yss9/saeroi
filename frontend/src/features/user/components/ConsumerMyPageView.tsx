import {
  ArrowRight,
  House,
  MapPin,
  Package,
  Receipt,
  ShoppingBag,
  SignOut,
  User,
} from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import {
  EmptyState,
  ErrorMessage,
  Loading,
  StatusBadge,
  type StatusTone,
} from '../../../components/common/index.ts'
import type {
  ConsumerMyPageData,
  ConsumerOrderStage,
  ConsumerOrderViewStatus,
} from '../types/consumerMyPage.types.ts'
import './consumer-mypage.css'

export type ConsumerMyPageViewState =
  | { status: 'loading' }
  | { status: 'error'; error: unknown; onRetry?: () => void }
  | { status: 'ready'; data: ConsumerMyPageData }

export interface ConsumerMyPageViewProps {
  state: ConsumerMyPageViewState
  onSignOut?: () => void
}

const orderStageMeta: Record<
  ConsumerOrderStage,
  { label: string; description: string }
> = {
  PAYMENT_COMPLETED: { label: '결제 완료', description: '주문을 확인하고 있어요' },
  PREPARING: { label: '상품 준비', description: '브랜드에서 준비 중이에요' },
  SHIPPING: { label: '배송 중', description: '새로운 쓰임이 오고 있어요' },
  DELIVERED: { label: '배송 완료', description: '배송이 완료됐어요' },
}

const orderStatusMeta: Record<
  ConsumerOrderViewStatus,
  { label: string; tone: StatusTone }
> = {
  PAYMENT_COMPLETED: { label: '결제 완료', tone: 'info' },
  PREPARING: { label: '상품 준비', tone: 'warning' },
  SHIPPING: { label: '배송 중', tone: 'info' },
  DELIVERED: { label: '배송 완료', tone: 'success' },
  CANCELED: { label: '주문 취소', tone: 'neutral' },
}

const accountNavigation = [
  { label: '마이페이지 홈', href: '#overview' },
  { label: '주문 내역', href: '#recent-orders' },
  { label: '배송지 관리', href: '#default-address' },
  { label: '회원 정보', href: '#account-summary' },
]

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

function AccountHeader() {
  return (
    <header className="consumer-account-header">
      <div className="consumer-account-header__inner">
        <Link className="consumer-account-header__logo" to="/" aria-label="SAEROI 홈">
          {/* public/ 자산 경로라 vite base 가 자동으로 안 붙는다 — /test/ 에서 404 방지 */}
          <img src={`${import.meta.env.BASE_URL}img/logo.png`} alt="" />
        </Link>

        <nav className="consumer-account-header__navigation" aria-label="쇼핑 메뉴">
          <Link to="/products">쇼핑</Link>
          <Link to="/marketplace">소재 마켓</Link>
          <Link to="/#featured-brands">브랜드</Link>
          <Link to="/#about">스토리</Link>
        </nav>

        <Link className="consumer-account-header__shop-link" to="/products">
          <ShoppingBag aria-hidden="true" />
          <span>쇼핑 계속하기</span>
        </Link>
      </div>
    </header>
  )
}

function AccountFooter() {
  return (
    <footer className="consumer-account-footer">
      <div>
        {/* public/ 자산 경로라 vite base 가 자동으로 안 붙는다 — /test/ 에서 404 방지 */}
        <img src={`${import.meta.env.BASE_URL}img/logo.png`} alt="" />
        <p>쓰임을 다한 소재에 새로운 가치를 더합니다.</p>
      </div>
      <p>© 2026 SAEROI. 모든 권리 보유.</p>
    </footer>
  )
}

function PageStatus({ state }: { state: Exclude<ConsumerMyPageViewState, { status: 'ready' }> }) {
  return (
    <main className="consumer-mypage-status">
      {state.status === 'loading' ? (
        <Loading label="마이페이지 정보를 불러오는 중" variant="skeleton" rows={6} />
      ) : (
        <ErrorMessage error={state.error} onRetry={state.onRetry} />
      )}
    </main>
  )
}

function OrderOverview({ data }: { data: ConsumerMyPageData }) {
  return (
    <section id="overview" className="consumer-mypage-card consumer-order-overview" aria-labelledby="order-overview-title">
      <div className="consumer-section-heading">
        <div>
          <p className="consumer-section-heading__eyebrow">ORDER STATUS</p>
          <h2 id="order-overview-title">주문 현황</h2>
        </div>
        <a href="#recent-orders">전체 주문 보기 <ArrowRight aria-hidden="true" /></a>
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

function RecentOrders({ data }: { data: ConsumerMyPageData }) {
  return (
    <section id="recent-orders" className="consumer-mypage-card" aria-labelledby="recent-orders-title">
      <div className="consumer-section-heading">
        <div>
          <p className="consumer-section-heading__eyebrow">RECENT ORDERS</p>
          <h2 id="recent-orders-title">최근 주문</h2>
        </div>
        <span className="consumer-section-heading__note">최근 3개월</span>
      </div>

      {data.recentOrders.length === 0 ? (
        <EmptyState
          title="최근 주문이 없습니다"
          description="새로운 쓰임을 발견하고 첫 주문을 시작해 보세요."
          action={<Link className="consumer-text-link" to="/products">상품 둘러보기</Link>}
        />
      ) : (
        <div className="consumer-recent-orders">
          {data.recentOrders.map((order) => {
            const status = orderStatusMeta[order.status]
            return (
              <article className="consumer-order-item" key={order.id} aria-label={`${order.productName} 주문`}>
                <img src={order.thumbnailUrl} alt={order.thumbnailAlt} />
                <div className="consumer-order-item__content">
                  <div className="consumer-order-item__meta">
                    <time dateTime={order.orderedAt}>{dateFormatter.format(new Date(order.orderedAt))}</time>
                    <span>{order.orderNumber}</span>
                  </div>
                  <p className="consumer-order-item__brand">{order.brandName}</p>
                  <h3>{order.productName}</h3>
                  <p className="consumer-order-item__price">
                    {priceFormatter.format(order.totalAmount)}
                    {order.itemCount > 1 && ` · ${order.itemCount}개`}
                  </p>
                </div>
                <div className="consumer-order-item__status">
                  <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                  <span>상세 화면은 다음 단계에서 연결됩니다.</span>
                </div>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}

function DefaultAddress({ data }: { data: ConsumerMyPageData }) {
  const address = data.defaultAddress

  return (
    <section id="default-address" className="consumer-mypage-card consumer-address-card" aria-labelledby="default-address-title">
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
            <span>[{address.postalCode}] {address.addressLine1}</span>
            {address.addressLine2 && <span>{address.addressLine2}</span>}
            <span>{address.phone}</span>
          </address>
          <p>배송지 추가·수정 기능은 배송지 관리 단계에서 연결됩니다.</p>
        </div>
      ) : (
        <EmptyState
          title="등록된 배송지가 없습니다"
          description="배송지 관리 화면이 연결되면 첫 배송지를 등록할 수 있습니다."
        />
      )}
    </section>
  )
}

function ConsumerMyPageReady({
  data,
  onSignOut,
}: {
  data: ConsumerMyPageData
  onSignOut?: () => void
}) {
  return (
    <main className="consumer-mypage-main">
      <nav className="consumer-breadcrumb" aria-label="현재 위치">
        <Link to="/"><House aria-hidden="true" /> 홈</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">마이페이지</span>
      </nav>

      <section id="account-summary" className="consumer-mypage-hero" aria-labelledby="consumer-mypage-title">
        <div>
          <p className="consumer-mypage-hero__eyebrow">MY SAEROI</p>
          <h1 id="consumer-mypage-title">{data.profile.name}님, 반갑습니다.</h1>
          <p>나의 주문과 배송 정보를 한곳에서 편안하게 확인하세요.</p>
        </div>
        <div className="consumer-mypage-hero__account">
          <span>{data.profile.email}</span>
          {onSignOut && (
            <button type="button" onClick={onSignOut}>
              <SignOut aria-hidden="true" /> 로그아웃
            </button>
          )}
        </div>
      </section>

      <div className="consumer-mypage-layout">
        <aside className="consumer-account-sidebar">
          <div>
            <p>MY ACCOUNT</p>
            <strong>{data.profile.name}</strong>
            <span>{data.profile.email}</span>
          </div>
          <nav aria-label="마이페이지 메뉴">
            {accountNavigation.map((item, index) => (
              <a key={item.href} href={item.href} aria-current={index === 0 ? 'page' : undefined}>
                {index === 0 && <User aria-hidden="true" />}
                {index === 1 && <Receipt aria-hidden="true" />}
                {index === 2 && <MapPin aria-hidden="true" />}
                {index === 3 && <Package aria-hidden="true" />}
                <span>{item.label}</span>
              </a>
            ))}
          </nav>
          <div className="consumer-account-sidebar__note">
            <span>SAEROI CIRCULAR CHOICE</span>
            <p>당신의 선택이 소재의 다음 여정을 만듭니다.</p>
          </div>
        </aside>

        <div className="consumer-mypage-content">
          <OrderOverview data={data} />
          <RecentOrders data={data} />
          <DefaultAddress data={data} />
        </div>
      </div>
    </main>
  )
}

export function ConsumerMyPageView({ state, onSignOut }: ConsumerMyPageViewProps) {
  return (
    <div className="consumer-mypage">
      <a className="consumer-skip-link" href="#consumer-main-content">본문으로 건너뛰기</a>
      <AccountHeader />
      <div id="consumer-main-content">
        {state.status === 'ready' ? (
          <ConsumerMyPageReady data={state.data} onSignOut={onSignOut} />
        ) : (
          <PageStatus state={state} />
        )}
      </div>
      <AccountFooter />
    </div>
  )
}
