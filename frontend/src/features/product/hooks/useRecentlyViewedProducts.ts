import { useQueries } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { getPublicProductModel } from '../api/getPublicProductModel.ts'
import { publicProductKeys } from '../api/publicProductKeys.ts'
import {
  readRecentlyViewedProductIds,
  removeRecentlyViewedProductIds,
} from '../utils/recentlyViewedProducts.ts'
import { isAppError } from '../../../utils/errors.ts'

/** Loads browser history through the same detail cache used by product pages. */
export function useRecentlyViewedProducts() {
  const [productIds] = useState(readRecentlyViewedProductIds)
  const queries = useQueries({
    queries: productIds.map((productId) => ({
      queryKey: [...publicProductKeys.all, 'detail', productId],
      queryFn: async () => {
        try {
          return (await getPublicProductModel(productId)) ?? null
        } catch (error) {
          if (isAppError(error) && error.status === 404) return null
          throw error
        }
      },
      retry: (count: number, error: unknown) =>
        !(isAppError(error) && error.status === 404) && count < 1,
    })),
  })

  const missingIds = productIds.filter(
    (_productId, index) => queries[index]?.isSuccess && queries[index]?.data === null,
  )
  const missingIdKey = missingIds.join(',')

  useEffect(() => {
    if (!missingIdKey) return
    const ids = missingIdKey.split(',').map(Number)
    removeRecentlyViewedProductIds(ids)
  }, [missingIdKey])

  // useQueries follows input order, but map by the saved IDs explicitly so this
  // remains true even when requests finish in a different order.
  const products = productIds.flatMap((productId, index) => {
    const product = queries[index]?.data
    return product?.productModelId === productId ? [product] : []
  })
  const failedQuery = queries.find((query) => query.isError)

  return {
    products,
    isPending: productIds.length > 0 && queries.some((query) => query.isPending),
    isError: failedQuery !== undefined,
    error: failedQuery?.error ?? null,
    refetch: () => Promise.all(queries.map((query) => query.refetch())),
  }
}
