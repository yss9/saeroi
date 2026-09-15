import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { productKeys } from '../../product/api/productKeys.ts'
import { consumerOrderKeys } from '../api/consumerOrderKeys.ts'
import { cancelProductOrder } from '../api/productOrderPaymentApi.ts'

/**
 * 주문 취소.
 *
 * **취소된 주문은 목록에서 사라진다.** 서버가 행을 이력 테이블로 옮기고 지우기 때문이다 —
 * 상태만 바꾸는 소재 거래와 다른 점이고, 개체를 다시 팔려면 그래야 한다. 그래서 응답을
 * 캐시에 넣을 것이 없고 목록을 다시 읽는다.
 *
 * 제품 캐시도 함께 비운다. 묶여 있던 개체가 재고로 돌아가는데, 방금 보던 제품 상세가 옛
 * 값으로 남으면 품절이던 제품이 계속 품절로 보인다.
 */
export function useCancelProductOrder() {
  const queryClient = useQueryClient()

  return useMutation<void, AppError, number>({
    mutationFn: (orderId) => cancelProductOrder(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: consumerOrderKeys.all })
      void queryClient.invalidateQueries({ queryKey: productKeys.all })
    },
  })
}
