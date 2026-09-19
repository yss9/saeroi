export const RECENTLY_VIEWED_PRODUCTS_LIMIT = 8

export function recentlyViewedProductsStorageKey(userId: number): string {
  return `recently-viewed:${userId}`
}

/**
 * The history is deliberately browser-local and contains IDs only. Reading can
 * throw when storage is disabled, so callers always receive a usable array.
 */
export function readRecentlyViewedProductIds(userId: number): number[] {
  if (!Number.isSafeInteger(userId) || userId <= 0) return []
  try {
    const raw = window.localStorage.getItem(recentlyViewedProductsStorageKey(userId))
    if (!raw) return []

    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []

    const seen = new Set<number>()
    return parsed
      .filter((value): value is number => Number.isSafeInteger(value) && value > 0)
      .filter((id) => {
        if (seen.has(id)) return false
        seen.add(id)
        return true
      })
      .slice(0, RECENTLY_VIEWED_PRODUCTS_LIMIT)
  } catch {
    return []
  }
}

/** Adds a successfully loaded product to the front without duplicates. */
export function recordRecentlyViewedProduct(userId: number, productId: number): void {
  if (!Number.isSafeInteger(userId) || userId <= 0 || !Number.isSafeInteger(productId) || productId <= 0) return

  const next = [
    productId,
    ...readRecentlyViewedProductIds(userId).filter((id) => id !== productId),
  ].slice(0, RECENTLY_VIEWED_PRODUCTS_LIMIT)

  try {
    window.localStorage.setItem(recentlyViewedProductsStorageKey(userId), JSON.stringify(next))
  } catch {
    // Storage may be unavailable in private or restricted browsing contexts.
  }
}

/** Removes products that are no longer publicly available. */
export function removeRecentlyViewedProductIds(userId: number, productIds: readonly number[]): void {
  if (productIds.length === 0) return
  const removed = new Set(productIds)
  const next = readRecentlyViewedProductIds(userId).filter((id) => !removed.has(id))

  try {
    window.localStorage.setItem(recentlyViewedProductsStorageKey(userId), JSON.stringify(next))
  } catch {
    // The list still renders normally even when history cannot be persisted.
  }
}
