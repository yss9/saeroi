import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { PublicProductDetail } from '../types/publicProduct.types.ts'
import {
  readRecentlyViewedProductIds,
  recentlyViewedProductsStorageKey,
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
    window.localStorage.setItem(recentlyViewedProductsStorageKey(10), JSON.stringify([3, 1, 2]))
    productApi.getPublicProductModel.mockImplementation((id: number) =>
      new Promise<PublicProductDetail>((resolve) => {
        window.setTimeout(() => resolve(product(id)), id === 3 ? 20 : id === 1 ? 10 : 1)
      }),
    )

    const { result } = renderHook(() => useRecentlyViewedProducts(10), { wrapper })

    await waitFor(() => expect(result.current.isPending).toBe(false))
    expect(result.current.products.map((item) => item.productModelId)).toEqual([3, 1, 2])
  })

  it('silently removes a product that is no longer publicly available', async () => {
    window.localStorage.setItem(recentlyViewedProductsStorageKey(10), JSON.stringify([3, 2]))
    productApi.getPublicProductModel.mockImplementation((id: number) => {
      if (id === 3) return Promise.reject(createAppError(ERROR_KIND.API_ERROR, { status: 404 }))
      return Promise.resolve(product(id))
    })

    const { result } = renderHook(() => useRecentlyViewedProducts(10), { wrapper })

    await waitFor(() => expect(result.current.products.map((item) => item.productModelId)).toEqual([2]))
    expect(result.current.isError).toBe(false)
    expect(readRecentlyViewedProductIds(10)).toEqual([2])
  })

  it('switches histories with the signed-in account and restores the original account history', async () => {
    window.localStorage.setItem(recentlyViewedProductsStorageKey(10), JSON.stringify([31]))
    window.localStorage.setItem(recentlyViewedProductsStorageKey(20), JSON.stringify([42]))
    productApi.getPublicProductModel.mockImplementation((id: number) => Promise.resolve(product(id)))

    const { result, rerender } = renderHook(
      ({ userId }: { userId: number | null }) => useRecentlyViewedProducts(userId),
      { wrapper, initialProps: { userId: 10 as number | null } },
    )

    await waitFor(() => expect(result.current.products.map((item) => item.productModelId)).toEqual([31]))
    rerender({ userId: 20 })
    await waitFor(() => expect(result.current.products.map((item) => item.productModelId)).toEqual([42]))
    rerender({ userId: null })
    expect(result.current.products).toEqual([])
    rerender({ userId: 10 })
    await waitFor(() => expect(result.current.products.map((item) => item.productModelId)).toEqual([31]))
  })
})
