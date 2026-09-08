import { useMutation, useQueryClient } from '@tanstack/react-query'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import type { AppError } from '../../../utils/errors.ts'
import { createListing } from '../api/createListing.ts'
import { marketplaceKeys } from '../api/marketplaceKeys.ts'
import type { CreateListingRequest, Listing } from '../types/marketplace.types.ts'

/**
 * 판매 등록 (Supplier Market).
 *
 * 등록이 성공하면 소재의 가용 수량이 줄어든다 — MARKETPLACE Lock 이 생기기 때문이다.
 * 그래서 목록뿐 아니라 **보유 소재 캐시도 무효화**한다. 빠뜨리면 사용자가 소재 화면으로
 * 돌아갔을 때 이미 잠긴 수량이 아직 쓸 수 있는 것처럼 보이고, 그 값으로 다시 등록하면
 * 서버가 409 로 거절한다.
 *
 * 도메인이 달라도(marketplace → inventory) 무효화는 필요하다. Feature 를 나눈 것은
 * 코드 배치의 문제이고, 캐시 일관성은 그 경계를 따르지 않는다.
 */
export function useCreateListing() {
  const queryClient = useQueryClient()

  return useMutation<Listing, AppError, CreateListingRequest>({
    mutationFn: createListing,
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
      queryClient.setQueryData(marketplaceKeys.detail(created.listingId), created)
    },
  })
}
