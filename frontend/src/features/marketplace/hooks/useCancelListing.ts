import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { cancelListing } from '../api/cancelListing.ts'
import { marketplaceKeys } from '../api/marketplaceKeys.ts'
import type { Listing } from '../types/marketplace.types.ts'

/**
 * 판매 취소.
 *
 * 취소하면 남은 수량의 잠금이 풀려 소재의 가용 수량이 늘어난다. 목록 캐시를 비우는 것만으로는
 * 부족하고 Material 재고 캐시도 함께 무효화해야 하지만, 재고 조회 훅이 아직 없다
 * (useCreateListing 의 TODO 와 같은 건이다).
 *
 * 취소 사유는 보내지 않는다 — `material_listing` 에 사유 컬럼이 없다.
 */
export function useCancelListing() {
  const queryClient = useQueryClient()

  return useMutation<Listing, AppError, number>({
    mutationFn: (listingId) => cancelListing(listingId),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.lists() })
      queryClient.setQueryData(marketplaceKeys.detail(updated.listingId), updated)
    },
  })
}
