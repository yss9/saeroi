import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PublicProductModel } from '../../features/product/types/publicProduct.types.ts'
import { ShopProvider } from '../../features/shop/ShopContext.tsx'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import type { PageResponse } from '../../types/api.types.ts'
import { SearchPage } from './SearchPage.tsx'

const product: PublicProductModel = { productModelId: 31, name: '데님 토트백', brandId: 5, brandName: 'REWEAVE', category: 'BAG', subcategory: 'TOTE', price: 129000, description: '테스트', thumbnailUrl: '/api/v1/public/product-models/31/thumbnail', status: 'ON_SALE', stockQuantity: 0, createdAt: '2026-09-17' }
const catalog: PageResponse<PublicProductModel> = { content: [product], page: 0, size: 12, totalElements: 1, totalPages: 1, hasNext: false }
function History() {
  const location = useLocation()
  const navigate = useNavigate()
  return <><output aria-label="현재 위치">{location.pathname}{location.search}</output><button onClick={() => navigate(-1)}>테스트 뒤로</button><button onClick={() => navigate(1)}>테스트 앞으로</button></>
}
function show(path = '/search') {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ShopProvider><MemoryRouter initialEntries={[path]}><Routes><Route path="/search" element={<SearchPage />} /><Route path="/products/:productId" element={<h1>공개 상품 상세</h1>} /></Routes><History /></MemoryRouter></ShopProvider></QueryClientProvider>)
}
function search(keyword: string) {
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: keyword } })
  fireEvent.submit(screen.getByRole('search'))
}
function url() { return new URL(screen.getByLabelText('현재 위치').textContent!, 'http://test') }
beforeEach(() => {
  useAuthStore.getState().signOut()
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: catalog } as AxiosResponse)
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })

