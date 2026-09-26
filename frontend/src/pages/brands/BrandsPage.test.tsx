import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { brandIntroduction, type PublicBrand } from '../../features/brand/publicBrands.ts'
import type { PublicProductModel } from '../../features/product/types/publicProduct.types.ts'
import { ShopProvider } from '../../features/shop/ShopContext.tsx'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import type { PageResponse } from '../../types/api.types.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
import { BrandsPage } from './BrandsPage.tsx'

const brand: PublicBrand = { brandId: 5, brandName: 'REWEAVE', introduction: null, logoUrl: null, productCount: 13 }
const brands: PageResponse<PublicBrand> = { content: [brand], page: 0, size: 8, totalElements: 1, totalPages: 1, hasNext: false }
const product: PublicProductModel = { productModelId: 31, name: '데님 토트백', brandId: 5, brandName: 'REWEAVE', category: 'BAG', subcategory: 'TOTE', price: 129000, description: '테스트', thumbnailUrl: '/api/v1/public/product-models/31/thumbnail', status: 'ON_SALE', stockQuantity: 0, createdAt: '2026-09-17', journeyUrl: null }
const products: PageResponse<PublicProductModel> = { content: [product], page: 0, size: 12, totalElements: 13, totalPages: 2, hasNext: true }
const response = (data: unknown) => ({ data }) as AxiosResponse
function Location() {
  const location = useLocation()
  const navigate = useNavigate()
  return <><output aria-label="현재 위치">{location.pathname}{location.search}</output><button onClick={() => navigate(-1)}>테스트 뒤로</button></>
}
function show(path = '/brands') {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ShopProvider><MemoryRouter initialEntries={[path]}><Routes><Route path="/brands" element={<BrandsPage />} /><Route path="/brands/:brandId" element={<BrandsPage />} /><Route path="/products/:productId" element={<h1>공개 상품 상세</h1>} /></Routes><Location /></MemoryRouter></ShopProvider></QueryClientProvider>)
}
function url() { return new URL(screen.getByLabelText('현재 위치').textContent!, 'http://test') }
function submit(keyword: string) { fireEvent.change(screen.getByRole('searchbox', { name: '브랜드명 검색' }), { target: { value: keyword } }); fireEvent.submit(screen.getByRole('search')) }
beforeEach(() => {
  useAuthStore.getState().signOut()
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  vi.spyOn(apiClient, 'get').mockImplementation(async (path) => response(path === '/v1/public/brands' ? brands : path === '/v1/public/brands/5' ? brand : products))
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })

