import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { cancelListing } from '../api/cancelListing.ts'
import { marketplaceKeys } from '../api/marketplaceKeys.ts'
import type { ListingDetail } from '../types/marketplace.types.ts'

export interface CancelListingVariables {
  listingId: number
  reason?: string
}

export function useCancelListing() {
  const queryClient = useQueryClient()

  return useMutation<ListingDetail, AppError, CancelListingVariables>({
    mutationFn: ({ listingId, reason }) => cancelListing(listingId, reason),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: marketplaceKeys.lists() })
      queryClient.setQueryData(marketplaceKeys.detail(updated.id), updated)
    },
  })
}
