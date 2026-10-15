import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { linkListingOnChain } from '../../../services/blockchain/chainLinkApi.ts'
import { useOnChainLink } from '../../../hooks/useOnChainLink.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { createListing } from '../services/marketplaceContractService.ts'
import { marketplaceKeys } from '../api/marketplaceKeys.ts'

/**
 * 판매 글을 온체인에도 등록한다.
 *
 * <b>서버 등록이 먼저 끝난 뒤에 부른다.</b> 순서를 뒤집으면 체인에는 판매 글이 있는데
 * 우리 DB 에는 없는 상태가 되고, 그 판매 글이 잠근 수량은 아무도 풀 수 없다.
 *
 * ⚠️ **`materialId` 는 우리 DB 의 id 가 아니라 온체인 `tokenId` 다.** 컨트랙트는 우리
 * 테이블을 모른다. 그래서 소재가 아직 발행되지 않았으면(`tokenId === null`) 부를 인자
 * 자체가 없고, 호출부가 그 경우를 먼저 걸러야 한다.
 */
export function useListingOnChain() {
  const queryClient = useQueryClient()

  const link = useOnChainLink({
    contract: 'marketplace',
    eventName: 'ListingCreated',
    argName: 'listingId',
    operationContract: 'MARKETPLACE',
    functionName: 'createListing',
    referenceType: 'TRADE',
    link: linkListingOnChain,
    onLinked: () => {
      // 온체인 번호가 붙으면 목록·상세의 배지가 바뀐다. 보유 소재도 함께 무효화하는
      // 이유는 체인 쪽 Lock 이 생겨 available 이 달라지기 때문이다.
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
    },
  })

  const register = useCallback(
    (listingId: number, tokenId: number, quantityGram: number) =>
      link.run(listingId, (operationId) => createListing(tokenId, quantityGram, operationId)),
    [link],
  )

  return { ...link, register }
}
