export const RECENTLY_VIEWED_PRODUCTS_STORAGE_KEY = 'saeroi.recently-viewed-product-ids'
export const RECENTLY_VIEWED_PRODUCTS_LIMIT = 8

/**
 * The history is deliberately browser-local and contains IDs only. Reading can
 * throw when storage is disabled, so callers always receive a usable array.
 */
export function readRecentlyViewedProductIds(): number[] {
  try {
    const raw = window.localStorage.getItem(RECENTLY_VIEWED_PRODUCTS_STORAGE_KEY)
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
export function recordRecentlyViewedProduct(productId: number): void {
  if (!Number.isSafeInteger(productId) || productId <= 0) return

  const next = [
    productId,
    ...readRecentlyViewedProductIds().filter((id) => id !== productId),
  ].slice(0, RECENTLY_VIEWED_PRODUCTS_LIMIT)

  try {
    window.localStorage.setItem(RECENTLY_VIEWED_PRODUCTS_STORAGE_KEY, JSON.stringify(next))
  } catch {
    // Storage may be unavailable in private or restricted browsing contexts.
  }
}

/** Removes products that are no longer publicly available. */
export function removeRecentlyViewedProductIds(productIds: readonly number[]): void {
  if (productIds.length === 0) return
  const removed = new Set(productIds)
  const next = readRecentlyViewedProductIds().filter((id) => !removed.has(id))

  try {
    window.localStorage.setItem(RECENTLY_VIEWED_PRODUCTS_STORAGE_KEY, JSON.stringify(next))
  } catch {
    // The list still renders normally even when history cannot be persisted.
  }
}
