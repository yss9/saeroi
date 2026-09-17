import { useConsumerAddresses } from './useConsumerAddresses.ts'
import { toAddressSummary } from './useConsumerMyPage.ts'
import type { ConsumerMyPageData, ConsumerProfileSummary } from '../types/consumerMyPage.types.ts'

/** Profile needs the authenticated identity and address book, not order availability. */
export function useConsumerProfile(profile: ConsumerProfileSummary | null) {
  const addresses = useConsumerAddresses()
  const data: ConsumerMyPageData | null = profile ? {
    profile,
    defaultAddress: toAddressSummary(addresses.data?.find((address) => address.isDefault)),
    orderStages: [],
    recentOrders: [],
  } : null
  return { data, isPending: addresses.isPending, isError: false, error: null, refetch: addresses.refetch }
}
