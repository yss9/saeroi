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
/**
 * <b>{@code tradeId} 를 훅이 아니라 호출 시점에 받는다.</b> 구매 폼은 거래가 만들어지기
 * 전에 이 훅을 준비해야 하는데, 그때는 아직 번호가 없다. 훅 인자로 두면 0 같은 자리값을
 * 넘기게 되고 그러면 서명 가드에 걸린다.
 */
export function useTradeOnChain() {
  const queryClient = useQueryClient()

  const link = useOnChainLink({
    contract: 'marketplace',
    eventName: 'OrderCreated',
    argName: 'orderId',
    link: linkTradeOnChain,
    onLinked: () => {
      // 어느 거래인지 모르므로 도메인 전체를 무효화한다. 거래 목록·상세가 함께 갱신된다.
      void queryClient.invalidateQueries({ queryKey: tradeKeys.all })
    },
  })

  const order = useCallback(
    (tradeId: number, chainListingId: number, quantityGram: number) =>
      link.run(tradeId, () => createOrder(chainListingId, quantityGram)),
    [link],
  )

  return { ...link, order }
}
