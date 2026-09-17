import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { Link, MemoryRouter, Route, Routes, useLocation, useRoutes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosResponse } from 'axios'
import { useShop } from '../../features/shop/ShopContext.tsx'
import { AppProviders } from '../../app/providers/AppProviders.tsx'
import { routes } from '../../app/router/routes.tsx'
import type { PublicProductDetail } from '../../features/product/types/publicProduct.types.ts'
import { apiClient } from '../../services/apiClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
import { LoginPage } from '../auth/LoginPage.tsx'
import { PublicProductDetailPage } from './PublicProductDetailPage.tsx'

const product: PublicProductDetail = {
  productModelId: 31, name: '데님 토트백', brandId: 5, brandName: 'REWEAVE', category: 'BAG', subcategory: 'TOTE',
  price: 129000, description: '소재의 다음 이야기\n오래 사용할 가방', stockQuantity: 2, status: 'ON_SALE',
  thumbnailUrl: '/api/v1/public/product-models/31/thumbnail', images: [], createdAt: '2026-09-17',
}
function Probe() {
  const location = useLocation()
  const shop = useShop()
  return <><output aria-label="현재 위치" data-state={JSON.stringify(location.state)}>{location.pathname}</output><output aria-label="장바구니 수">{shop.cartCount}</output></>
}
function HomeMemory() {
  const shop = useShop()
  return <><button onClick={() => { shop.toggleWishlist('31'); shop.addToCart('31') }}>HOME 메모리 동작</button><Link to="/products/31">상품 상세</Link><output aria-label="HOME 찜 상태">{String(shop.wishlist.has('31'))}</output></>
}
function show(path = '/products/31') {
  return render(<AppProviders><MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/products/:productId" element={<PublicProductDetailPage />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/shop" element={<HomeMemory />} />
  </Routes><Probe /></MemoryRouter></AppProviders>)
}
function signIn() { useAuthStore.getState().signIn({ id: 5, email: 'c@test.com', name: '고객', role: 'CONSUMER', walletAddress: null }, 'test-token') }
function ActualRoutes() { return useRoutes(routes) }
beforeEach(() => {
  useAuthStore.getState().signOut()
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: product } as AxiosResponse)
  vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { accessToken: 'test-token', user: { id: 5, email: 'c@test.com', name: '고객', role: 'CONSUMER' } } } as AxiosResponse)
})
afterEach(() => { cleanup(); useAuthStore.getState().signOut(); vi.restoreAllMocks() })

