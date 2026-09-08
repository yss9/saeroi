import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { shipTrade } from '../api/shipTrade.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import {
  STALE_TRADE_ERROR_CODES,
  type ShipTradeRequest,
  type Trade,
  type TradeShipResult,
} from '../types/trade.types.ts'

/**
 * 판매자 발송 처리.
 *
 * 응답이 `Trade` 가 아니라 바뀐 값만 담은 `TradeShipResult` 라서
 * `setQueryData(detail, result)` 로 덮으면 **상세 캐시가 반쪽짜리 거래로 바뀐다**
 * (소재 이름·금액·지갑 주소가 사라진다). 그래서 필드를 병합한다.
 *
 * 병합하는 값은 서버가 실제로 돌려준 것뿐이다 — 배송지(수취인·주소)는 응답에 없으므로
 * 지어내지 않는다. 송장 번호는 `mutation.data` 로 화면에 바로 보여주고, 새로고침 후에도
 * 남는 것은 `TradeResponse` 에 `shipping` 이 추가된 뒤부터다.
 */
export function useShipTrade(tradeId: number) {
  const queryClient = useQueryClient()

  return useMutation<TradeShipResult, AppError, ShipTradeRequest>({
    mutationFn: (payload) => shipTrade(tradeId, payload),

    onSuccess: (result) => {
      queryClient.setQueryData<Trade>(tradeKeys.detail(tradeId), (current) =>
        current ? merge(current, result) : current,
      )
      // 목록의 상태 배지와 "발송 필요" 표시가 그대로면 판매자가 같은 건을 다시 누른다.
      void queryClient.invalidateQueries({ queryKey: tradeKeys.lists() })
    },

    onError: (error) => {
      // "지금 상태로는 안 된다" 는 거절은 화면이 낡았다는 신호다. 사유만 띄우면
      // 사용자가 같은 버튼을 계속 누르므로, 거래를 다시 읽어 현재 상태로 맞춘다.
      if (error.code !== null && STALE_TRADE_ERROR_CODES.includes(error.code)) {
        void queryClient.invalidateQueries({ queryKey: tradeKeys.detail(tradeId) })
        void queryClient.invalidateQueries({ queryKey: tradeKeys.lists() })
      }
    },
  })
}

function merge(current: Trade, result: TradeShipResult): Trade {
  return {
    ...current,
    status: result.status,
    shippedAt: result.shippedAt,
    // 배송 정보가 이미 있을 때만 손댄다. 없는 상태에서 만들어 내면 수취인·주소가 빈
    // 배송지가 생기고, 화면은 그것을 "배송지 있음" 으로 읽는다.
    shipping: current.shipping
      ? {
          ...current.shipping,
          carrier: result.carrier,
          trackingNumber: result.trackingNumber,
          shippedAt: result.shippedAt,
        }
      : current.shipping,
  }
}
