import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useBlockchainTransaction } from '../../../hooks/useBlockchainTransaction.ts'
import { cancelListing } from '../services/marketplaceContractService.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { marketplaceKeys } from '../api/marketplaceKeys.ts'

/**
 * 판매 글 내리기의 온체인 서명.
 *
 * <b>이것이 없으면 같은 소재를 다시 팔 수 없다.</b> 판매 등록은 체인에서 소재 잔액을
 * 잠그고({@code createListing} → {@code MaterialToken} 의 lock), 그 잠금을 푸는 것은
 * {@code cancelListing} 뿐이다. 오프체인 취소만 하면 DB 의 잠금은 풀리는데 체인의 잠금은
 * 영원히 남아, 재등록이 {@code Marketplace: insufficient available balance} 로 막힌다.
 *
 * 실제로 그렇게 됐다 — 판매글 #1 이 tokenId 4 의 잔액 12000 전부를 잠근 채 ACTIVE 로
 * 남아 있고, 그 소재는 다시 팔 수 없다.
 *
 * {@code markShipped}·{@code confirmReceipt} 와 같은 모양이다. 새 번호를 채번하지 않고
 * 이미 있는 판매 글의 상태만 바꾸므로 서버에 알릴 것이 없다 —
 * {@code useOnChainLink} 가 아니라 트랜잭션만 보낸다.
 */
export function useListingChainAction() {
  const queryClient = useQueryClient()

  const transaction = useBlockchainTransaction({
    onConfirmed: () => {
      // 잠금이 풀리면 그 소재의 가용 수량이 늘어난다. 보유 소재를 비우지 않으면
      // 되살아난 수량이 화면에 안 보여서 사용자는 여전히 못 파는 줄 안다.
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
    },
  })

  const cancel = useCallback(
    (chainListingId: number) => transaction.execute(() => cancelListing(chainListingId)),
    [transaction],
  )

  return { ...transaction, cancel }
}
