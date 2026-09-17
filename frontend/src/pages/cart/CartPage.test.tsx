import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RoleGuard } from '../../app/router/guards/RoleGuard.tsx'
import { CartButton } from '../../features/cart/CartButton.tsx'
import { cartErrorMessage, cartKey, type Cart, type CartItem } from '../../features/cart/useCart.ts'
import { ShopProvider } from '../../features/shop/ShopContext.tsx'
import { apiClient } from '../../services/apiClient.ts'
import { queryClient } from '../../services/queryClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
import type { UserRole } from '../../types/role.types.ts'
import { CartPage } from './CartPage.tsx'

const item: CartItem = { product: { productModelId: 31, name: '데님 백', brandId: 5, brandName: 'REWEAVE', price: 99000,
  category: 'BAG', subcategory: 'TOTE', status: 'ON_SALE', stockQuantity: 4, thumbnailUrl: '/api/v1/product-models/31/thumbnail', description: '', createdAt: '2026-09-17' },
  quantity: 2, addedPrice: 89000, priceChanged: true, purchasable: true }
const cart: Cart = { items: [item], totalQuantity: 2, totalPrice: 198000 }
const empty: Cart = { items: [], totalQuantity: 0, totalPrice: 0 }
function response(data: unknown) { return { data } as AxiosResponse }
function login(role: UserRole = 'CONSUMER', id = 7) { useAuthStore.getState().signIn({ id, name: '고객', email: 't@test.com', role, walletAddress: null }, `token-${id}`) }
function Probe() { const location = useLocation(); return <output aria-label="위치" data-state={JSON.stringify(location.state)}>{location.pathname}{location.search}</output> }
function show(path = '/cart', client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  render(<QueryClientProvider client={client}><ShopProvider><MemoryRouter initialEntries={[path]}><Routes>
    <Route element={<RoleGuard allow={['CONSUMER']} />}><Route path="/cart" element={<CartPage />} /></Route>
    <Route path="/shop" element={<><CartButton productId={31} name="상품 A" /><CartButton productId={31} name="상품 B" /><CartButton productId={32} name="다른 상품" /></>} />
    <Route path="/login" element={<p>로그인 화면</p>} /><Route path="/403" element={<p>접근 불가</p>} />
  </Routes><Probe /></MemoryRouter></ShopProvider></QueryClientProvider>)
  return client
}
beforeEach(() => {
  useAuthStore.getState().signOut()
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  vi.spyOn(apiClient, 'get').mockImplementation(async (url) => response(url.endsWith('/thumbnail') ? new Blob(['image']) : cart))
  vi.spyOn(apiClient, 'post').mockResolvedValue(response(cart))
  vi.spyOn(apiClient, 'patch').mockResolvedValue(response({ ...cart, items: [{ ...item, quantity: 3 }], totalQuantity: 3, totalPrice: 297000 }))
  vi.spyOn(apiClient, 'delete').mockResolvedValue(response(empty))
})
afterEach(() => { cleanup(); useAuthStore.getState().signOut(); vi.restoreAllMocks() })

