import { useQuery } from '@tanstack/react-query'
import type { PageResponse } from '../../../types/api.types.ts'
import type { AppError } from '../../../utils/errors.ts'
import { getPublicProductModels } from '../api/getPublicProductModels.ts'
import { publicProductKeys } from '../api/publicProductKeys.ts'
import type {
  PublicProductModel,
  PublicProductModelFilters,
} from '../types/publicProduct.types.ts'

/** 소비자 쇼핑몰의 공개 판매 상품 목록. */
export function usePublicProductModels(filters: PublicProductModelFilters = {}) {
  return useQuery<PageResponse<PublicProductModel>, AppError>({
    queryKey: publicProductKeys.list(filters),
    queryFn: () => getPublicProductModels(filters),
  })
}