describe('public consumer product detail', () => {
  it.each(['/products', '/business/products/31'])('keeps legacy business entry %s protected', async (path) => {
    render(<AppProviders><MemoryRouter initialEntries={[path]}><ActualRoutes /><Probe /></MemoryRouter></AppProviders>)
    await waitFor(() => expect(screen.getByLabelText('현재 위치')).toHaveTextContent('/login'))
    const state = JSON.parse(screen.getByLabelText('현재 위치').getAttribute('data-state')!)
    expect(state.from.pathname).toBe(path === '/products' ? '/business/products' : path)
    expect(apiClient.get).not.toHaveBeenCalled()
  })
  it('uses public API without auth and falls back to thumbnail for an empty gallery', async () => {
    show()
    expect(await screen.findByRole('heading', { name: product.name })).toBeInTheDocument()
    expect(apiClient.get).toHaveBeenCalledWith('/v1/public/product-models/31', { skipAuth: true, skipTokenRefresh: true })
    expect(screen.getByText('₩ 129,000')).toBeInTheDocument()
    expect(screen.getByText('남은 수량 2개')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: '데님 토트백 대표 이미지' })).toHaveAttribute('src', product.thumbnailUrl)
    expect(screen.queryByRole('button', { name: /번째 상품 이미지/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: product.brandName })).toHaveAttribute('href', `/brands/${product.brandId}`)
    expect(screen.getByText(/소재의 다음 이야기/)).toHaveTextContent('오래 사용할 가방')
    expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, left: 0, behavior: 'instant' })
  })
  it('renders no-image state without dummy assets', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { ...product, thumbnailUrl: '' } } as AxiosResponse)
    show()
    expect(await screen.findByText('이미지 준비 중')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: '상품 이미지 갤러리' })).queryByRole('img')).not.toBeInTheDocument()
  })
  it('renders one MAIN image without unnecessary gallery controls', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { ...product, images: [{ productImageId: 1, url: '/api/v1/public/product-models/31/images/1', type: 'MAIN', sortOrder: 0 }] } } as AxiosResponse)
    show()
    expect(await screen.findByRole('img', { name: '데님 토트백 MAIN' })).toHaveAttribute('src', '/api/v1/public/product-models/31/images/1')
    expect(screen.queryByRole('button', { name: /번째 상품 이미지/ })).not.toBeInTheDocument()
  })
  it('sorts four images by sortOrder and switches active images using backend URLs', async () => {
    const images = [3, 1, 4, 2].map((order) => ({ productImageId: order, url: `/api/v1/public/product-models/31/images/${order}`, type: (['MAIN', 'ANGLE', 'DETAIL', 'CONTEXT'] as const)[order - 1], sortOrder: order }))
    vi.mocked(apiClient.get).mockResolvedValue({ data: { ...product, images } } as AxiosResponse)
    show()
    expect(await screen.findByRole('img', { name: '데님 토트백 MAIN' })).toHaveAttribute('src', images[1].url)
    fireEvent.click(screen.getByRole('button', { name: '4번째 상품 이미지' }))
    expect(screen.getByRole('img', { name: '데님 토트백 CONTEXT' })).toHaveAttribute('src', images[2].url)
    expect(screen.getByRole('button', { name: '4번째 상품 이미지' })).toHaveAttribute('aria-pressed', 'true')
    expect(images.map((image) => image.sortOrder)).toEqual([3, 1, 4, 2])
  })
  it('keeps static thumbnail paths and recovers gracefully from image failures', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { ...product, thumbnailUrl: '/images/landing/product-denim-circle.webp' } } as AxiosResponse)
    show()
    const image = await screen.findByRole('img', { name: '데님 토트백 대표 이미지' })
    expect(image).toHaveAttribute('src', '/images/landing/product-denim-circle.webp')
    fireEvent.error(image)
    expect(screen.getByText('이미지를 불러오지 못했습니다')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: product.name })).toBeInTheDocument()
  })
  it('marks stockQuantity zero as sold out while allowing wishlist interaction', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { ...product, stockQuantity: 0 } } as AxiosResponse)
    show()
    expect(await screen.findByRole('button', { name: '품절' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '장바구니 담기' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '찜하기' })).toBeEnabled()
  })
  it.each(['찜하기', '장바구니 담기', '구매하기'])('returns to the same product after login from %s without automatic purchase', async (button) => {
    show()
    await screen.findByRole('heading', { name: product.name })
    fireEvent.click(screen.getByRole('button', { name: button }))
    expect(screen.getByLabelText('현재 위치')).toHaveTextContent('/login')
    expect(screen.getByLabelText('현재 위치')).toHaveAttribute('data-state', JSON.stringify({ from: { pathname: '/products/31' } }))
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'c@test.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))
    await waitFor(() => expect(screen.getByLabelText('현재 위치')).toHaveTextContent('/products/31'))
    expect(apiClient.post).toHaveBeenCalledTimes(1)
    expect(screen.getByLabelText('장바구니 수')).toHaveTextContent('0')
  })
  it('shares HOME memory state across routes and never creates orders', async () => {
    signIn()
    show('/shop')
    fireEvent.click(screen.getByRole('button', { name: 'HOME 메모리 동작' }))
    fireEvent.click(screen.getByRole('link', { name: '상품 상세' }))
    expect(await screen.findByRole('button', { name: '찜하기' })).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(screen.getByRole('button', { name: '장바구니 담기' }))
    expect(screen.getByLabelText('장바구니 수')).toHaveTextContent('2')
    fireEvent.click(screen.getByRole('button', { name: '찜하기' }))
    fireEvent.click(screen.getByRole('button', { name: '구매하기' }))
    expect(screen.getByText(/현재 주문이나 결제는 진행되지 않습니다/)).toBeInTheDocument()
    expect(apiClient.post).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('link', { name: '전체 상품 보기 →' }))
    expect(screen.getByLabelText('HOME 찜 상태')).toHaveTextContent('false')
    expect(screen.getByLabelText('장바구니 수')).toHaveTextContent('2')
  })
  it('handles 404 as missing product without retry or a forbidden page', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(createAppError(ERROR_KIND.API_ERROR, { status: 404 }))
    show()
    expect(await screen.findByRole('heading', { name: '상품을 찾을 수 없습니다' })).toBeInTheDocument()
    expect(apiClient.get).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
  })
  it.each(['abc', '0', '-1', '1e2'])('rejects invalid product ID %s without an API request', (id) => {
    show(`/products/${id}`)
    expect(screen.getByRole('heading', { name: '상품을 찾을 수 없습니다' })).toBeInTheDocument()
    expect(apiClient.get).not.toHaveBeenCalled()
  })
  it('shows loading and retryable server errors', async () => {
    let reject!: (error: unknown) => void
    vi.mocked(apiClient.get).mockReturnValueOnce(new Promise((_resolve, fail) => { reject = fail })).mockRejectedValueOnce(createAppError(ERROR_KIND.API_ERROR, { status: 500 }))
    show()
    expect(screen.getByText('상품을 불러오는 중')).toBeInTheDocument()
    await act(async () => { reject(createAppError(ERROR_KIND.API_ERROR, { status: 500 })) })
    expect(await screen.findByRole('alert', {}, { timeout: 4000 })).toHaveTextContent('상품을 불러오지 못했습니다')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: product.name })).toBeInTheDocument()
  })
})
