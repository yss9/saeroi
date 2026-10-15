import { describe, expect, it } from 'vitest'
import { paginationWindow, resolveShopCategory, shopCategories, shopQuery } from './catalog.ts'
import { publicProductImageUrl } from '../product/api/publicProductImageUrl.ts'

describe('SHOP URL and image contracts', () => {
  it('maps lowercase paths to enum values and rejects invalid parent/child pairs', () => {
    expect(resolveShopCategory('clothing', 'top')).toMatchObject({ category: 'CLOTHING', subcategory: 'TOP' })
    expect(resolveShopCategory('bag', 'tote')).toMatchObject({ category: 'BAG', subcategory: 'TOTE' })
    expect(resolveShopCategory()).toMatchObject({ label: 'ALL' })
    expect(resolveShopCategory()?.category).toBeUndefined()
    for (const [category, child] of [['etc', undefined], ['CLOTHING', undefined], ['bag', 'top'], ['wallet', 'tote'], ['unknown', undefined]]) {
      expect(resolveShopCategory(category, child)).toBeNull()
    }
    expect(shopCategories.map((item) => item.label)).toEqual(['ALL', 'CLOTHING', 'BAG', 'WALLET', 'ACCESSORY', 'HOME'])
  })

  it('uses 0-based pages and defaults invalid pagination/sort values', () => {
    expect(shopQuery(new URLSearchParams('page=3&sort=PRICE_DESC'))).toEqual({ page: 3, sort: 'PRICE_DESC' })
    for (const page of ['-1', '1.5', 'NaN', '9007199254740992']) {
      expect(shopQuery(new URLSearchParams(`page=${page}&sort=BAD`))).toEqual({ page: 0, sort: 'LATEST' })
    }
    expect(paginationWindow(3, 4)).toEqual([0, 1, 2, 3])
    expect(paginationWindow(90, 100)).toEqual([88, 89, 90, 91, 92])
  })

  it('resolves only API images against API base and keeps static assets on the frontend', () => {
    const apiImage = '/api/v1/public/product-models/1/thumbnail'
    expect(publicProductImageUrl(apiImage, 'https://api.example.com/api/', '/')).toBe('https://api.example.com/api/v1/public/product-models/1/thumbnail')
    expect(publicProductImageUrl(apiImage, '/test/api', '/test/')).toBe('/test/api/v1/public/product-models/1/thumbnail')
    expect(publicProductImageUrl('/images/landing/demo.webp', 'https://api.example.com/api', '/')).toBe('/images/landing/demo.webp')
    expect(publicProductImageUrl('/images/landing/demo.webp', '/api', '/test/')).toBe('/test/images/landing/demo.webp')
    expect(publicProductImageUrl('https://cdn.example.com/image.webp')).toBe('https://cdn.example.com/image.webp')
  })
})
