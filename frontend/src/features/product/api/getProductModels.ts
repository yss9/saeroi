import { apiClient } from '../../../services/apiClient.ts'
import type { PageResponse } from '../../../types/api.types.ts'
import type { ProductModel, ProductModelFilters } from '../types/product.types.ts'

/**
 * 마켓 제품 목록 — `GET /v1/product-models`
 *
 * 아무 조건도 주지 않으면 **판매 중인 제품 전체, 최신순, 첫 페이지**다. 서버가 기본값을
 * `ON_SALE` 로 접으므로 화면이 매번 status 를 실어 보낼 필요가 없다.
 *
 * `undefined` 인 필드는 axios 가 쿼리에서 빼므로 서버가 기본값을 쓴다 — 화면이 "전체"를
 * 뜻하는 특수값을 만들어 보내지 않아도 된다.
 *
 * 봉투는 다른 도메인과 같은 `PageResponse` 다. `page` 는 0부터다.
 */
export async function getProductModels(
  filters: ProductModelFilters = {},
): Promise<PageResponse<ProductModel>> {
  const { data } = await apiClient.get<PageResponse<ProductModel>>('/v1/product-models', {
    params: filters,
  })
  return data
}
