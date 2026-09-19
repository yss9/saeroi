import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  readRecentlyViewedProductIds,
  RECENTLY_VIEWED_PRODUCTS_STORAGE_KEY,
  recordRecentlyViewedProduct,
} from './recentlyViewedProducts.ts'

beforeEach(() => {
  window.localStorage.clear()
  vi.restoreAllMocks()
})

describe('recently viewed product history', () => {
  it('moves an already viewed product to the front without duplicating it', () => {
    window.localStorage.setItem(RECENTLY_VIEWED_PRODUCTS_STORAGE_KEY, JSON.stringify([3, 2, 1]))

    recordRecentlyViewedProduct(2)

    expect(readRecentlyViewedProductIds()).toEqual([2, 3, 1])
  })

  it('keeps only the eight most recent product IDs', () => {
    for (let productId = 1; productId <= 9; productId += 1) {
      recordRecentlyViewedProduct(productId)
    }

    expect(readRecentlyViewedProductIds()).toEqual([9, 8, 7, 6, 5, 4, 3, 2])
  })

  it('treats blocked browser storage as an empty history without throwing', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked')
    })

    expect(readRecentlyViewedProductIds()).toEqual([])
    expect(() => recordRecentlyViewedProduct(31)).not.toThrow()
  })
})
