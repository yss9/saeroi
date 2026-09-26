import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { changeProductModelStatus } from '../api/changeProductModelStatus.ts'
import { productKeys } from '../api/productKeys.ts'
import type { ProductModel, ProductStatusAction } from '../types/product.types.ts'

export interface ChangeStatusVariables {
  productModelId: number
  action: ProductStatusAction
}

/**
 * 판매 시작 / 중단.
 *
 * **마켓 목록도 무효화한다.** 판매를 시작하면 그 제품이 마켓에 나타나고 중단하면 사라지는데,
 * 목록 캐시가 그대로면 사용자는 방금 한 일이 반영되지 않았다고 읽는다. 도메인이 같아도
 * 두 목록은 다른 키를 쓰므로 둘 다 건드려야 한다.
 */
export function useChangeProductModelStatus() {
  const queryClient = useQueryClient()

  return useMutation<ProductModel, AppError, ChangeStatusVariables>({
    mutationFn: ({ productModelId, action }) => changeProductModelStatus(productModelId, action),

    onSuccess: (product) => {
      queryClient.setQueryData(productKeys.detail(product.productModelId), product)
      void queryClient.invalidateQueries({ queryKey: productKeys.lists() })
    },
  })
}
