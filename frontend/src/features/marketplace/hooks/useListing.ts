import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getListing } from '../api/getListing.ts'
import { marketplaceKeys } from '../api/marketplaceKeys.ts'
import type { ListingDetail } from '../types/marketplace.types.ts'

export function useListing(listingId: number | null) {
  return useQuery<ListingDetail, AppError>({
    queryKey: marketplaceKeys.detail(listingId ?? 0),
    queryFn: () => getListing(listingId as number),
    enabled: listingId !== null,
    staleTime: 5_000,
  })
}
