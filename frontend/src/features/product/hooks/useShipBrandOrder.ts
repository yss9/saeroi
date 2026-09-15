import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { shipBrandOrder } from '../api/brandOrderApi.ts'
import { brandOrderKeys } from '../api/brandOrderKeys.ts'
import type {
  BrandOrderShipRequest,
  BrandOrderShipResult,
} from '../types/brandOrder.types.ts'

/**
 * 발송 처리.
 *
 * **목록 전체를 다시 읽는다.** 응답이 바뀐 값만 담는 다른 타입이라 목록 행에 그대로 넣을
 * 수 없고, 무엇보다 그 주문이 발송 대기 목록에서 **빠져야** 한다 — 상태만 고쳐 넣으면
 * `status=PAID` 로 거른 목록에 이미 보낸 주문이 남는다.
 *
 * 발송 대기 건수도 같은 접두사 아래라 함께 갱신된다.
 */
export function useShipBrandOrder() {
  const queryClient = useQueryClient()

  return useMutation<
    BrandOrderShipResult,
    AppError,
    { orderId: number; request: BrandOrderShipRequest }
  >({
    mutationFn: ({ orderId, request }) => shipBrandOrder(orderId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: brandOrderKeys.all })
    },
  })
}
