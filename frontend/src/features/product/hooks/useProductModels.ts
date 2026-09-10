import { useQuery } from '@tanstack/react-query'
import type { PageResponse } from '../../../types/api.types.ts'
import type { AppError } from '../../../utils/errors.ts'
import { getProductModels } from '../api/getProductModels.ts'
import { productKeys } from '../api/productKeys.ts'
import type { ProductModel, ProductModelFilters } from '../types/product.types.ts'

/**
 * 마켓 제품 목록.
 *
 * 필터를 키에 담는다 — 카테고리를 바꾸면 다른 캐시가 된다. `useMyProductModels` 와는
 * 키가 다르다 (`{ mine: true }`): 이쪽은 `ON_SALE` 만, 그쪽은 `DRAFT` 를 포함하므로
 * 한 캐시를 공유하면 마켓에 준비 중인 제품이 스쳐 보인다.
 */
export function useProductModels(filters: ProductModelFilters = {}) {
  return useQuery<PageResponse<ProductModel>, AppError>({
    queryKey: productKeys.list(filters),
    queryFn: () => getProductModels(filters),
  })
}
