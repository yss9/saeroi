import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useBlockchainTransaction } from '../../../hooks/useBlockchainTransaction.ts'
import { confirmReceipt, markShipped } from '../../marketplace/services/marketplaceContractService.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { tradeKeys } from '../api/tradeKeys.ts'

/**
 * 발송·수령 확인의 온체인 서명.
 *
 * **앞의 두 단계와 다르다.** `markShipped` 와 `confirmReceipt` 는 새 번호를 채번하지 않는다 —
 * 이미 있는 주문의 상태를 바꿀 뿐이다. 그래서 서버에 알릴 것이 없고
 * ({@link useOnChainLink} 가 필요 없다) 트랜잭션만 보내면 된다.
 *
 * **`confirmReceipt` 가 이 서비스에서 소유권이 실제로 옮겨가는 유일한 자리다.**
 * 컨트랙트가 ERC1155 잔액을 판매자에서 구매자로 이동시킨다. 오프체인의
 * `POST /receive/confirm` 이 DB 보유량을 옮기는 것과 짝을 이룬다.
 */
export function useTradeChainAction(tradeId: number) {
  const queryClient = useQueryClient()

  const transaction = useBlockchainTransaction({
    onConfirmed: () => {
      void queryClient.invalidateQueries({ queryKey: tradeKeys.detail(tradeId) })
      // 수령 확인이면 양쪽 보유량이 바뀐다. 발송이면 안 바뀌지만, 두 경우를 나눠
      // 무효화하면 어느 쪽을 불렀는지 훅이 알아야 해서 의존이 늘어난다.
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
    },
  })

  const ship = useCallback(
    (chainTradeId: number) => transaction.execute(() => markShipped(chainTradeId)),
    [transaction],
  )

  const receive = useCallback(
    (chainTradeId: number) => transaction.execute(() => confirmReceipt(chainTradeId)),
    [transaction],
  )

  return { ...transaction, ship, receive }
}
