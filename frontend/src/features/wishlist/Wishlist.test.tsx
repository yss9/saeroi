import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RoleGuard } from '../../app/router/guards/RoleGuard.tsx'
import { ConsumerWishlistPage } from '../../pages/mypage/ConsumerWishlistPage.tsx'
import { apiClient } from '../../services/apiClient.ts'
import { queryClient as appQueryClient } from '../../services/queryClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import type { UserRole } from '../../types/role.types.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
import { WishlistButton } from './WishlistButton.tsx'
import { wishlistKeys, type WishlistItem } from './useWishlist.ts'

vi.mock('../shop/ShopContext.tsx', () => ({ useShop: () => ({ cartCount: 0 }) }))

const entry: WishlistItem = {
  product: { productModelId: 31, name: '데님 백', brandId: 5, brandName: 'REWEAVE', price: 39000,
    category: 'BAG', subcategory: 'TOTE', status: 'ON_SALE', stockQuantity: 3,
    thumbnailUrl: '/api/v1/product-models/31/thumbnail', description: '', createdAt: '2026-09-17', journeyUrl: null },
  wishedAt: '2026-09-17T14:02:11', purchasable: true,
}
const result = { content: [entry], page: 0, size: 12, totalElements: 13, totalPages: 2, hasNext: true }
function response(data: unknown) { return { data } as AxiosResponse }
function login(role: UserRole = 'CONSUMER', id = 7) {
  useAuthStore.getState().signIn({ id, name: '소비자', email: 'test@test.com', role, walletAddress: null }, `token-${id}`)
}
function Probe() {
  const location = useLocation()
  return <output aria-label="위치" data-state={JSON.stringify(location.state)}>{location.pathname}{location.search}</output>
}
function show(path = '/shop', client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/shop" element={<><WishlistButton productId={31} name="상품 A" /><WishlistButton productId={31} name="상품 B" /><Link to="/search?q=데님">검색 이동</Link></>} />
    <Route path="/search" element={<WishlistButton productId={31} name="검색 상품" />} />
    <Route element={<RoleGuard allow={['CONSUMER']} />}><Route path="/mypage/wishlist" element={<ConsumerWishlistPage />} /></Route>
    <Route path="/login" element={<p>로그인 화면</p>} /><Route path="/403" element={<p>접근 불가</p>} />
  </Routes><Probe /></MemoryRouter></QueryClientProvider>)
  return client
}
beforeEach(() => {
  useAuthStore.getState().signOut()
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  vi.spyOn(apiClient, 'get').mockImplementation(async (url) => {
    if (url.endsWith('/product-ids')) return response([31])
    if (url.endsWith('/thumbnail')) return response(new Blob(['image']))
    return response(result)
  })
  vi.spyOn(apiClient, 'post').mockResolvedValue(response({}))
  vi.spyOn(apiClient, 'delete').mockResolvedValue(response({}))
})
afterEach(() => { cleanup(); useAuthStore.getState().signOut(); vi.restoreAllMocks() })

