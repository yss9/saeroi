import { useNavigate } from 'react-router-dom'
import { EmptyState } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { CatalogCard } from '../../features/shop/CatalogCard.tsx'
import { usePublicProductModels } from '../../features/product/hooks/usePublicProductModels.ts'
import '../shop/shop.css'
import '../../features/wishlist/wishlist.css'

/**
 * 최근 본 상품.
 *
 * **조회 이력 저장 기능은 아직 없다** — 프론트 로컬 기록도, 서버 기록도 없다. 그래서 "최근
 * 순"을 흉내 내지 않고 판매 중인 실제 상품을 최신순으로 보여준다(2026-09-18). 예전에는
 * 상품 카드는 찜 목록과 공유하며, 별도의 장바구니 버튼 없이 상세 화면으로 연결한다.
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
      <section className="consumer-mypage-card consumer-server-wishlist" aria-labelledby="recently-viewed-title">
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
          <ul className="shop-catalog__grid" aria-label="최근 본 상품 목록">
            {items.map((product) => (
              <li key={product.productModelId}><CatalogCard product={product} showCart={false} /></li>
            ))}
          </ul>
        )}
      </section>
    </ConsumerAccountLayout>
  )
}
