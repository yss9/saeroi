import { Link, useNavigate } from 'react-router-dom'
import { EmptyState } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { CatalogCard } from '../../features/shop/CatalogCard.tsx'
import { useRecentlyViewedProducts } from '../../features/product/hooks/useRecentlyViewedProducts.ts'
import '../shop/shop.css'
import '../../features/wishlist/wishlist.css'

/**
 * 최근 본 상품.
 *
 * 상품 상세에서 저장한 브라우저 로컬 이력을 최근 순으로 보여준다.
 * 사라진 상품은 조용히 제외되고, 상품 카드는 장바구니 버튼 없이 상세로 연결한다.
 */
export function ConsumerRecentlyViewedPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null
  const recent = useRecentlyViewedProducts()
  const items = recent.products

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

        {recent.isPending ? (
          <div className="shop-catalog__state" role="status">상품을 불러오는 중</div>
        ) : recent.isError ? (
          <div className="shop-catalog__state" role="alert">
            <h3>최근 본 상품을 불러오지 못했습니다</h3>
            <button type="button" onClick={() => void recent.refetch()}>다시 시도</button>
          </div>
        ) : items.length === 0 ? (
          <EmptyState
            title="최근 본 상품이 없습니다"
            description="상품을 둘러보면 여기에 쌓입니다."
            action={<Link className="ui-button ui-button--primary ui-button--sm" to="/shop">SHOP 보기</Link>}
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
