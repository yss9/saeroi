import { X } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button, EmptyState } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import '../../features/user/components/consumer-wishlist.css'

interface WishlistItem {
  id: string
  productName: string
  brandName: string
  price: number
}

const priceFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
})

/** 화면 확인용 목데이터. 찜하기 API·테이블이 아직 없다. */
const MOCK_WISHLIST: readonly WishlistItem[] = [
  { id: '1', productName: '업사이클 데님 토트백', brandName: 'ReWeave', price: 89000 },
  { id: '2', productName: '패치워크 파우치', brandName: 'ReWeave', price: 39000 },
  { id: '3', productName: '스컬그레이몬', brandName: '한동규랜드', price: 5000 },
]

/**
 * 찜한 상품.
 *
 * **프론트엔드 UI만 먼저 만든 화면이다(2026-09-16).** 찜하기 자체가 아직 없는 기능이라
 * (상품 상세에 찜 버튼도 없다) 목데이터로 화면 구조만 잡는다. "찜 해제" 는 로컬 상태에서만
 * 지운다 — 서버에 반영되지 않는다.
 */
export function ConsumerWishlistPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null

  const [items, setItems] = useState(MOCK_WISHLIST)
  const [addedToCartIds, setAddedToCartIds] = useState<Record<string, boolean>>({})

  function removeItem(id: string) {
    setItems((prev) => prev.filter((it) => it.id !== id))
  }

  function addToCart(id: string) {
    // 장바구니 기능이 아직 없어(백엔드에 카트 API가 없음) 로컬 확인 표시만 한다.
    setAddedToCartIds((prev) => ({ ...prev, [id]: true }))
  }

  return (
    <ConsumerAccountLayout
      current="wishlist"
      profile={profile}
      onSignOut={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
      signOutPending={logout.isPending}
      signOutError={logout.error}
    >
      <section className="consumer-mypage-card" aria-labelledby="wishlist-title">
        <div className="consumer-section-heading">
          <div>
            <p className="consumer-section-heading__eyebrow">WISHLIST</p>
            <h2 id="wishlist-title">찜한 상품</h2>
          </div>
          <span className="consumer-section-heading__note">{items.length}개</span>
        </div>

        {items.length === 0 ? (
          <EmptyState
            title="찜한 상품이 없습니다"
            description="마음에 드는 상품을 찜해 보세요."
            action={
              <Link className="consumer-text-link" to="/products">
                상품 둘러보기
              </Link>
            }
          />
        ) : (
          <div className="consumer-wishlist-grid">
            {items.map((item) => (
              <article className="consumer-wishlist-card" key={item.id}>
                <div className="consumer-wishlist-card__thumbnail">
                  <span aria-hidden="true" />
                  <button
                    type="button"
                    className="consumer-wishlist-card__unlike"
                    aria-label={`${item.productName} 삭제`}
                    onClick={() => removeItem(item.id)}
                  >
                    <X aria-hidden="true" />
                  </button>
                </div>
                <div className="consumer-wishlist-card__body">
                  <p className="consumer-wishlist-card__brand">{item.brandName}</p>
                  <h3>{item.productName}</h3>
                  <div className="consumer-wishlist-card__price-row">
                    <p className="consumer-wishlist-card__price">
                      {priceFormatter.format(item.price)}
                    </p>
                    <div className="consumer-mock-cart-action">
                      <Button variant="primary" size="sm" onClick={() => addToCart(item.id)}>
                        장바구니 담기
                      </Button>
                      {addedToCartIds[item.id] ? (
                        <span className="consumer-mock-cart-message" role="status">
                          담았습니다
                        </span>
                      ) : null}
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
