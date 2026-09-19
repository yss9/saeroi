import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  readRecentlyViewedProductIds,
  recentlyViewedProductsStorageKey,
  recordRecentlyViewedProduct,
} from './recentlyViewedProducts.ts'

beforeEach(() => {
  window.localStorage.clear()
  vi.restoreAllMocks()
})

describe('recently viewed product history', () => {
  it('moves an already viewed product to the front without duplicating it', () => {
    window.localStorage.setItem(recentlyViewedProductsStorageKey(10), JSON.stringify([3, 2, 1]))

    recordRecentlyViewedProduct(10, 2)

    expect(readRecentlyViewedProductIds(10)).toEqual([2, 3, 1])
  })

  it('keeps only the eight most recent product IDs', () => {
    for (let productId = 1; productId <= 9; productId += 1) {
      recordRecentlyViewedProduct(10, productId)
    }

    expect(readRecentlyViewedProductIds(10)).toEqual([9, 8, 7, 6, 5, 4, 3, 2])
  })

  it('keeps each account history isolated and preserves it across logout and login', () => {
    recordRecentlyViewedProduct(10, 31)
    recordRecentlyViewedProduct(20, 42)
    recordRecentlyViewedProduct(10, 32)

    expect(readRecentlyViewedProductIds(10)).toEqual([32, 31])
    expect(readRecentlyViewedProductIds(20)).toEqual([42])
    expect(readRecentlyViewedProductIds(10)).toEqual([32, 31])
    expect(window.localStorage.getItem('saeroi.recently-viewed-product-ids')).toBeNull()
  })

  it('treats blocked browser storage as an empty history without throwing', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked')
    })

    expect(readRecentlyViewedProductIds(10)).toEqual([])
    expect(() => recordRecentlyViewedProduct(10, 31)).not.toThrow()
  })
})
