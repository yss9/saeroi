import { useNavigate } from 'react-router-dom'
import { EmptyState } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { CartButton } from '../../features/cart/CartButton.tsx'
import { usePublicProductModels } from '../../features/product/hooks/usePublicProductModels.ts'
import { publicProductImageUrl } from '../../features/product/api/publicProductImageUrl.ts'
import '../../features/user/components/consumer-wishlist.css'

const priceFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
})

/**
 * 최근 본 상품.
 *
 * **조회 이력 저장 기능은 아직 없다** — 프론트 로컬 기록도, 서버 기록도 없다. 그래서 "최근
 * 순"을 흉내 내지 않고 판매 중인 실제 상품을 최신순으로 보여준다(2026-09-18). 예전에는
 * 존재하지 않는 상품 이름·이미지로 30개를 채운 목데이터였는데, 장바구니 담기를 눌러도
 * 실제 상품이 아니라 API 호출이 아예 없었다 — 실제 상품 목록을 쓰면 같은 CartButton으로
 * 진짜 장바구니 연동이 된다.
 */
export function ConsumerRecentlyViewedPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null
  const catalog = usePublicProductModels({ sort: 'LATEST', size: 20 })
  const items = catalog.data?.content ?? []

  return (
    <ConsumerAccountLayout
      current="recentlyViewed"
      profile={profile}
      onSignOut={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
      signOutPending={logout.isPending}
      signOutError={logout.error}
    >
      <section className="consumer-mypage-card" aria-labelledby="recently-viewed-title">
        <div className="consumer-section-heading">
          <div>
            <p className="consumer-section-heading__eyebrow">RECENTLY VIEWED</p>
            <h2 id="recently-viewed-title">최근 본 상품</h2>
          </div>
        </div>

        {catalog.isPending ? (
          <div className="shop-catalog__state" role="status">상품을 불러오는 중</div>
        ) : catalog.isError ? (
          <div className="shop-catalog__state" role="alert">
            <h3>최근 본 상품을 불러오지 못했습니다</h3>
            <button type="button" onClick={() => void catalog.refetch()}>다시 시도</button>
          </div>
        ) : items.length === 0 ? (
          <EmptyState
            title="최근 본 상품이 없습니다"
            description="상품을 둘러보면 여기에 쌓입니다."
          />
        ) : (
          <div className="consumer-recently-viewed-row">
            {items.map((product) => (
              <article className="consumer-recently-viewed-card" key={product.productModelId}>
                <div className="consumer-recently-viewed-card__thumbnail">
                  <img
                    src={publicProductImageUrl(product.thumbnailUrl)}
                    alt={product.name}
                    loading="lazy"
                    decoding="async"
                    // 이미지를 못 받으면 깨진 아이콘 대신 썸네일 칸 배경만 남긴다.
                    onError={(event) => { event.currentTarget.style.visibility = 'hidden' }}
                  />
                </div>
                <div className="consumer-recently-viewed-card__body">
                  <p className="consumer-recently-viewed-card__brand">{product.brandName}</p>
                  <h3>{product.name}</h3>
                  <div className="consumer-recently-viewed-card__price-row">
                    <p className="consumer-recently-viewed-card__price">
                      {priceFormatter.format(product.price)}
                    </p>
                    <div className="consumer-mock-cart-action">
                      <CartButton
                        productId={product.productModelId}
                        name={product.name}
                        text="장바구니 담기"
                        className="ui-button ui-button--primary ui-button--sm"
                        disabled={product.status !== 'ON_SALE' || product.stockQuantity === 0}
                        toggleOnSuccess
                      />
                    </div>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </ConsumerAccountLayout>
  )
}
