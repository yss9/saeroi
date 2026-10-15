import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { confirmProductOrderDelivery } from '../api/confirmProductOrderDelivery.ts'
import { consumerOrderKeys } from '../api/consumerOrderKeys.ts'
import type { ConsumerOrderResponse } from '../types/consumerOrder.types.ts'

/**
 * 수령 확인.
 *
 * **목록과 주문 현황을 함께 비운다.** 둘은 다른 요청이라(`/me`·`/me/summary`) 한쪽만 비우면
 * 카드에는 "배송 완료" 가 뜨는데 아래 현황표는 여전히 "배송 중" 에 1 을 세고 있다 — 같은
 * 화면에서 두 값이 어긋나면 사용자는 어느 쪽을 믿어야 할지 알 수 없다.
 *
 * 응답을 캐시에 직접 넣지 않는다. 목록의 한 줄은 개체이고 응답은 주문이라, 주문에 제품이
 * 여럿이면 여러 줄을 한꺼번에 고쳐야 한다 — 다시 읽는 편이 어긋날 여지가 없다.
 */
export function useConfirmProductOrderDelivery() {
  const queryClient = useQueryClient()

  return useMutation<ConsumerOrderResponse, AppError, number>({
    mutationFn: confirmProductOrderDelivery,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: consumerOrderKeys.all })
    },
  })
}
