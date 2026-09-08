import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { linkTradeOnChain } from '../../../services/blockchain/chainLinkApi.ts'
import { useOnChainLink } from '../../../hooks/useOnChainLink.ts'
import { createOrder } from '../../marketplace/services/marketplaceContractService.ts'
import { tradeKeys } from '../api/tradeKeys.ts'

/**
 * 거래를 온체인 주문으로 만든다.
 *
 * **판매 글이 먼저 온체인에 등록되어 있어야 한다.** 컨트랙트의 `createOrder` 첫 인자가
 * 온체인 `listingId` 이고, 그것은 판매자가 서명해야 생긴다. 그래서 구매자 혼자서는
 * 이 단계를 시작할 수 없다 — 화면이 그 사실을 먼저 알려야 한다.
 *
 * 이 번호가 있어야 발송·수령 확인을 온체인에서 부를 수 있다
 * (`markShipped(orderId)` · `confirmReceipt(orderId)`).
 */
export function useTradeOnChain(tradeId: number) {
  const queryClient = useQueryClient()

  const link = useOnChainLink({
    contract: 'marketplace',
    eventName: 'OrderCreated',
    argName: 'orderId',
    link: linkTradeOnChain,
    onLinked: () => {
      void queryClient.invalidateQueries({ queryKey: tradeKeys.detail(tradeId) })
    },
  })

  const order = useCallback(
    (chainListingId: number, quantityGram: number) =>
      link.run(tradeId, () => createOrder(chainListingId, quantityGram)),
    [link, tradeId],
  )

  return { ...link, order }
}
