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
  imageUrl: string
}

const priceFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
})

/** Pixabay 무료 이미지(2026-09-17) — 카드 크기·간격 확인용. 상품과 1:1로 대응하지 않고 돌려쓴다. */
const IMAGE_POOL: readonly string[] = [
  'https://cdn.pixabay.com/photo/2022/05/06/18/00/woman-7178848_1280.jpg',
  'https://cdn.pixabay.com/photo/2020/10/26/18/05/man-5688112_640.jpg',
  'https://cdn.pixabay.com/photo/2018/10/28/23/39/shoes-3780166_640.jpg',
  'https://cdn.pixabay.com/photo/2020/10/30/15/43/woman-5698925_640.jpg',
  'https://cdn.pixabay.com/photo/2016/03/23/08/34/woman-1274361_640.jpg',
  'https://cdn.pixabay.com/photo/2017/08/01/11/48/woman-2564660_640.jpg',
  'https://cdn.pixabay.com/photo/2020/04/09/09/30/nike-5020608_640.jpg',
  'https://cdn.pixabay.com/photo/2022/05/19/18/41/woman-7207965_640.jpg',
  'https://cdn.pixabay.com/photo/2016/11/29/03/26/pair-1867051_640.jpg',
  'https://cdn.pixabay.com/photo/2017/01/23/19/40/woman-2003647_640.jpg',
  'https://cdn.pixabay.com/photo/2016/11/22/19/08/hangers-1850082_640.jpg',
  'https://cdn.pixabay.com/photo/2017/02/26/13/22/clothing-2100208_640.jpg',
  'https://cdn.pixabay.com/photo/2020/07/20/15/17/clothing-5423353_640.jpg',
  'https://cdn.pixabay.com/photo/2024/05/28/03/32/clothes-8792691_640.jpg',
  'https://cdn.pixabay.com/photo/2022/05/09/19/42/hangers-7185447_640.jpg',
]

const PRODUCT_POOL: readonly { productName: string; brandName: string; price: number }[] = [
  { productName: '업사이클 데님 토트백', brandName: 'ReWeave', price: 89000 },
  { productName: '패치워크 파우치', brandName: 'ReWeave', price: 39000 },
  { productName: '스컬그레이몬', brandName: '한동규랜드', price: 5000 },
  { productName: '재생 울 니트', brandName: 'ReWeave', price: 132000 },
  { productName: '리사이클 캔버스 백팩', brandName: 'ReWeave', price: 76000 },
  { productName: '업사이클 스니커즈', brandName: '한동규랜드', price: 68000 },
  { productName: '빈티지 데님 자켓', brandName: 'ReWeave', price: 98000 },
  { productName: '리메이크 니트 가디건', brandName: 'ReWeave', price: 112000 },
  { productName: '업사이클 크로스백', brandName: '한동규랜드', price: 54000 },
  { productName: '리사이클 러너 슈즈', brandName: 'ReWeave', price: 71000 },
]

/**
 * 화면 확인용 목데이터. 찜하기 API·테이블이 아직 없다.
 *
 * 15~20개로 채운다(2026-09-17) — 그리드가 몇 줄로 넘어갔을 때도 카드 크기·간격이
 * 흐트러지지 않는지 보려는 용도라 3개로는 확인이 안 됐다.
 */
const MOCK_WISHLIST: readonly WishlistItem[] = Array.from({ length: 18 }, (_, index) => ({
  id: String(index + 1),
  ...PRODUCT_POOL[index % PRODUCT_POOL.length],
  imageUrl: IMAGE_POOL[index % IMAGE_POOL.length],
}))

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
                  <img src={item.imageUrl} alt={item.productName} />
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
                      {addedToCartIds[item.id] ? (
                        // 장바구니 화면이 아직 없어(카트 API 미착수) 이동은 하지 않는다 —
                        // 버튼 자체가 "이미 담겨 있다"는 상태만 보여준다.
                        <Button variant="secondary" size="sm">
                          장바구니로 이동
                        </Button>
                      ) : (
                        <Button variant="primary" size="sm" onClick={() => addToCart(item.id)}>
                          장바구니 담기
                        </Button>
                      )}
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