describe('consumer cart', () => {
  it.each([['anonymous', null, '/login'], ['business', 'BRAND', '/403']] as const)('guards %s access without calling consumer APIs', (_name, role, target) => {
    if (role) login(role)
    show()
    expect(screen.getByLabelText('위치')).toHaveTextContent(target)
    expect(apiClient.get).not.toHaveBeenCalled()
  })
  it('returns a signed-out add click to the original URL without automatic cart merge', () => {
    show('/shop?q=토트')
    fireEvent.click(screen.getByRole('button', { name: '상품 A 장바구니 담기' }))
    expect(screen.getByLabelText('위치')).toHaveTextContent('/login')
    expect(JSON.parse(screen.getByLabelText('위치').getAttribute('data-state')!).from.pathname).toBe('/shop?q=토트')
    expect(apiClient.get).not.toHaveBeenCalled(); expect(apiClient.post).not.toHaveBeenCalled()
  })
  it('blocks business add actions with a consumer-account notice', () => {
    login('BRAND'); show('/shop')
    fireEvent.click(screen.getByRole('button', { name: '상품 A 장바구니 담기' }))
    expect(screen.getByRole('alert')).toHaveTextContent('소비자 계정')
    expect(apiClient.post).not.toHaveBeenCalled()
  })
  it('locks duplicate and other product writes until the cumulative add completes, then uses the response without GET', async () => {
    login()
    let finish!: (data: AxiosResponse) => void
    vi.mocked(apiClient.post).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    const client = show('/shop')
    await waitFor(() => expect(client.getQueryData(cartKey(7))).toEqual(cart))
    const getCount = vi.mocked(apiClient.get).mock.calls.length
    fireEvent.click(screen.getByRole('button', { name: '상품 A 장바구니 담기' }))
    fireEvent.click(screen.getByRole('button', { name: '상품 B 장바구니 담기' }))
    fireEvent.click(screen.getByRole('button', { name: '다른 상품 장바구니 담기' }))
    await waitFor(() => expect(apiClient.post).toHaveBeenCalledTimes(1))
    expect(screen.getByRole('button', { name: '상품 B 장바구니 담기' })).toBeDisabled()
    await act(async () => finish(response(cart)))
    expect(apiClient.post).toHaveBeenCalledWith('/v1/consumers/me/cart', { productModelId: 31, quantity: 1 })
    expect(apiClient.get).toHaveBeenCalledTimes(getCount)
    expect(client.getQueryData(cartKey(7))).toEqual(cart)
  })
  it('renders authenticated blob images, current prices and an item-count header badge', async () => {
    login(); show()
    const row = await screen.findByRole('article', { name: '데님 백 장바구니 상품' })
    expect((await within(row).findByRole('img')).getAttribute('src')).toMatch(/^blob:/)
    expect(apiClient.get).toHaveBeenCalledWith('/v1/product-models/31/thumbnail', { responseType: 'blob' })
    expect(screen.getByRole('button', { name: '장바구니, 상품 1개' })).toHaveTextContent('1')
    expect(screen.getByRole('button', { name: '주문하기' })).toBeDisabled()
  })
  it('sets quantity via PATCH and immediately updates totals without re-querying', async () => {
    login(); show()
    const increase = await screen.findByRole('button', { name: '데님 백 수량 늘리기' })
    fireEvent.click(screen.getByRole('checkbox', { name: '데님 백 주문 선택' }))
    const count = vi.mocked(apiClient.get).mock.calls.filter(([url]) => url.endsWith('/cart')).length
    fireEvent.click(increase)
    await waitFor(() => expect(screen.getByLabelText('데님 백 수량 값')).toHaveTextContent('3'))
    expect(apiClient.patch).toHaveBeenCalledWith('/v1/consumers/me/cart/31', { quantity: 3 })
    expect(within(screen.getByRole('complementary', { name: '장바구니 합계' })).getByText('297,000원')).toBeInTheDocument()
    expect(vi.mocked(apiClient.get).mock.calls.filter(([url]) => url.endsWith('/cart'))).toHaveLength(count)
  })
  it('removes an item by productModelId and updates the empty state and badge', async () => {
    login(); show()
    fireEvent.click(await screen.findByRole('button', { name: '제거' }))
    expect(await screen.findByText('장바구니가 비어 있습니다')).toBeInTheDocument()
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/consumers/me/cart/31')
    expect(screen.getByRole('button', { name: '장바구니, 상품 0개' })).toBeInTheDocument()
  })
  it('clears the whole cart only after confirmation', async () => {
    login(); show()
    const clear = await screen.findByRole('button', { name: '전체 비우기' })
    vi.mocked(window.confirm).mockReturnValueOnce(false)
    fireEvent.click(clear)
    expect(apiClient.delete).not.toHaveBeenCalled()
    fireEvent.click(clear)
    await screen.findByText('장바구니가 비어 있습니다')
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/consumers/me/cart')
  })
  it.each([['DISCONTINUED', 3, '판매 종료'], ['ON_SALE', 0, '품절'], ['ON_SALE', 3, '구매 불가']] as const)('keeps unavailable %s items and trusts server totals (%s, %s)', async (status, stockQuantity, label) => {
    login()
    const unavailable = { ...item, product: { ...item.product, productModelId: 32, name: '구매 불가 상품', status, stockQuantity }, quantity: 5, purchasable: false }
    vi.mocked(apiClient.get).mockImplementation(async (url) => response(url.endsWith('/thumbnail') ? new Blob(['image']) : { ...cart, items: [item, unavailable], totalQuantity: 2, totalPrice: 12345 }))
    show()
    const row = await screen.findByRole('article', { name: '구매 불가 상품 장바구니 상품' })
    expect(within(row).getByText(label)).toBeInTheDocument()
    expect(within(row).getByRole('button', { name: '구매 불가 상품 수량 늘리기' })).toBeDisabled()
    expect(within(row).getByRole('button', { name: '구매 불가 상품 수량 줄이기' })).toBeDisabled()
    expect(within(row).getByRole('button', { name: '제거' })).toBeEnabled()
    expect(within(row).queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: `${unavailable.product.name} 주문 선택` })).toBeDisabled()
    fireEvent.click(screen.getByRole('checkbox', { name: '데님 백 주문 선택' }))
    expect(within(screen.getByRole('complementary', { name: '장바구니 합계' })).getByText('198,000원')).toBeInTheDocument()
    expect(screen.getByText(/구매 불가 상품은 제외/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '장바구니, 상품 2개' })).toBeInTheDocument()
  })
  it.each([[89000, '가격 인상'], [109000, '가격 인하']] as const)('shows the previous %s price and %s alongside the current price', async (addedPrice, label) => {
    login(); vi.mocked(apiClient.get).mockImplementation(async (url) => response(url.endsWith('/thumbnail') ? new Blob(['image']) : { ...cart, items: [{ ...item, addedPrice }] }))
    show()
    const row = await screen.findByRole('article', { name: '데님 백 장바구니 상품' })
    expect(within(row).getByText(label)).toBeInTheDocument()
    expect(row.querySelector('del')).toHaveTextContent(`${addedPrice.toLocaleString('ko-KR')}원`)
    expect(within(row).getByText('주문 시 현재 가격 기준')).toBeInTheDocument()
  })
  it('prevents quantity zero and stock/99 overflow', async () => {
    login(); vi.mocked(apiClient.get).mockImplementation(async (url) => response(url.endsWith('/thumbnail') ? new Blob(['image']) : { ...cart, items: [{ ...item, quantity: 1, product: { ...item.product, stockQuantity: 1 } }] }))
    show()
    expect(await screen.findByRole('button', { name: '데님 백 수량 줄이기' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '데님 백 수량 늘리기' })).toBeDisabled()
  })
  it('caps quantity at 99 even with more stock', async () => {
    login(); vi.mocked(apiClient.get).mockImplementation(async (url) => response(url.endsWith('/thumbnail') ? new Blob(['image']) : { ...cart, items: [{ ...item, quantity: 99, product: { ...item.product, stockQuantity: 150 } }] }))
    show()
    expect(await screen.findByRole('button', { name: '데님 백 수량 늘리기' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '데님 백 수량 줄이기' })).toBeEnabled()
  })
  it('can reduce existing excess quantity directly to the available stock', async () => {
    login(); vi.mocked(apiClient.get).mockImplementation(async (url) => response(url.endsWith('/thumbnail') ? new Blob(['image']) : { ...cart, items: [{ ...item, quantity: 8, product: { ...item.product, stockQuantity: 3 } }] }))
    show()
    fireEvent.click(await screen.findByRole('button', { name: '데님 백 수량 줄이기' }))
    await waitFor(() => expect(apiClient.patch).toHaveBeenCalledWith('/v1/consumers/me/cart/31', { quantity: 3 }))
  })
  it.each([['CART_FULL', 400, '최대 50개'], ['CART_QUANTITY_EXCEEDS_STOCK', 409, '남은 재고보다']] as const)('provides a specific %s retry notice', (code, status, text) => {
    expect(cartErrorMessage(createAppError(ERROR_KIND.API_ERROR, { code, status }))).toContain(text)
  })
  it('keeps concurrent-update failures local and permits a manual retry without automatic POST retries', async () => {
    login(); vi.mocked(apiClient.post).mockRejectedValueOnce(createAppError(ERROR_KIND.API_ERROR, { status: 409, code: 'CART_CONCURRENT_UPDATE' }))
    show('/shop')
    fireEvent.click(screen.getByRole('button', { name: '상품 A 장바구니 담기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('장바구니 변경이 겹쳤')
    expect(apiClient.post).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: '상품 A 장바구니 담기' }))
    await screen.findByText(/장바구니에 담았습니다/)
    expect(apiClient.post).toHaveBeenCalledTimes(2)
  })
  it('shows loading and no placeholder products', () => {
    login(); vi.mocked(apiClient.get).mockImplementation(() => new Promise(() => {})); show()
    expect(screen.getByText('장바구니를 불러오는 중')).toBeInTheDocument()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })
  it('retries a failed initial GET and links empty carts to SHOP', async () => {
    login(); vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(response(empty)); show()
    await screen.findByText('장바구니를 불러오지 못했습니다')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    await screen.findByText('장바구니가 비어 있습니다')
    expect(screen.getByRole('link', { name: '상품 둘러보기' })).toHaveAttribute('href', '/shop')
  })
  it('discards a late cart snapshot after switching accounts', async () => {
    login()
    let finish!: (data: AxiosResponse) => void
    vi.mocked(apiClient.post).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    show('/shop', queryClient)
    fireEvent.click(screen.getByRole('button', { name: '상품 A 장바구니 담기' }))
    await waitFor(() => expect(apiClient.post).toHaveBeenCalledTimes(1))
    vi.mocked(apiClient.get).mockResolvedValue(response(empty))
    await act(async () => login('CONSUMER', 8))
    await waitFor(() => expect(queryClient.getQueryData(cartKey(8))).toEqual(empty))
    await act(async () => finish(response(cart)))
    expect(queryClient.getQueryData(cartKey(7))).toBeUndefined()
    expect(queryClient.getQueryData(cartKey(8))).toEqual(empty)
  })
})
