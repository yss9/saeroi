import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { createListing } from '../api/createListing.ts'
import { marketplaceKeys } from '../api/marketplaceKeys.ts'
import type { CreateListingRequest, Listing } from '../types/marketplace.types.ts'

/**
 * 판매 등록 (Supplier Market).
 *
 * 등록이 성공하면 소재의 가용 수량이 줄어든다 — MARKETPLACE Lock 이 생기기 때문이다.
 * 그래서 목록뿐 아니라 **Material 재고 캐시도 무효화**해야 한다.
 *
 * TODO(2번/Material 담당과 협의): 재고 조회 훅이 나오면 그 Query Key 도 함께 무효화한다.
 * 지금은 Marketplace 캐시만 정리한다.
 */
export function useCreateListing() {
  const queryClient = useQueryClient()

  return useMutation<Listing, AppError, CreateListingRequest>({
    mutationFn: createListing,
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.lists() })
      queryClient.setQueryData(marketplaceKeys.detail(created.listingId), created)
    },
  })
}
