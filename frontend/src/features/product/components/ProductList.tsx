import { useState } from 'react'
import {
  Button,
  EmptyState,
  ErrorMessage,
  Loading,
  Pagination,
} from '../../../components/common/index.ts'
import { useProductModels } from '../hooks/useProductModels.ts'
import {
  PRODUCT_CATEGORIES,
  PRODUCT_CATEGORY_LABEL,
  type ProductCategory,
} from '../types/product.types.ts'
import { ProductCard } from './ProductCard.tsx'
import './product.css'

/**
 * 마켓 제품 목록.
 *
 * **판매 중인 제품만 보여준다.** status 를 보내지 않으면 서버가 `ON_SALE` 로 접는다 —
 * `DRAFT` 는 브랜드가 준비 중인 제품이라 마켓에 뜨면 안 된다. 브랜드 본인 목록은
 * `/brand/products` 가 따로 있고 그쪽이 `DRAFT` 를 포함한다.
 *
 * 카테고리를 바꾸면 첫 페이지로 돌아간다. 3페이지를 보다가 필터를 바꾸면 결과가 3페이지도
 * 안 될 수 있고, 그때 빈 화면이 뜨면 사용자는 "그 카테고리에 제품이 없다" 고 읽는다.
 */
export function ProductList() {
  // 목록 필터는 이 화면에서만 쓰는 값이라 useState 다. 전역으로 올리면 다른 화면에서
  // 남은 필터가 되돌아온다.
  const [category, setCategory] = useState<ProductCategory | null>(null)
  const [page, setPage] = useState(0)

  const { data, isPending, isError, error, refetch } = useProductModels({
    // `undefined` 는 쿼리에서 빠지므로 서버가 기본값(전체 카테고리)을 쓴다.
    category: category ?? undefined,
    page,
  })

  function changeCategory(next: ProductCategory | null) {
    setCategory(next)
    setPage(0)
  }

  return (
    <>
      <div className="product-list__filters" role="group" aria-label="카테고리 필터">
        <Button
          size="sm"
          variant={category === null ? 'primary' : 'secondary'}
          aria-pressed={category === null}
          onClick={() => changeCategory(null)}
        >
          전체
        </Button>
        {PRODUCT_CATEGORIES.map((item) => (
          <Button
            key={item}
            size="sm"
            variant={category === item ? 'primary' : 'secondary'}
            aria-pressed={category === item}
            onClick={() => changeCategory(item)}
          >
            {PRODUCT_CATEGORY_LABEL[item]}
          </Button>
        ))}
      </div>

      {isPending && <Loading variant="skeleton" rows={3} label="제품을 불러오는 중" />}
      {isError && <ErrorMessage error={error} onRetry={() => void refetch()} />}

      {/* "결과 없음" 과 "조회 실패" 를 다르게 다룬다 — 전자는 필터를 바꾸면 되고 후자는
          다시 시도해야 한다 (ARCHITECTURE §8). */}
      {data && data.content.length === 0 && (
        <EmptyState
          title="판매 중인 제품이 없습니다"
          description={
            category === null
              ? '브랜드가 제품을 등록하고 생산하면 여기에 표시됩니다.'
              : `${PRODUCT_CATEGORY_LABEL[category]} 카테고리에 판매 중인 제품이 없습니다.`
          }
          action={
            category !== null ? (
              <Button size="sm" variant="secondary" onClick={() => changeCategory(null)}>
                전체 보기
              </Button>
            ) : undefined
          }
        />
      )}

      {data && data.content.length > 0 && (
        <ul className="product-list">
          {data.content.map((product) => (
            <ProductCard key={product.productModelId} product={product} />
          ))}
        </ul>
      )}

      {data && <Pagination page={data} onChange={setPage} />}
    </>
  )
}
