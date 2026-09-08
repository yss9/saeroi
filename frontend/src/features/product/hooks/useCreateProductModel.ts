import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { createProductModel } from '../api/createProductModel.ts'
import { productKeys } from '../api/productKeys.ts'
import type { CreateProductModelRequest, ProductModel } from '../types/product.types.ts'

/**
 * 제품 등록.
 *
 * 목록 캐시를 무효화한다 — 등록 직후 목록에 안 보이면 사용자는 등록이 실패한 줄 안다.
 * 상세는 응답으로 받은 값을 그대로 심는다. 등록 응답이 완전한 `ProductModel` 이라
 * 반쪽짜리가 되지 않는다.
 */
export function useCreateProductModel() {
  const queryClient = useQueryClient()

  return useMutation<ProductModel, AppError, CreateProductModelRequest>({
    mutationFn: createProductModel,
    onSuccess: (product) => {
      queryClient.setQueryData(productKeys.detail(product.productModelId), product)
      void queryClient.invalidateQueries({ queryKey: productKeys.lists() })
    },
  })
}
