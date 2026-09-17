import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { useWishlistList } from '../../features/wishlist/useWishlist.ts'
import { CatalogCard } from '../../features/shop/CatalogCard.tsx'
import { CatalogPagination } from '../../features/shop/CatalogPagination.tsx'
import { shopQuery } from '../../features/shop/catalog.ts'
import '../shop/shop.css'
import '../../features/wishlist/wishlist.css'

/** Consumer-only route; availability is owned by the server. */
export function ConsumerWishlistPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const [params, setParams] = useSearchParams()
  const { page } = shopQuery(params)
  const query = useWishlistList(page)
  const data = query.data
  function changePage(next: number) {
    const updated = new URLSearchParams(params)
    if (next === 0) updated.delete('page')
    else updated.set('page', String(next))
    setParams(updated)
  }
  return <ConsumerAccountLayout current="wishlist" profile={user ? { name: user.name, email: user.email } : null}
    onSignOut={() => logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })}
    signOutPending={logout.isPending} signOutError={logout.error}>
    <section className="consumer-mypage-card consumer-server-wishlist" aria-labelledby="wishlist-title" aria-busy={query.isFetching}>
      <div className="consumer-section-heading"><div><p className="consumer-section-heading__eyebrow">WISHLIST</p><h2 id="wishlist-title">찜한 상품</h2></div>
        {data && <span className="consumer-section-heading__note">{data.totalElements}개</span>}
      </div>
      {query.isPending ? <div className="shop-catalog__state" role="status">찜한 상품을 불러오는 중</div>
        : query.isError ? <div className="shop-catalog__state" role="alert"><h3>찜 목록을 불러오지 못했습니다</h3><button type="button" onClick={() => void query.refetch()}>다시 시도</button></div>
        : data && page > 0 && page >= data.totalPages ? <div className="shop-catalog__state" role="status"><p>이 페이지에 찜한 상품이 없습니다</p><button type="button" onClick={() => changePage(0)}>첫 페이지 보기</button></div>
        : !data || data.content.length === 0 ? <div className="shop-catalog__state" role="status"><h3>찜한 상품이 없습니다</h3><p>마음에 드는 상품을 찜해 보세요.</p><Link to="/shop">상품 둘러보기</Link></div>
        : <ul className="shop-catalog__grid" aria-label="찜한 상품 목록">{data.content.map(({ product, purchasable }) => <li key={product.productModelId}><CatalogCard product={product} imageMode="authenticated" purchasable={purchasable} /></li>)}</ul>}
      {!query.isError && data && page < data.totalPages && <CatalogPagination label="찜 목록 페이지" page={data.page} totalPages={data.totalPages} hasNext={data.hasNext} onPageChange={changePage} />}
    </section>
  </ConsumerAccountLayout>
}
