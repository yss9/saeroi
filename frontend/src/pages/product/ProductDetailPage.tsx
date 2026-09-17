import { useParams } from 'react-router-dom'
import { BackLink, ErrorMessage } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { ProductDetail } from '../../features/product/components/ProductDetail.tsx'
import { useProductModel } from '../../features/product/hooks/useProductModel.ts'
import type { ProductModel } from '../../features/product/types/product.types.ts'
import { ProductPurchasePanel } from '../../features/user/components/ProductPurchasePanel.tsx'

/**
 * 제품 상세.
 *
 * **로그인이 필요하다.** 제품 조회와 이미지 모두 서버가 인증을 요구한다. 공개 라우트로
 * 두면 비회원에게 401 로 빈 화면이 나가므로 `ProtectedRoute` 안에 있다.
 *
 * **구매는 소비자만 한다.** 제품 정보(`product`)와 주문·배송지(`user`)는 서로 다른
 * 도메인이라 조립을 페이지가 한다 (ARCHITECTURE §12.1) — 제품 컴포넌트가 배송지 훅을
 * 부르기 시작하면 feature 경계가 무너진다.
 *
 * ⚠️ 소비자가 QR 로 들어오는 입구는 이 화면이 아니라 `/qr/:qrToken` 다.
 * 그쪽은 비회원 공개여야 한다 (ARCHITECTURE §9) — 이 화면과 혼동하지 말 것.
 *
 * **돌아갈 곳이 역할마다 다르다.** 브랜드는 내 제품에서, 나머지는 업무 목록에서 들어온다 —
 * 한쪽으로 고정하면 브랜드가 남의 목록으로 튕겨 자기 제품을 다시 찾아야 한다.
 */
/** 돌아갈 목록. 문구와 경로를 한 쌍으로 묶는다 — 따로 두면 이름과 목적지가 어긋난다. */
const MY_PRODUCTS = { to: '/brand/products', label: '내 제품' } as const
const CATALOG = { to: '/business/products', label: '제품 목록' } as const

export function ProductDetailPage() {
  const { productId } = useParams<{ productId: string }>()
  const { role } = useAuth()
  const parsed = Number(productId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  // 상세와 같은 Query 키를 본다. 요청이 한 번 더 나가지 않는다.
  const product = useProductModel(id)

  const back = role === 'BRAND' ? MY_PRODUCTS : CATALOG

  if (id === null) {
    return (
      <main className="page">
        <BackLink {...back} />
        <ErrorMessage error="잘못된 주소입니다." />
      </main>
    )
  }

  return (
    <main className="page">
      <BackLink {...back} />

      <ProductDetail productModelId={id} />

      {role === 'CONSUMER' && product.data && (
        <ProductPurchasePanel
          productModelId={id}
          purchasable={isPurchasable(product.data)}
          blockedReason={blockedReason(product.data)}
        />
      )}
    </main>
  )
}

/** 살 수 있는 상태인가. 서버가 거절할 조건을 화면이 먼저 본다. */
function isPurchasable(product: ProductModel): boolean {
  return product.status === 'ON_SALE' && product.stockQuantity > 0
}

/**
 * 못 사는 이유.
 *
 * 버튼만 잠그면 사용자는 자기가 뭘 잘못했다고 생각한다. 특히 **등록만 된 제품의 재고 0 은
 * "다 팔렸다" 가 아니라 "아직 만들지 않았다"** 라, 품절과 같은 문구를 쓰면 기다리면 살 수
 * 있는 것처럼 읽힌다.
 */
function blockedReason(product: ProductModel): string | undefined {
  if (product.status === 'DISCONTINUED') return '단종된 제품이라 주문할 수 없습니다.'
  if (product.status === 'DRAFT') return '아직 판매를 시작하지 않은 제품입니다.'
  if (product.stockQuantity === 0) return '품절되어 지금은 주문할 수 없습니다.'
  return undefined
}
