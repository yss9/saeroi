import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { marketplaceKeys } from '../../marketplace/api/marketplaceKeys.ts'
import { createTrade } from '../api/createTrade.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import type { CreateTradeRequest, TradeDetail } from '../types/trade.types.ts'

/**
 * 구매 요청.
 *
 * ⚠️ 성공하면 **Marketplace 캐시도 무효화**해야 한다. 구매한 수량만큼 판매 가능
 * 수량이 줄었는데 목록이 그대로면, 사용자는 이미 없는 수량을 다시 사려 한다.
 *
 * 도메인이 달라도(trade → marketplace) 무효화는 필요하다. Feature 를 나눈 것은
 * 코드 배치의 문제이고, 캐시 일관성은 그 경계를 따르지 않는다.
 */
export function useCreateTrade() {
  const queryClient = useQueryClient()

  return useMutation<TradeDetail, AppError, CreateTradeRequest>({
    mutationFn: createTrade,
    onSuccess: (trade) => {
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.all })
      void queryClient.invalidateQueries({ queryKey: tradeKeys.lists() })
      queryClient.setQueryData(tradeKeys.detail(trade.id), trade)
    },
  })
}
