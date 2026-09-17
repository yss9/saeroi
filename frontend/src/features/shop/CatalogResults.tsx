import type { usePublicProductModels } from '../product/hooks/usePublicProductModels.ts'
import { CatalogCard } from './CatalogCard.tsx'
import { CatalogPagination } from './CatalogPagination.tsx'

interface Props {
  catalog: ReturnType<typeof usePublicProductModels>
  page: number
  onPageChange: (page: number) => void
  emptyTitle?: string
  emptyDescription?: string
}

export function CatalogResults({ catalog, page, onPageChange, emptyTitle = '판매 중인 상품이 없습니다', emptyDescription = '브랜드의 새로운 상품을 준비하고 있습니다.' }: Props) {
  const data = catalog.data
  return <>
    <div aria-busy={catalog.isFetching}>
      {catalog.isPending ? (
        <div className="shop-catalog__state" role="status"><p>상품을 불러오는 중</p><div className="shop-catalog__skeleton" aria-hidden="true" /></div>
      ) : catalog.isError ? (
        <div className="shop-catalog__state" role="alert"><h2>상품을 불러오지 못했습니다</h2><p>잠시 후 다시 시도해 주세요.</p><button type="button" onClick={() => void catalog.refetch()}>다시 시도</button></div>
      ) : data && page > 0 && page >= data.totalPages ? (
        <div className="shop-catalog__state" role="status"><h2>요청한 페이지에 상품이 없습니다</h2><button type="button" onClick={() => onPageChange(0)}>첫 페이지 보기</button></div>
      ) : !data || data.content.length === 0 ? (
        <div className="shop-catalog__state" role="status"><h2>{emptyTitle}</h2><p>{emptyDescription}</p></div>
      ) : (
        <ul className="shop-catalog__grid" aria-label="상품 목록">{data.content.map((product) => <li key={product.productModelId}><CatalogCard product={product} /></li>)}</ul>
      )}
    </div>
    {!catalog.isError && data && page < data.totalPages ? <CatalogPagination page={data.page} totalPages={data.totalPages} hasNext={data.hasNext} onPageChange={onPageChange} /> : null}
  </>
}
