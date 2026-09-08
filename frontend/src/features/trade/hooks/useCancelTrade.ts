import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { marketplaceKeys } from '../../marketplace/api/marketplaceKeys.ts'
import { cancelTrade } from '../api/paymentApi.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import {
  STALE_TRADE_ERROR_CODES,
  type CancelTradeRequest,
  type Trade,
} from '../types/trade.types.ts'

/**
 * 거래 취소.
 *
 * **`usePayment.cancel` 과 같은 엔드포인트를 부른다.** 그쪽은 결제 흐름(PaymentPhase)의
 * 한 갈래로 취소를 다루므로 결제창을 오가는 상태 기계가 함께 붙어 있다. 판매자 취소는
 * 결제와 무관하니 그 상태 기계를 끌고 올 이유가 없어 훅을 따로 둔다.
 *
 * HTTP 호출은 `cancelTrade` 하나를 공유한다 — 경로나 payload 규칙이 갈라지면
 * 한쪽만 고쳐질 자리가 생긴다.
 *
 * 취소는 판매글의 잔여 수량을 되돌리므로 marketplace 캐시도 무효화한다. 그러지 않으면
 * 되살아난 수량이 목록에 보이지 않는다.
 */
export function useCancelTrade(tradeId: number) {
  const queryClient = useQueryClient()

  return useMutation<Trade, AppError, CancelTradeRequest>({
    mutationFn: ({ reason }) => cancelTrade(tradeId, reason),

    onSuccess: (trade) => {
      // 취소 응답은 거래 전체다(발송·수령과 다르다). 그대로 덮어도 반쪽이 되지 않는다.
      queryClient.setQueryData(tradeKeys.detail(tradeId), trade)
      void queryClient.invalidateQueries({ queryKey: tradeKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.all })
    },

    onError: (error) => {
      // 이미 발송된 거래는 취소할 수 없다(PAYMENT_STATE_INVALID). 화면이 낡았다는 뜻이라
      // 사유만 띄우고 끝내면 사용자가 같은 버튼을 다시 누른다.
      if (error.code !== null && STALE_TRADE_ERROR_CODES.includes(error.code)) {
        void queryClient.invalidateQueries({ queryKey: tradeKeys.detail(tradeId) })
        void queryClient.invalidateQueries({ queryKey: tradeKeys.lists() })
      }
    },
  })
}