describe('public brands', () => {
  it('renders the public list with size 8, count, null introduction and distinct logo fallbacks', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(response({ ...brands, content: [brand, { ...brand, brandId: 6, brandName: 'OCEAN' }] }))
    show()
    const card = await screen.findByRole('article', { name: 'REWEAVE 브랜드' })
    expect(screen.getByRole('heading', { name: 'BRANDS', level: 1 }).parentElement).toHaveClass('shop-catalog__heading')
    expect(apiClient.get).toHaveBeenCalledWith('/v1/public/brands', { params: { keyword: undefined, sort: 'PRODUCT_COUNT', page: 0, size: 8 }, skipAuth: true, skipTokenRefresh: true })
    expect(within(card).getByText(brandIntroduction.card)).toBeInTheDocument()
    expect(within(card).getByRole('img')).toHaveTextContent('RE')
    expect(screen.getByRole('img', { name: 'OCEAN 로고 대체 이미지' })).toHaveTextContent('OC')
    expect(within(card).getByText('13개 상품')).toBeInTheDocument()
    expect(within(card).getByRole('link')).toHaveAttribute('href', '/brands/5')
    expect(screen.queryByRole('navigation', { name: '브랜드 페이지' })).not.toBeInTheDocument()
    expect(within(screen.getByRole('navigation', { name: '주요 메뉴' })).getByRole('link', { name: 'BRANDS' })).toHaveAttribute('href', '/brands')
  })
  it('uses size 8 and 0-based pagination while retaining search and sort', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (_path, config) => {
      const page = (config?.params as { page: number }).page
      return response({ ...brands, page, totalElements: 9, totalPages: 2, hasNext: page === 0 })
    })
    show('/brands?q=RE&sort=NAME')
    const pagination = await screen.findByRole('navigation', { name: '브랜드 페이지' })
    vi.mocked(window.scrollTo).mockClear()
    fireEvent.click(within(pagination).getByRole('button', { name: '2페이지' }))
    await waitFor(() => expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/brands', expect.objectContaining({ params: { keyword: 'RE', sort: 'NAME', page: 1, size: 8 } })))
    expect(url().searchParams.get('q')).toBe('RE')
    expect(url().searchParams.get('sort')).toBe('NAME')
    await waitFor(() => expect(within(screen.getByRole('navigation', { name: '브랜드 페이지' })).getByRole('button', { name: '다음 페이지' })).toBeDisabled())
    expect(window.scrollTo).toHaveBeenCalledOnce()
  })
  it('searches brand names, resets page and restores keyword on back navigation', async () => {
    show('/brands?q=old&page=3')
    await screen.findByText('요청한 페이지에 브랜드가 없습니다')
    submit('  리위브 & RE  ')
    await waitFor(() => expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/brands', expect.objectContaining({ params: { keyword: '리위브 & RE', sort: 'PRODUCT_COUNT', page: 0, size: 8 } })))
    expect(url().searchParams.get('q')).toBe('리위브 & RE')
    expect(url().searchParams.has('page')).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: '테스트 뒤로' }))
    await waitFor(() => expect(screen.getByRole('searchbox')).toHaveValue('old'))
  })
  it('switches sort to NAME without losing keyword and resets pagination', async () => {
    show('/brands?q=RE&page=2')
    await screen.findByText('요청한 페이지에 브랜드가 없습니다')
    fireEvent.change(screen.getByRole('combobox', { name: '브랜드 정렬' }), { target: { value: 'NAME' } })
    await waitFor(() => expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/brands', expect.objectContaining({ params: { keyword: 'RE', sort: 'NAME', page: 0, size: 8 } })))
    expect(url().searchParams.has('page')).toBe(false)
  })
  it('clears the brand search input with the same 22px X as SEARCH without submitting', async () => {
    show('/brands?q=RE&sort=NAME')
    await screen.findByRole('article')
    const count = vi.mocked(apiClient.get).mock.calls.length
    const clear = screen.getByRole('button', { name: '검색어 지우기' })
    expect(clear.querySelector('svg')).toHaveAttribute('width', '22')
    expect(clear.querySelector('svg')).toHaveAttribute('height', '22')
    fireEvent.click(clear)
    expect(screen.getByRole('searchbox', { name: '브랜드명 검색' })).toHaveValue('')
    expect(screen.getByRole('searchbox')).toHaveFocus()
    expect(screen.queryByRole('button', { name: '검색어 지우기' })).not.toBeInTheDocument()
    expect(apiClient.get).toHaveBeenCalledTimes(count)
    expect(url().searchParams.get('q')).toBe('RE')
    expect(screen.getByRole('combobox', { name: '브랜드 정렬' })).toHaveValue('NAME')
  })
  it('renders registered introduction and logo and falls back when the logo fails', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(response({ ...brands, content: [{ ...brand, introduction: '버려진 소재의 새 쓰임', logoUrl: '/api/v1/public/brands/5/logo' }] }))
    show()
    expect(await screen.findByText('버려진 소재의 새 쓰임')).toBeInTheDocument()
    const logo = screen.getByRole('img', { name: 'REWEAVE 로고' })
    expect(logo).toHaveAttribute('src', '/api/v1/public/brands/5/logo')
    fireEvent.error(logo)
    expect(screen.getByRole('img', { name: 'REWEAVE 로고 대체 이미지' })).toHaveTextContent('RE')
  })
  it.each(['', '?q=missing'])('shows empty list state without fake brands for %s', async (query) => {
    vi.mocked(apiClient.get).mockResolvedValue(response({ ...brands, content: [], totalElements: 0, totalPages: 0 }))
    show(`/brands${query}`)
    expect(await screen.findByRole('heading', { name: query ? '검색 결과가 없습니다' : '공개된 브랜드가 없습니다' })).toBeInTheDocument()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })
  it('shows loading and retry for the same list query', async () => {
    let reject!: (error: Error) => void
    vi.mocked(apiClient.get).mockReturnValueOnce(new Promise((_resolve, fail) => { reject = fail }))
    show('/brands?q=RE&sort=NAME')
    expect(screen.getByText('브랜드를 불러오는 중')).toBeInTheDocument()
    await act(async () => reject(new Error('network')))
    expect(await screen.findByRole('alert')).toHaveTextContent('브랜드를 불러오지 못했습니다')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('article')).toBeInTheDocument()
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/brands', expect.objectContaining({ params: { keyword: 'RE', sort: 'NAME', page: 0, size: 8 } }))
  })
  it('opens brand detail from a card and requests size 12 products by brandId', async () => {
    show()
    const card = await screen.findByRole('article')
    fireEvent.click(within(card).getByRole('link'))
    expect(await screen.findByRole('heading', { name: 'REWEAVE' })).toBeInTheDocument()
    expect(screen.getByText(brandIntroduction.detail)).toBeInTheDocument()
    const productCard = await screen.findByRole('article', { name: '데님 토트백 상품' })
    expect(apiClient.get).toHaveBeenCalledWith('/v1/public/brands/5', { skipAuth: true, skipTokenRefresh: true })
    expect(apiClient.get).toHaveBeenCalledWith('/v1/public/product-models', { params: { brandId: 5, page: 0, size: 12 }, skipAuth: true, skipTokenRefresh: true })
    expect(within(productCard).getByRole('link', { name: 'REWEAVE' })).toHaveAttribute('href', '/brands/5')
    expect(productCard.querySelector('a a')).toBeNull()
    expect(within(productCard).getByText('품절')).toBeInTheDocument()
    fireEvent.click(within(productCard).getByRole('link', { name: '데님 토트백 상세 보기' }))
    expect(screen.getByRole('heading', { name: '공개 상품 상세' })).toBeInTheDocument()
  })
  it('uses independent size 12 product pagination on detail', async () => {
    vi.mocked(apiClient.get).mockImplementation(async (path, config) => path === '/v1/public/brands/5' ? response(brand) : response({ ...products, page: (config?.params as { page: number }).page, hasNext: (config?.params as { page: number }).page === 0 }))
    show('/brands/5')
    const pagination = await screen.findByRole('navigation', { name: '상품 페이지' })
    fireEvent.click(within(pagination).getByRole('button', { name: '2페이지' }))
    await waitFor(() => expect(apiClient.get).toHaveBeenLastCalledWith('/v1/public/product-models', expect.objectContaining({ params: { brandId: 5, page: 1, size: 12 } })))
    expect(screen.queryByRole('navigation', { name: '브랜드 페이지' })).not.toBeInTheDocument()
  })
  it('treats 404 as unavailable brand without requesting products or retrying automatically', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(createAppError(ERROR_KIND.API_ERROR, { status: 404 }))
    show('/brands/5')
    expect(await screen.findByRole('heading', { name: '브랜드를 찾을 수 없습니다' })).toBeInTheDocument()
    expect(apiClient.get).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('link', { name: '전체 브랜드 보기' })).toHaveAttribute('href', '/brands')
  })
  it.each(['not-a-number', '0', '9007199254740993'])('rejects invalid brand ID %s without API requests', (id) => {
    show(`/brands/${id}`)
    expect(screen.getByRole('heading', { name: '브랜드를 찾을 수 없습니다' })).toBeInTheDocument()
    expect(apiClient.get).not.toHaveBeenCalled()
  })
  it('retries failed brand detail before loading products', async () => {
    vi.mocked(apiClient.get).mockRejectedValueOnce(createAppError(ERROR_KIND.API_ERROR, { status: 500 })).mockRejectedValueOnce(createAppError(ERROR_KIND.API_ERROR, { status: 500 }))
    show('/brands/5')
    expect(await screen.findByRole('alert', {}, { timeout: 3000 })).toHaveTextContent('브랜드를 불러오지 못했습니다')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: 'REWEAVE' })).toBeInTheDocument()
    expect(await screen.findByRole('article')).toHaveAccessibleName('데님 토트백 상품')
  })
})
