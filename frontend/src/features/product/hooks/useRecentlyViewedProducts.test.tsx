import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { PublicProductDetail } from '../types/publicProduct.types.ts'
import {
  readRecentlyViewedProductIds,
  RECENTLY_VIEWED_PRODUCTS_STORAGE_KEY,
} from '../utils/recentlyViewedProducts.ts'
import { useRecentlyViewedProducts } from './useRecentlyViewedProducts.ts'

const productApi = vi.hoisted(() => ({ getPublicProductModel: vi.fn() }))
vi.mock('../api/getPublicProductModel.ts', () => productApi)

function product(productModelId: number): PublicProductDetail {
  return {
    productModelId,
    name: `상품 ${productModelId}`,
    category: 'BAG',
    subcategory: 'TOTE',
    brandId: 1,
    brandName: '브랜드',
    price: productModelId * 1000,
    description: '',
    thumbnailUrl: '',
    status: 'ON_SALE',
    stockQuantity: 1,
    createdAt: '2026-09-19',
    journeyUrl: null,
    images: [],
  }
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

beforeEach(() => {
  window.localStorage.clear()
  vi.clearAllMocks()
})

describe('useRecentlyViewedProducts', () => {
  it('keeps the stored order even when product requests finish out of order', async () => {
    window.localStorage.setItem(RECENTLY_VIEWED_PRODUCTS_STORAGE_KEY, JSON.stringify([3, 1, 2]))
    productApi.getPublicProductModel.mockImplementation((id: number) =>
      new Promise<PublicProductDetail>((resolve) => {
        window.setTimeout(() => resolve(product(id)), id === 3 ? 20 : id === 1 ? 10 : 1)
      }),
    )

    const { result } = renderHook(() => useRecentlyViewedProducts(), { wrapper })

    await waitFor(() => expect(result.current.isPending).toBe(false))
    expect(result.current.products.map((item) => item.productModelId)).toEqual([3, 1, 2])
  })

  it('silently removes a product that is no longer publicly available', async () => {
    window.localStorage.setItem(RECENTLY_VIEWED_PRODUCTS_STORAGE_KEY, JSON.stringify([3, 2]))
    productApi.getPublicProductModel.mockImplementation((id: number) => {
      if (id === 3) return Promise.reject(createAppError(ERROR_KIND.API_ERROR, { status: 404 }))
      return Promise.resolve(product(id))
    })

    const { result } = renderHook(() => useRecentlyViewedProducts(), { wrapper })

    await waitFor(() => expect(result.current.products.map((item) => item.productModelId)).toEqual([2]))
    expect(result.current.isError).toBe(false)
    expect(readRecentlyViewedProductIds()).toEqual([2])
  })
})
