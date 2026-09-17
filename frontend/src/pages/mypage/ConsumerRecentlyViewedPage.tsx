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
  imageUrl: string
}

const priceFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
})

/** Pixabay 무료 이미지(2026-09-17) — 카드 크기·간격 확인용. 상품과 1:1로 대응하지 않고 돌려쓴다. */
const IMAGE_POOL: readonly string[] = [
  'https://cdn.pixabay.com/photo/2020/10/30/15/43/woman-5698925_640.jpg',
  'https://cdn.pixabay.com/photo/2016/03/23/08/34/woman-1274361_640.jpg',
  'https://cdn.pixabay.com/photo/2017/08/01/11/48/woman-2564660_640.jpg',
  'https://cdn.pixabay.com/photo/2020/04/09/09/30/nike-5020608_640.jpg',
  'https://cdn.pixabay.com/photo/2022/05/06/18/00/woman-7178848_1280.jpg',
  'https://cdn.pixabay.com/photo/2020/10/26/18/05/man-5688112_640.jpg',
  'https://cdn.pixabay.com/photo/2018/10/28/23/39/shoes-3780166_640.jpg',
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
  { productName: '재생 울 니트', brandName: 'ReWeave', price: 132000 },
  { productName: '패치워크 파우치', brandName: 'ReWeave', price: 39000 },
  { productName: '스컬그레이몬', brandName: '한동규랜드', price: 5000 },
  { productName: '리사이클 캔버스 백팩', brandName: 'ReWeave', price: 76000 },
  { productName: '업사이클 스니커즈', brandName: '한동규랜드', price: 68000 },
  { productName: '빈티지 데님 자켓', brandName: 'ReWeave', price: 98000 },
  { productName: '리메이크 니트 가디건', brandName: 'ReWeave', price: 112000 },
  { productName: '업사이클 크로스백', brandName: '한동규랜드', price: 54000 },
  { productName: '리사이클 러너 슈즈', brandName: 'ReWeave', price: 71000 },
]

const LATEST_VIEWED_AT = new Date('2026-09-16T10:12:00').getTime()

/**
 * 화면 확인용 목데이터. 조회 이력 저장이 아직 없다(프론트 로컬 기록도, 서버 기록도 없음).
 *
 * 15~20개로 채운다(2026-09-17) — 예전엔 4개뿐이라 한 줄에 다 들어가, 여러 줄로 넘어갔을
 * 때 카드 크기·간격이 어떻게 보이는지 확인할 수 없었다.
 */
const MOCK_RECENTLY_VIEWED: readonly RecentlyViewedItem[] = Array.from(
  { length: 18 },
  (_, index) => ({
    id: String(index + 1),
    ...PRODUCT_POOL[index % PRODUCT_POOL.length],
    // 최근 볼수록 앞에 오도록 하나씩 20시간 전으로 밀어낸다.
    viewedAt: new Date(LATEST_VIEWED_AT - index * 20 * 60 * 60 * 1000).toISOString(),
    imageUrl: IMAGE_POOL[index % IMAGE_POOL.length],
  }),
)

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
                  <img src={item.imageUrl} alt={item.productName} />
                </div>
                <div className="consumer-recently-viewed-card__body">
                  <p className="consumer-recently-viewed-card__brand">{item.brandName}</p>
                  <h3>{item.productName}</h3>
                  <div className="consumer-recently-viewed-card__price-row">
                    <p className="consumer-recently-viewed-card__price">
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
