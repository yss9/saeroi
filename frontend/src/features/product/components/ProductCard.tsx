import { Link } from 'react-router-dom'
import { StatusBadge } from '../../../components/common/index.ts'
import { formatWon } from '../../../utils/quantity.ts'
import { useProductThumbnail } from '../hooks/useProductThumbnail.ts'
import {
  PRODUCT_CATEGORY_LABEL,
  type ProductModel,
} from '../types/product.types.ts'
import './product.css'

export interface ProductCardProps {
  product: ProductModel
}

/**
 * 마켓 제품 카드.
 *
 * **이미지를 카드마다 따로 받는다.** 대표 이미지 경로가 인증을 요구해 `<img src>` 로는
 * 못 쓰고, 제품 id 로 요청해 blob URL 로 바꿔야 한다 (`useProductThumbnail`).
 *
 * 목록에 12개가 오면 요청이 12개 나간다. 서버가 `Cache-Control: no-store` 로 주므로
 * 브라우저 캐시도 안 쓴다 — 한 번에 여러 장을 주는 엔드포인트가 생기면 그때 줄인다.
 * 지금 구조로는 카드가 각자 받는 것이 유일한 방법이다.
 *
 * ⚠️ **재고 0 인 제품도 목록에 온다.** 다 팔린 제품을 `SOLD_OUT` 으로 바꾸는 것은 주문
 * 처리의 몫이고 그 코드가 아직 없다. 그래서 상태가 `ON_SALE` 이어도 재고가 0 일 수 있어,
 * 카드가 `stockQuantity` 를 보고 품절로 표시한다.
 */
export function ProductCard({ product }: ProductCardProps) {
  const thumbnail = useProductThumbnail(product.productModelId)
  const soldOut = product.stockQuantity === 0

  return (
    <li className="product-card">
      <Link to={`/products/${product.productModelId}`} className="product-card__link">
        <div className="product-card__image">
          {thumbnail.url === null ? (
            // 이미지가 없거나 아직 못 받은 상태. 자리를 비워 두면 카드 높이가 흔들린다.
            <span className="product-card__image-empty" aria-hidden="true" />
          ) : (
            <img src={thumbnail.url} alt={`${product.name} 대표 이미지`} />
          )}
          {soldOut && <span className="product-card__sold-out">품절</span>}
        </div>

        <div className="product-card__body">
          <span className="product-card__category">
            {PRODUCT_CATEGORY_LABEL[product.category]}
          </span>
          <strong className="product-card__name">{product.name}</strong>
          <span className="product-card__price">{formatWon(product.price)}</span>
          {/* 판매 중이 아닌 제품이 섞여 들어오면 왜 못 사는지 알려야 한다.
              기본 필터는 ON_SALE 이지만 화면이 status 를 지정할 수도 있다. */}
          {product.status !== 'ON_SALE' && (
            <StatusBadge tone="neutral">판매 중이 아닙니다</StatusBadge>
          )}
        </div>
      </Link>
    </li>
  )
}
