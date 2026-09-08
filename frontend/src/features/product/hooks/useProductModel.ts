import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getProductModel } from '../api/getProductModel.ts'
import { productKeys } from '../api/productKeys.ts'
import type { ProductModel } from '../types/product.types.ts'

/** 제품 상세. id 가 없으면(주소가 잘못됨) 요청하지 않는다. */
export function useProductModel(productModelId: number | null) {
  return useQuery<ProductModel, AppError>({
    queryKey: productKeys.detail(productModelId ?? 0),
    queryFn: () => getProductModel(productModelId as number),
    enabled: productModelId !== null,
  })
}
