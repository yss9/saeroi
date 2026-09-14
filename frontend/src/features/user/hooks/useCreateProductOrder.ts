import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { consumerOrderKeys } from '../api/consumerOrderKeys.ts'
import {
  createProductOrder,
  type CreateProductOrderRequest,
} from '../api/createProductOrder.ts'
import { productKeys } from '../../product/api/productKeys.ts'
import type { ConsumerOrderResponse } from '../types/consumerOrder.types.ts'

/**
 * 제품 주문.
 *
 * **주문이 성사되면 재고가 준다.** 주문 목록뿐 아니라 제품 쪽 캐시도 함께 비운다 —
 * 방금 산 제품의 상세를 그대로 보고 있으면 남은 수량이 옛 값으로 남고, 사용자는 자기
 * 주문이 반영되지 않았다고 읽는다.
 */
export function useCreateProductOrder() {
  const queryClient = useQueryClient()

  return useMutation<ConsumerOrderResponse, AppError, CreateProductOrderRequest>({
    mutationFn: createProductOrder,
    onSuccess: () => {
      // 목록과 상태별 건수가 한 접두사 아래 있다. 둘 다 바뀐다.
      void queryClient.invalidateQueries({ queryKey: consumerOrderKeys.all })
      void queryClient.invalidateQueries({ queryKey: productKeys.all })
    },
  })
}
