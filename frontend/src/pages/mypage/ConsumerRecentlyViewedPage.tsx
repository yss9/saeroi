import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, EmptyState } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import '../../features/user/components/consumer-wishlist.css'

interface RecentlyViewedItem {
  id: string
  productName: string
  brandName: string
  price: number
  viewedAt: string
}

const priceFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
})

/** 화면 확인용 목데이터. 조회 이력 저장이 아직 없다(프론트 로컬 기록도, 서버 기록도 없음). */
const MOCK_RECENTLY_VIEWED: readonly RecentlyViewedItem[] = [
  {
    id: '1',
    productName: '업사이클 데님 토트백',
    brandName: 'ReWeave',
    price: 89000,
    viewedAt: '2026-09-16T10:12:00',
  },
  {
    id: '2',
    productName: '재생 울 니트',
    brandName: 'ReWeave',
    price: 132000,
    viewedAt: '2026-09-15T18:40:00',
  },
  {
    id: '3',
    productName: '패치워크 파우치',
    brandName: 'ReWeave',
    price: 39000,
    viewedAt: '2026-09-14T09:30:00',
  },
  {
    id: '4',
    productName: '스컬그레이몬',
    brandName: '한동규랜드',
    price: 5000,
    viewedAt: '2026-09-12T21:05:00',
  },
]

/**
 * 최근 본 상품.
 *
 * **프론트엔드 UI만 먼저 만든 화면이다(2026-09-16).** 상품 상세를 볼 때마다 기록을 남기는
 * 기능이 아직 없어 목데이터로 화면 구조(가로 스크롤 카드)만 잡는다.
 */
export function ConsumerRecentlyViewedPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { name: user.name, email: user.email } : null

  const [addedToCartIds, setAddedToCartIds] = useState<Record<string, boolean>>({})

  function addToCart(id: string) {
    // 장바구니 기능이 아직 없어(백엔드에 카트 API가 없음) 로컬 확인 표시만 한다.
    setAddedToCartIds((prev) => ({ ...prev, [id]: true }))
  }

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

        {MOCK_RECENTLY_VIEWED.length === 0 ? (
          <EmptyState
            title="최근 본 상품이 없습니다"
            description="상품을 둘러보면 여기에 쌓입니다."
          />
        ) : (
          <div className="consumer-recently-viewed-row">
            {MOCK_RECENTLY_VIEWED.map((item) => (
              <article className="consumer-recently-viewed-card" key={item.id}>
                <div className="consumer-recently-viewed-card__thumbnail">
                  <span aria-hidden="true" />
                </div>
                <div className="consumer-recently-viewed-card__body">
                  <p className="consumer-recently-viewed-card__brand">{item.brandName}</p>
                  <h3>{item.productName}</h3>
                  <div className="consumer-recently-viewed-card__price-row">
                    <p className="consumer-recently-viewed-card__price">
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
