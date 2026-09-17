import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PublicProductModel } from '../../features/product/types/publicProduct.types.ts'
import { ShopProvider } from '../../features/shop/ShopContext.tsx'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import type { PageResponse } from '../../types/api.types.ts'
import { ShopPage } from './ShopPage.tsx'

const product: PublicProductModel = {
  productModelId: 1, name: '테스트 데님 백', brandName: '테스트 브랜드', brandId: 2,
  category: 'BAG', subcategory: 'TOTE', price: 129000, description: '테스트 상품',
  thumbnailUrl: '/api/v1/public/product-models/1/thumbnail', status: 'ON_SALE', stockQuantity: 0, createdAt: '2026-09-17',
}
const catalog: PageResponse<PublicProductModel> = { content: [product], page: 0, size: 12, totalElements: 1, totalPages: 1, hasNext: false }

function Location() { const location = useLocation(); return <output aria-label="현재 위치">{location.pathname}{location.search}</output> }
function show(path = '/shop') {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <ShopProvider><Routes>
          {['/shop', '/shop/:category', '/shop/:category/:subcategory'].map((route) => <Route key={route} path={route} element={<ShopPage />} />)}
        </Routes><Location /></ShopProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}
beforeEach(() => {
  useAuthStore.getState().signOut()
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: catalog } as AxiosResponse)
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })

describe('consumer SHOP', () => {
  it('loads ALL without auth, renders stock-based sold-out cards and hides one-page pagination', async () => {
    show()
    const card = await screen.findByRole('article', { name: '테스트 데님 백 상품' })
    expect(apiClient.get).toHaveBeenCalledWith('/v1/public/product-models', {
      params: { category: undefined, subcategory: undefined, sort: 'LATEST', page: 0, size: 12 }, skipAuth: true, skipTokenRefresh: true,
    })
    expect(within(card).getByText('품절')).toBeInTheDocument()
    expect(within(card).getByText('₩ 129,000')).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: '테스트 데님 백 상세 보기' })).toHaveAttribute('href', '/products/1')
    expect(within(card).getByRole('link', { name: '테스트 브랜드' })).toHaveAttribute('href', '/brands/2')
    expect(card.querySelector('a a')).toBeNull()
    expect(within(card).getByRole('img')).toHaveAttribute('src', '/api/v1/public/product-models/1/thumbnail')
    expect(screen.queryByRole('navigation', { name: '상품 페이지' })).not.toBeInTheDocument()
    expect(screen.queryByText('ETC')).not.toBeInTheDocument()
    expect(within(screen.getByRole('navigation', { name: '상품 카테고리' })).getByRole('link', { name: 'ALL' })).toHaveAttribute('aria-current', 'page')
    fireEvent.error(within(card).getByRole('img'))
    expect(within(card).getByText('이미지 준비 중')).toBeInTheDocument()
  })

  it('resets page on category and sort changes and maps URL enums correctly', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { ...catalog, page: 1, totalElements: 20, totalPages: 2 } } as AxiosResponse)
    show('/shop/clothing/top?page=1&sort=PRICE_DESC')
    await screen.findByRole('article', { name: '테스트 데님 백 상품' })
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/product-models', expect.objectContaining({ params: { category: 'CLOTHING', subcategory: 'TOP', sort: 'PRICE_DESC', page: 1, size: 12 } }))
    fireEvent.change(screen.getByRole('combobox', { name: '정렬' }), { target: { value: 'PRICE_ASC' } })
    await waitFor(() => expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/product-models', expect.objectContaining({ params: { category: 'CLOTHING', subcategory: 'TOP', sort: 'PRICE_ASC', page: 0, size: 12 } })))
    fireEvent.click(within(screen.getByRole('navigation', { name: '상품 하위 카테고리' })).getByRole('link', { name: 'OUTER' }))
    await waitFor(() => expect(screen.getByLabelText('현재 위치')).toHaveTextContent('/shop/clothing/outer?sort=PRICE_ASC'))
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/product-models', expect.objectContaining({ params: { category: 'CLOTHING', subcategory: 'OUTER', sort: 'PRICE_ASC', page: 0, size: 12 } }))
  })

  it('requests 0-based pagination and exposes boundary controls for 48 products', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (_url, config) => {
      const page = (config?.params as { page?: number } | undefined)?.page ?? 0
      return { data: { ...catalog, page, totalElements: 48, totalPages: 4, hasNext: page < 3 } } as AxiosResponse
    })
    show()
    const pagination = await screen.findByRole('navigation', { name: '상품 페이지' })
    expect(within(pagination).getByRole('button', { name: '이전 페이지' })).toBeDisabled()
    fireEvent.click(within(pagination).getByRole('button', { name: '4페이지' }))
    await waitFor(() => expect(screen.getByLabelText('현재 위치')).toHaveTextContent('page=3'))
    await waitFor(() => expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled())
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/product-models', expect.objectContaining({ params: expect.objectContaining({ page: 3, size: 12 }) }))
  })

  it('shows loading, error and successful retry states', async () => {
    let reject!: (error: Error) => void
    vi.mocked(apiClient.get).mockReturnValueOnce(new Promise((_resolve, fail) => { reject = fail }))
    show()
    expect(screen.getByText('상품을 불러오는 중')).toBeInTheDocument()
    reject(new Error('test network failure'))
    expect(await screen.findByRole('alert')).toHaveTextContent('상품을 불러오지 못했습니다')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('article', { name: '테스트 데님 백 상품' })).toBeInTheDocument()
  })

  it('shows empty results without inserting dummy products', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { ...catalog, content: [], totalElements: 0, totalPages: 0 } } as AxiosResponse)
    show('/shop/home')
    expect(await screen.findByText('판매 중인 상품이 없습니다')).toBeInTheDocument()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })

  it('uses static thumbnails without API prefix and does not mark available items as sold out', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { ...catalog, content: [{ ...product, stockQuantity: 2, thumbnailUrl: '/images/landing/product-denim-circle.webp' }] } } as AxiosResponse)
    show()
    const card = await screen.findByRole('article', { name: '테스트 데님 백 상품' })
    expect(within(card).getByRole('img')).toHaveAttribute('src', '/images/landing/product-denim-circle.webp')
    expect(within(card).queryByText('품절')).not.toBeInTheDocument()
  })

  it('offers first-page recovery when a bookmarked page no longer exists', async () => {
    show('/shop?page=8')
    expect(await screen.findByText('요청한 페이지에 상품이 없습니다')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '첫 페이지 보기' }))
    expect(await screen.findByRole('article', { name: '테스트 데님 백 상품' })).toBeInTheDocument()
    expect(screen.getByLabelText('현재 위치')).toHaveTextContent('page=0')
  })

  it('rejects invalid parent/child combinations before any API request', () => {
    show('/shop/bag/top')
    expect(screen.getByRole('heading', { name: '페이지를 찾을 수 없습니다' })).toBeInTheDocument()
    expect(apiClient.get).not.toHaveBeenCalled()
  })
})