describe('server wishlist', () => {
  it('does not request ids while signed out and preserves the login return path', () => {
    show('/search?q=데님')
    const heart = screen.getByRole('button', { name: '검색 상품 찜하기' })
    expect(heart).toHaveAttribute('aria-pressed', 'false')
    expect(apiClient.get).not.toHaveBeenCalled()
    fireEvent.click(heart)
    expect(screen.getByLabelText('위치')).toHaveTextContent('/login')
    expect(JSON.parse(screen.getByLabelText('위치').getAttribute('data-state')!).from.pathname).toBe('/search?q=데님')
    expect(apiClient.post).not.toHaveBeenCalled()
  })
  it('fetches ids once and shares state across duplicate cards and route navigation', async () => {
    login(); show()
    expect(await screen.findByRole('button', { name: '상품 A 찜 해제' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '상품 B 찜 해제' })).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(screen.getByRole('link', { name: '검색 이동' }))
    expect(await screen.findByRole('button', { name: '검색 상품 찜 해제' })).toHaveAttribute('aria-pressed', 'true')
    expect(apiClient.get).toHaveBeenCalledTimes(1)
  })
  it('waits for success, suppresses duplicate clicks, then updates every heart', async () => {
    login()
    vi.mocked(apiClient.get).mockResolvedValue(response([]))
    let finish!: (data: AxiosResponse) => void
    vi.mocked(apiClient.post).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    show()
    const a = await screen.findByRole('button', { name: '상품 A 찜하기' })
    await waitFor(() => expect(a).toBeEnabled())
    const b = screen.getByRole('button', { name: '상품 B 찜하기' })
    fireEvent.click(a); fireEvent.click(b); fireEvent.click(a)
    await waitFor(() => expect(apiClient.post).toHaveBeenCalledTimes(1))
    expect(a).toHaveAttribute('aria-pressed', 'false')
    await act(async () => finish(response({})))
    expect(await screen.findByRole('button', { name: '상품 A 찜 해제' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '상품 B 찜 해제' })).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(screen.getByRole('button', { name: '상품 A 찜 해제' }))
    await screen.findByRole('button', { name: '상품 A 찜하기' })
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/consumers/me/wishlist/31')
  })
  it('keeps the existing heart on failure and permits retry', async () => {
    login(); vi.mocked(apiClient.delete).mockRejectedValueOnce(new Error('offline'))
    show()
    fireEvent.click(await screen.findByRole('button', { name: '상품 A 찜 해제' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('찜 변경에 실패')
    expect(screen.getByRole('button', { name: '상품 A 찜 해제' })).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(screen.getByRole('button', { name: '상품 A 찜 해제' }))
    await screen.findByRole('button', { name: '상품 A 찜하기' })
    expect(apiClient.delete).toHaveBeenCalledTimes(2)
  })
  it('does not mutate if the id lookup failed', async () => {
    login(); vi.mocked(apiClient.get).mockRejectedValue(new Error('offline')); show()
    await screen.findAllByRole('alert')
    fireEvent.click(screen.getByRole('button', { name: '상품 A 찜하기' }))
    expect(apiClient.post).not.toHaveBeenCalled()
  })
  it('does not request consumer ids for a business account', () => {
    login('BRAND'); show()
    fireEvent.click(screen.getByRole('button', { name: '상품 A 찜하기' }))
    expect(screen.getByRole('alert')).toHaveTextContent('소비자 계정')
    expect(apiClient.get).not.toHaveBeenCalled(); expect(apiClient.post).not.toHaveBeenCalled()
  })
  it.each([['DISCONTINUED', 5, '판매 종료'], ['SOLD_OUT', 3, '품절'], ['ON_SALE', 0, '품절'], ['ON_SALE', 5, '구매 불가']] as const)('keeps unavailable %s/%s items visible with %s and permits removal', async (status, stockQuantity, label) => {
      login()
      const item = { ...entry, purchasable: false, product: { ...entry.product, status, stockQuantity } }
      vi.mocked(apiClient.get).mockImplementation(async (url) => url.endsWith('/product-ids') ? response([31]) : url.endsWith('/thumbnail') ? response(new Blob(['image'])) : response({ ...result, content: [item] }))
      show('/mypage/wishlist')
      const card = await screen.findByRole('article', { name: '데님 백 상품' })
      expect(within(card).getByText(label)).toBeInTheDocument()
      expect(within(card).queryByRole('link')).not.toBeInTheDocument()
      expect(within(card).getByText('₩ 39,000')).toBeInTheDocument()
      fireEvent.click(within(card).getByRole('button', { name: '데님 백 찜 해제' }))
      await waitFor(() => expect(apiClient.delete).toHaveBeenCalledWith('/v1/consumers/me/wishlist/31'))
    })
  it('reuses authenticated blob images and revokes object URLs on unmount', async () => {
    login(); show('/mypage/wishlist')
    const image = await screen.findByRole('img', { name: 'REWEAVE 데님 백' })
    expect(image.getAttribute('src')).toMatch(/^blob:/)
    expect(apiClient.get).toHaveBeenCalledWith('/v1/product-models/31/thumbnail', { responseType: 'blob' })
    expect(apiClient.get).not.toHaveBeenCalledWith('/v1/public/product-models/31/thumbnail', expect.anything())
    const src = image.getAttribute('src')
    const revoke = vi.spyOn(URL, 'revokeObjectURL')
    cleanup()
    expect(revoke).toHaveBeenCalledWith(src)
  })
  it('requests size 12 and 0-based pages without re-fetching ids', async () => {
    login(); show('/mypage/wishlist')
    await screen.findByRole('article', { name: '데님 백 상품' })
    fireEvent.click(within(screen.getByRole('navigation', { name: '찜 목록 페이지' })).getByRole('button', { name: '다음 페이지' }))
    await waitFor(() => expect(apiClient.get).toHaveBeenCalledWith('/v1/consumers/me/wishlist', expect.objectContaining({ params: { page: 1, size: 12 } })))
    expect(screen.getByLabelText('위치')).toHaveTextContent('/mypage/wishlist?page=1')
    expect(vi.mocked(apiClient.get).mock.calls.filter(([url]) => url.endsWith('/product-ids'))).toHaveLength(1)
  })
  it('shows empty state with a consumer SHOP destination', async () => {
    login(); vi.mocked(apiClient.get).mockResolvedValue(response({ ...result, content: [], totalElements: 0, totalPages: 0, hasNext: false }))
    show('/mypage/wishlist')
    expect(await screen.findByText('찜한 상품이 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '상품 둘러보기' })).toHaveAttribute('href', '/shop')
  })
  it('shows a loading state without mock products', () => {
    login(); vi.mocked(apiClient.get).mockImplementation(() => new Promise(() => {}))
    show('/mypage/wishlist')
    expect(screen.getByText('찜한 상품을 불러오는 중')).toHaveAttribute('role', 'status')
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })
  it('offers the first page after the last item on a later page is removed', async () => {
    login(); vi.mocked(apiClient.get).mockResolvedValue(response({ ...result, page: 1, content: [], totalElements: 12, totalPages: 1, hasNext: false }))
    show('/mypage/wishlist?page=1')
    fireEvent.click(await screen.findByRole('button', { name: '첫 페이지 보기' }))
    await waitFor(() => expect(apiClient.get).toHaveBeenCalledWith('/v1/consumers/me/wishlist', expect.objectContaining({ params: { page: 0, size: 12 } })))
    expect(screen.getByLabelText('위치')).not.toHaveTextContent('?page=1')
  })
  it('ignores a mutation response from a previous account', async () => {
    login()
    let finish!: (data: AxiosResponse) => void
    vi.mocked(apiClient.delete).mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    show('/shop', appQueryClient)
    fireEvent.click(await screen.findByRole('button', { name: '상품 A 찜 해제' }))
    await waitFor(() => expect(apiClient.delete).toHaveBeenCalledTimes(1))
    await act(async () => login('CONSUMER', 8))
    await screen.findByRole('button', { name: '상품 A 찜 해제' })
    await act(async () => finish(response({})))
    expect(appQueryClient.getQueryData(wishlistKeys.ids(7))).toBeUndefined()
    expect(appQueryClient.getQueryData(wishlistKeys.ids(8))).toEqual([31])
  })
  it('retries a failed list request', async () => {
    login(); vi.mocked(apiClient.get).mockRejectedValueOnce(createAppError(ERROR_KIND.API_ERROR, { status: 500 }))
    show('/mypage/wishlist')
    expect(await screen.findByRole('alert')).toHaveTextContent('찜 목록을 불러오지 못')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('article', { name: '데님 백 상품' })).toBeInTheDocument()
  })
  it.each([['signed out', null, '/login'], ['business account', 'BRAND', '/403']] as const)('guards the list for %s', (_name, role, target) => {
      if (role) login(role)
      show('/mypage/wishlist')
      expect(screen.getByLabelText('위치')).toHaveTextContent(target)
      expect(apiClient.get).not.toHaveBeenCalled()
    })
  it('drops cached ids on logout and fetches the new account without leaking hearts', async () => {
    login(); show('/shop', appQueryClient)
    await screen.findByRole('button', { name: '상품 A 찜 해제' })
    await act(async () => useAuthStore.getState().signOut())
    expect(screen.getByRole('button', { name: '상품 A 찜하기' })).toHaveAttribute('aria-pressed', 'false')
    expect(appQueryClient.getQueryData(wishlistKeys.ids(7))).toBeUndefined()
    vi.mocked(apiClient.get).mockResolvedValue(response([]))
    await act(async () => login('CONSUMER', 8))
    await waitFor(() => expect(screen.getByRole('button', { name: '상품 A 찜하기' })).toBeEnabled())
    expect(screen.getByRole('button', { name: '상품 A 찜하기' })).toHaveAttribute('aria-pressed', 'false')
  })
})
