import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { marketplaceKeys } from '../../marketplace/api/marketplaceKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import { confirmTradeReceipt } from '../api/confirmTradeReceipt.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import {
  STALE_TRADE_ERROR_CODES,
  type Trade,
  type TradeReceiptResult,
} from '../types/trade.types.ts'

/**
 * 구매자 수령 확인.
 *
 * **이 요청은 거래 도메인 밖의 캐시까지 낡게 만든다.** 소유권이 실제로 옮겨지므로
 * 양쪽 보유 소재(inventory·material)가 바뀌고, 판매 잠금이 소비되어 마켓플레이스의
 * 잔여 수량도 달라진다. 그래서 네 도메인을 함께 무효화한다 — Feature 경계는 코드 배치의
 * 문제이고 캐시 일관성은 그 경계를 따르지 않는다 (`useCreateTrade` 와 같은 이유).
 *
 * 무효화하지 않으면 "수령 확인했는데 내 보유 소재에 안 보인다" 가 된다.
 */
export function useConfirmTradeReceipt(tradeId: number) {
  const queryClient = useQueryClient()

  return useMutation<TradeReceiptResult, AppError, void>({
    mutationFn: () => confirmTradeReceipt(tradeId),

    onSuccess: (result) => {
      queryClient.setQueryData<Trade>(tradeKeys.detail(tradeId), (current) =>
        current ? merge(current, result) : current,
      )
      void queryClient.invalidateQueries({ queryKey: tradeKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
      void queryClient.invalidateQueries({ queryKey: materialKeys.all })
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.all })
    },

    onError: (error) => {
      if (error.code !== null && STALE_TRADE_ERROR_CODES.includes(error.code)) {
        void queryClient.invalidateQueries({ queryKey: tradeKeys.detail(tradeId) })
        void queryClient.invalidateQueries({ queryKey: tradeKeys.lists() })
      }
    },
  })
}

function merge(current: Trade, result: TradeReceiptResult): Trade {
  return {
    ...current,
    status: result.status,
    receivedAt: result.receivedAt,
    buyerSignedAt: result.buyerSignedAt,
    completedAt: result.completedAt,
    shipping: current.shipping
      ? { ...current.shipping, deliveredAt: result.receivedAt }
      : current.shipping,
  }
}