describe('public product search', () => {
  it.each(['/search', '/search?q=%20%20'])('shows a useful pre-search view without requesting all products at %s', (path) => {
    show(path)
    expect(screen.getByText('EXPLORE BY CATEGORY')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: '상품명 또는 브랜드명 검색' })).toBeInTheDocument()
    expect(screen.queryByText('상품명 또는 브랜드명 검색')).not.toBeInTheDocument()
    expect(screen.queryByText('입력한 문구를 포함한 상품명·브랜드명 검색')).not.toBeInTheDocument()
    expect(screen.queryByText('어떤 새로운 쓰임을 찾고 있나요?')).not.toBeInTheDocument()
    expect(screen.queryByText('상품명이나 브랜드명으로 검색해 보세요.')).not.toBeInTheDocument()
    const categories = screen.getByRole('navigation', { name: '검색 전 카테고리 바로가기' })
    expect(within(categories).getAllByRole('link').map((link) => link.textContent)).toEqual(['CLOTHING', 'BAG', 'WALLET', 'ACCESSORY', 'HOME'])
    expect(categories.querySelector('svg')).toBeNull()
    expect(within(categories).getByRole('link', { name: 'BAG' })).toHaveAttribute('href', '/shop/bag')
    expect(screen.queryByText('ETC')).not.toBeInTheDocument()
    expect(apiClient.get).not.toHaveBeenCalled()
  })
  it('submits an encoded keyword to URL and uses the existing public list API', async () => {
    show()
    search('  토트 & REWEAVE  ')
    expect(await screen.findByRole('article', { name: '데님 토트백 상품' })).toBeInTheDocument()
    expect(url().searchParams.get('q')).toBe('토트 & REWEAVE')
    expect(apiClient.get).toHaveBeenCalledWith('/v1/public/product-models', { params: { keyword: '토트 & REWEAVE', sort: 'LATEST', page: 0, size: 12 }, skipAuth: true, skipTokenRefresh: true })
    expect(screen.getByText('1개 상품')).toBeInTheDocument()
  })
  it('restores URL keyword without applying hidden category filters or rendering filter controls', async () => {
    show('/search?q=ReCode%20%ED%86%A0%ED%8A%B8&category=bag')
    await screen.findByRole('article')
    expect(screen.getByRole('searchbox')).toHaveValue('ReCode 토트')
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/product-models', expect.objectContaining({ params: { keyword: 'ReCode 토트', sort: 'LATEST', page: 0, size: 12 } }))
    expect(screen.queryByRole('navigation', { name: '검색 결과 카테고리' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })
  it('resets pagination and removes obsolete filters when submitting another keyword', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (_url, config) => ({ data: { ...catalog, page: (config?.params as { page?: number } | undefined)?.page ?? 0, totalElements: 48, totalPages: 4, hasNext: true } } as AxiosResponse))
    show('/search?q=%ED%86%A0%ED%8A%B8&page=2&sort=PRICE_DESC&category=bag')
    await screen.findByRole('article')
    search('자켓')
    await waitFor(() => expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/product-models', expect.objectContaining({ params: { keyword: '자켓', sort: 'LATEST', page: 0, size: 12 } })))
    expect(url().searchParams.get('q')).toBe('자켓')
    expect(url().searchParams.has('category')).toBe(false)
    expect(url().searchParams.has('sort')).toBe(false)
    expect(url().searchParams.has('page')).toBe(false)
  })
  it('uses SHOP pagination with 0-based requests and preserves keyword only', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (_url, config) => {
      const page = (config?.params as { page?: number } | undefined)?.page ?? 0
      return { data: { ...catalog, page, totalElements: 48, totalPages: 4, hasNext: page < 3 } } as AxiosResponse
    })
    show('/search?q=REWEAVE&category=bag')
    const pagination = await screen.findByRole('navigation', { name: '상품 페이지' })
    expect(screen.getByText('48개 상품')).toBeInTheDocument()
    fireEvent.click(within(pagination).getByRole('button', { name: '4페이지' }))
    await waitFor(() => expect(url().searchParams.get('page')).toBe('3'))
    expect(url().searchParams.get('q')).toBe('REWEAVE')
    expect(url().searchParams.has('category')).toBe(false)
    await waitFor(() => expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled())
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/product-models', expect.objectContaining({ params: { keyword: 'REWEAVE', sort: 'LATEST', page: 3, size: 12 } }))
  })
  it('keeps input, results and URL in sync when navigating back and forward', async () => {
    show('/search?q=first')
    await screen.findByRole('article')
    search('second')
    await waitFor(() => expect(url().searchParams.get('q')).toBe('second'))
    fireEvent.click(screen.getByRole('button', { name: '테스트 뒤로' }))
    await waitFor(() => expect(screen.getByRole('searchbox')).toHaveValue('first'))
    expect(url().searchParams.get('q')).toBe('first')
    fireEvent.click(screen.getByRole('button', { name: '테스트 앞으로' }))
    await waitFor(() => expect(screen.getByRole('searchbox')).toHaveValue('second'))
    expect(url().searchParams.get('q')).toBe('second')
  })
  it('shows no results without inserting dummy products and retains input', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { ...catalog, content: [], totalElements: 0, totalPages: 0 } } as AxiosResponse)
    show('/search?q=missing')
    expect(await screen.findByRole('heading', { name: '검색 결과가 없습니다' })).toBeInTheDocument()
    expect(screen.getByText('0개 상품')).toBeInTheDocument()
    expect(screen.getByRole('searchbox')).toHaveValue('missing')
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })
  it('shows loading and retry without losing the keyword', async () => {
    let reject!: (error: Error) => void
    vi.mocked(apiClient.get).mockReturnValueOnce(new Promise((_resolve, fail) => { reject = fail }))
    show('/search?q=retry&category=bag')
    expect(screen.getByText('상품을 불러오는 중')).toBeInTheDocument()
    await act(async () => { reject(new Error('network')) })
    expect(await screen.findByRole('alert')).toHaveTextContent('상품을 불러오지 못했습니다')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('article')).toBeInTheDocument()
    expect(url().searchParams.get('q')).toBe('retry')
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/product-models', expect.objectContaining({ params: { keyword: 'retry', sort: 'LATEST', page: 0, size: 12 } }))
  })
  it('reuses SHOP cards and image URL handling without adding wishlist or cart controls', async () => {
    show('/search?q=bag')
    const card = await screen.findByRole('article')
    expect(within(card).getByRole('link', { name: '데님 토트백 상세 보기' })).toHaveAttribute('href', '/products/31')
    expect(within(card).getByRole('img')).toHaveAttribute('src', product.thumbnailUrl)
    expect(within(card).getByText('품절')).toBeInTheDocument()
    expect(within(card).queryByRole('button')).not.toBeInTheDocument()
    expect(within(card).getByRole('link', { name: 'REWEAVE' })).toHaveAttribute('href', '/brands/5')
    expect(card.querySelector('a a')).toBeNull()
    fireEvent.click(within(card).getByRole('link', { name: '데님 토트백 상세 보기' }))
    expect(screen.getByRole('heading', { name: '공개 상품 상세' })).toBeInTheDocument()
  })
  it('clears a submitted blank keyword back to the pre-search view', async () => {
    show('/search?q=old&page=2')
    await screen.findByText('요청한 페이지에 상품이 없습니다')
    const count = vi.mocked(apiClient.get).mock.calls.length
    search('   ')
    expect(screen.getByText('EXPLORE BY CATEGORY')).toBeInTheDocument()
    expect(url().searchParams.has('q')).toBe(false)
    expect(url().searchParams.has('page')).toBe(false)
    expect(apiClient.get).toHaveBeenCalledTimes(count)
  })
  it('clears the input without submitting and returns focus to the searchbox', async () => {
    show('/search?q=old')
    await screen.findByRole('article')
    const count = vi.mocked(apiClient.get).mock.calls.length
    fireEvent.click(screen.getByRole('button', { name: '검색어 지우기' }))
    expect(screen.getByRole('searchbox')).toHaveValue('')
    expect(screen.getByRole('searchbox')).toHaveFocus()
    expect(screen.queryByRole('button', { name: '검색어 지우기' })).not.toBeInTheDocument()
    expect(url().searchParams.get('q')).toBe('old')
    expect(apiClient.get).toHaveBeenCalledTimes(count)
  })
  it('links the header SEARCH icon to the search page without an overlay', () => {
    show()
    expect(screen.getByRole('link', { name: '상품 검색' })).toHaveAttribute('href', '/search')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
