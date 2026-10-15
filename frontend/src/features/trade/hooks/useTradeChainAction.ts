import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useBlockchainTransaction } from '../../../hooks/useBlockchainTransaction.ts'
import {
  cancelOrder,
  confirmReceipt,
  markShipped,
} from '../../marketplace/services/marketplaceContractService.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import { tradeKeys } from '../api/tradeKeys.ts'

/**
 * 발송·수령 확인의 온체인 서명.
 *
 * **앞의 두 단계와 다르다.** `markShipped` 와 `confirmReceipt` 는 새 번호를 채번하지 않는다 —
 * 이미 있는 주문의 상태를 바꿀 뿐이라 `useOnChainLink` 의 모양(이벤트에서 id 를 꺼내
 * 알린다)에 맞지 않는다.
 *
 * **수령은 서버에 따로 알리지 않는다.** 뒤이어 부르는 수령 확정(`POST /receive/confirm`)이
 * 체인을 읽어 수령이 남았는지 확인하고 그때 `buyer_signed_at` 까지 채운다. 순서가 서명
 * 먼저이므로 보고할 것이 없다 — 예전에는 반대 순서라 수령 확인이 서명 없이 그 값을
 * 채우고 있었고, 지갑 서명을 하지 않은 거래가 "검증됨" 으로 보였다.
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
      // **소재 쪽도 함께 비운다.** confirmReceipt 는 ERC1155 잔액을 판매자에서 구매자로
      // 옮기므로 소재 상세와 보유자별 잔량(useMaterialBalances)이 함께 낡는다. 보유 목록만
      // 비우면 소재 상세를 열어 둔 사용자는 옛 잔량을 계속 본다 — 서버 쪽 수령 확인
      // (useConfirmTradeReceipt)이 두 캐시를 함께 비우는 것과 같은 이유다.
      void queryClient.invalidateQueries({ queryKey: materialKeys.all })
      // 방금 바꾼 것이 바로 체인 주문 상태다. 이걸 비우지 않으면
      // TradeChainActionPanel 이 "아직 밀렸다" 를 계속 보여준다 —
      // staleTime 이 지날 때까지 사용자는 방금 한 서명이 반영되지 않았다고 읽는다.
      // 어느 주문이었는지 훅이 모르므로 접두사로 비운다.
      void queryClient.invalidateQueries({ queryKey: ['chain', 'order'] })
    },
  })

  const ship = useCallback(
    (chainTradeId: number) => transaction.execute(
      (operationId) => markShipped(chainTradeId, operationId),
      { contract: 'MARKETPLACE', functionName: 'markShipped', referenceType: 'TRADE', referenceId: tradeId },
    ),
    [transaction],
  )

  const receive = useCallback(
    async (chainTradeId: number) => {
      const receipt = await transaction.execute(
        (operationId) => confirmReceipt(chainTradeId, operationId),
        { contract: 'MARKETPLACE', functionName: 'confirmReceipt', referenceType: 'TRADE', referenceId: tradeId },
      )
      // 서버에 따로 알리지 않는다. 뒤이어 부르는 수령 확정이 체인을 읽어 확인하고 그때
      // 서명 시각까지 채운다 — 같은 일을 두 곳에서 하면 어긋나는 순간 정본을 알 수 없다.
      void queryClient.invalidateQueries({ queryKey: tradeKeys.detail(tradeId) })
      return receipt
    },
    [queryClient, tradeId, transaction],
  )

  /**
   * 주문 취소.
   *
   * <b>이것이 없으면 남은 수량을 전부 사려는 다음 사람이 막힌다.</b> `createOrder` 는
   * 판매 글의 `remainingQuantity` 를 깎고 `reservedQuantity` 로 옮긴다. 오프체인
   * 거래만 취소하면 서버는 수량을 판매 글에 되돌리는데(`PaymentService.restoreListingQuantity`)
   * 체인은 그대로라, 두 값이 어긋난다.
   *
   * 그 뒤 구매자가 화면에 보이는 잔량(서버 기준)을 전부 사면 서버는 통과시키지만
   * 체인이 `Marketplace: insufficient listing quantity` 로 되돌린다. 실제로 판매글 #2 가
   * 그 상태다 — 서버 기준 잔량과 체인의 remainingQuantity 가 5000g 어긋나 있다.
   *
   * 컨트랙트는 주문이 CREATED 일 때만, 그리고 구매자나 판매자만 취소를 허용한다
   * (Marketplace.sol:279).
   */
  const cancel = useCallback(
    (chainTradeId: number) => transaction.execute(
      (operationId) => cancelOrder(chainTradeId, operationId),
      { contract: 'MARKETPLACE', functionName: 'cancelOrder', referenceType: 'TRADE', referenceId: tradeId },
    ),
    [transaction],
  )

  return { ...transaction, ship, receive, cancel }
}
