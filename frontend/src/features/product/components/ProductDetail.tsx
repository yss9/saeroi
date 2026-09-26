import { ErrorMessage, Loading, StatusBadge, type StatusTone } from '../../../components/common/index.ts'
import { formatWon } from '../../../utils/quantity.ts'
import { useProductModel } from '../hooks/useProductModel.ts'
import { useProductThumbnail } from '../hooks/useProductThumbnail.ts'
import {
  PRODUCT_CATEGORY_LABEL,
  PRODUCT_MODEL_STATUS_LABEL,
  type ProductModel,
  type ProductModelStatus,
} from '../types/product.types.ts'
import './product.css'

export interface ProductDetailProps {
  productModelId: number
}

/**
 * 상태 → 톤.
 *
 * 도메인 상태를 공통 컴포넌트가 알지 못하게 매핑을 여기 둔다 (`StatusBadge` 주석 참고).
 *
 * `DRAFT` 를 회색으로 두는 것이 중요하다. 판매 준비는 "아직 팔지 않는다" 는 뜻인데
 * 초록으로 칠하면 판매 중과 구분되지 않는다.
 */
const TONE: Record<ProductModelStatus, StatusTone> = {
  DRAFT: 'neutral',
  ON_SALE: 'success',
  SOLD_OUT: 'warning',
  DISCONTINUED: 'danger',
}

/**
 * 제품 상세.
 *
 * **로그인이 필요한 화면이다.** 제품 조회 API 와 이미지 모두 인증을 요구한다
 * (`SecurityConfig` 가 `/api/v1/auth/**` 와 헬스체크만 열어 둔다). 그래서 라우트도
 * `ProtectedRoute` 안에 있다 — 공개로 두면 비회원에게 401 로 빈 화면이 나간다.
 *
 * 이미지는 `<img src={thumbnailUrl}>` 로 그릴 수 없다. `thumbnailUrl` 은 저장 키이고,
 * 실제 이미지 경로는 인증을 요구하는 별도 엔드포인트다 (`useProductThumbnail` 참고).
 */
export function ProductDetail({ productModelId }: ProductDetailProps) {
  const { data, isPending, isError, error, refetch } = useProductModel(productModelId)

  if (isPending) return <Loading variant="skeleton" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  return (
    <article className="product-detail">
      <ProductThumbnail productModelId={productModelId} name={data.name} />

      <div className="product-detail__body">
        <header className="product-detail__header">
          <div className="product-detail__title">
            <h2>{data.name}</h2>
            <StatusBadge tone={TONE[data.status]}>
              {PRODUCT_MODEL_STATUS_LABEL[data.status]}
            </StatusBadge>
          </div>
          <p className="product-detail__category">{PRODUCT_CATEGORY_LABEL[data.category]}</p>
        </header>

        <p className="product-detail__price">{formatWon(data.price)}</p>

        <StockNotice product={data} />

        {/* 사용자가 적은 글이라 줄바꿈을 그대로 살린다. */}
        <p className="product-detail__description">{data.description}</p>
      </div>
    </article>
  )
}

/**
 * 재고 안내.
 *
 * **수량만 보여주면 안 된다.** 제품 모델은 설계도이고 팔 물건은 생산에서 나오므로,
 * 등록만 된 제품은 상태가 `DRAFT` 이고 수량이 0 이다. 그 0 을 숫자로만 두면 사용자는
 * "품절" 로 읽는다 — 아직 만들지 않은 것과 다 팔린 것은 다르다.
 */
function StockNotice({ product }: { product: ProductModel }) {
  if (product.status === 'DRAFT') {
    return (
      <p className="product-detail__stock product-detail__stock--muted">
        아직 판매를 시작하지 않은 제품입니다. 생산 제품이 등록된 뒤 판매가 시작됩니다.
      </p>
    )
  }

  if (product.status === 'DISCONTINUED') {
    return <p className="product-detail__stock product-detail__stock--muted">단종된 제품입니다.</p>
  }

  if (product.stockQuantity === 0) {
    return <p className="product-detail__stock product-detail__stock--muted">품절되었습니다.</p>
  }

  return <p className="product-detail__stock">남은 수량 {product.stockQuantity}개</p>
}

/**
 * 대표 이미지.
 *
 * 이미지가 없는 것은 실패가 아니라 하나의 상태다 — 서버가
 * `PRODUCT_THUMBNAIL_NOT_FOUND` 를 주는 제품이 있을 수 있고, 그때 화면 전체를 에러로
 * 덮으면 볼 수 있는 정보까지 사라진다. 자리표시자로 대신한다.
 */
function ProductThumbnail({ productModelId, name }: { productModelId: number; name: string }) {
  const { url, isPending, isError } = useProductThumbnail(productModelId)

  return (
    <div className="product-detail__image">
      {isPending && <Loading label="이미지를 불러오는 중" />}
      {url !== null && <img src={url} alt={`${name} 대표 이미지`} />}
      {isError && (
        <p className="product-detail__image-empty" role="status">
          이미지를 불러오지 못했습니다.
        </p>
      )}
    </div>
  )
}
