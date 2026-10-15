import { useMutation, useQueryClient } from '@tanstack/react-query'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import type { AppError } from '../../../utils/errors.ts'
import { cancelListing } from '../api/cancelListing.ts'
import { marketplaceKeys } from '../api/marketplaceKeys.ts'
import type { Listing } from '../types/marketplace.types.ts'

/**
 * 판매 취소.
 *
 * 취소하면 남은 수량의 잠금이 풀려 소재의 가용 수량이 **늘어난다.** 그래서 보유 소재
 * 캐시도 함께 무효화한다 — 빠뜨리면 되살아난 수량이 화면에 안 보여서 사용자가 다시
 * 팔 수 있는 줄 모른다.
 *
 * 취소 사유는 보내지 않는다 — `material_listing` 에 사유 컬럼이 없다.
 */
export function useCancelListing() {
  const queryClient = useQueryClient()

  return useMutation<Listing, AppError, number>({
    mutationFn: (listingId) => cancelListing(listingId),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
      queryClient.setQueryData(marketplaceKeys.detail(updated.listingId), updated)
    },
  })
}
