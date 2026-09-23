import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { linkTradeOnChain } from '../../../services/blockchain/chainLinkApi.ts'
import { useOnChainLink } from '../../../hooks/useOnChainLink.ts'
import {
  createOrder,
  getListing,
} from '../../marketplace/services/marketplaceContractService.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { ERROR_KIND, createAppError } from '../../../utils/errors.ts'
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
      link.run(tradeId, async () => {
        // **서명 전에 체인의 잔량을 확인한다.**
        //
        // 서버 잔량과 체인 잔량은 어긋날 수 있다. 오프체인에서 취소된 거래는 서버가
        // 판매 글에 수량을 되돌리지만, 체인의 주문은 cancelOrder 를 서명해야만 풀린다.
        // 그 서명을 놓친 거래가 있으면 체인의 remainingQuantity 만 작게 남는다.
        //
        // 그 상태에서 화면에 보이는 잔량(서버 기준)을 전부 사면 서버는 통과시키고
        // 체인만 `Marketplace: insufficient listing quantity` 로 되돌린다. 사용자에게는
        // "재고가 부족" 이라는 영문 revert 만 보이고, 방금 판매글에서 본 숫자와 달라
        // 화면이 고장 난 것처럼 읽힌다.
        //
        // 미리 읽어 두면 두 숫자를 나란히 보여줄 수 있다. 읽기는 가스가 들지 않는다.
        const onChain = await getListing(chainListingId)
        if (onChain.remainingQuantity < BigInt(quantityGram)) {
          throw createAppError(ERROR_KIND.TRANSACTION_FAILED, {
            message:
              `판매 가능한 수량이 ${formatKg(onChain.remainingQuantity)} 뿐이라 ` +
              `${formatKg(quantityGram)} 를 주문할 수 없습니다. ` +
              '앞선 취소 거래의 수량 정리가 완료되지 않았습니다. ' +
              '수량을 줄이거나, 판매자에게 문의해 주세요.',
          })
        }
        return createOrder(chainListingId, quantityGram)
      }),
    [link],
  )

  return { ...link, order }
}
