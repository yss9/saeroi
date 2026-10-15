import { apiClient } from '../../../services/apiClient.ts'
import type { PageResponse } from '../../../types/api.types.ts'
import type {
  PublicProductModel,
  PublicProductModelFilters,
} from '../types/publicProduct.types.ts'

/** 비회원도 조회할 수 있는 판매 중 상품 목록. */
export async function getPublicProductModels(
  filters: PublicProductModelFilters = {},
): Promise<PageResponse<PublicProductModel>> {
  const { data } = await apiClient.get<PageResponse<PublicProductModel>>(
    '/v1/public/product-models',
    {
      params: filters,
      // 로그인 상태와 무관한 공개 요청이다. 만료 토큰 때문에 조회가 막히거나
      // 불필요한 재발급이 일어나지 않도록 인증 흐름에서 분리한다.
      skipAuth: true,
      skipTokenRefresh: true,
    },
  )
  return data
}
