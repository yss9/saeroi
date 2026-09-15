import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { productKeys } from '../api/productKeys.ts'
import {
  updateProductModel,
  type UpdateProductModelRequest,
} from '../api/updateProductModel.ts'
import type { ProductModel } from '../types/product.types.ts'

/**
 * 제품 수정.
 *
 * 상세 캐시에는 응답을 바로 넣고 목록은 다시 읽는다. 사용자가 방금 있던 화면(상세)이
 * 먼저 맞아야 하고, 목록은 정렬이나 필터에 따라 자리가 바뀔 수 있어 서버에 다시 묻는 편이
 * 어긋날 여지가 없다.
 *
 * **대표 이미지를 바꾸면 그 이미지도 다시 받아야 한다.** 썸네일은 제품 상세 키 아래에
 * 매달려 있어(`useProductThumbnail`) detail 을 비우면 함께 비워진다.
 */
export function useUpdateProductModel() {
  const queryClient = useQueryClient()

  return useMutation<
    ProductModel,
    AppError,
    { productModelId: number; request: UpdateProductModelRequest }
  >({
    mutationFn: ({ productModelId, request }) => updateProductModel(productModelId, request),
    onSuccess: (product) => {
      queryClient.setQueryData(productKeys.detail(product.productModelId), product)
      void queryClient.invalidateQueries({ queryKey: productKeys.detail(product.productModelId) })
      void queryClient.invalidateQueries({ queryKey: productKeys.lists() })
    },
  })
}
