import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ShopProvider } from '../../features/shop/ShopContext.tsx'
import { useAuthStore } from '../../stores/authStore.ts'
import { approveGroup, cancelGroup, getGroup, readyGroup, type OrderGroup } from '../../features/cart/checkout.ts'
import { OrderGroupPage } from './OrderGroupPage.tsx'
import { apiClient } from '../../services/apiClient.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
vi.mock('../../features/cart/checkout.ts', async importOriginal => ({ ...await importOriginal<object>(), getGroup: vi.fn(), approveGroup: vi.fn(), cancelGroup: vi.fn(), readyGroup: vi.fn() }))
const group: OrderGroup = { orderGroupId: 3, orderGroupCode: 'ORG-3', totalPrice: 20000, status: 'CREATED', paidAt: null, orderedAt: '2026-09-17', cancelable: true,
  orders: [{ orderId: 1, orderCode: 'ORD-1', orderGroupCode: 'ORG-3', brandName: '브랜드 A', totalPrice: 10000, status: 'CREATED', orderedAt: '2026-09-17', paidAt: null, shippedAt: null, deliveredAt: null, items: [] },
    { orderId: 2, orderCode: 'ORD-2', orderGroupCode: 'ORG-3', brandName: '브랜드 B', totalPrice: 10000, status: 'CREATED', orderedAt: '2026-09-17', paidAt: null, shippedAt: null, deliveredAt: null, items: [] }] }
function show(returning = false, suffix = '') { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ShopProvider><MemoryRouter initialEntries={[`/order-groups/3${returning ? '/payment/return' : ''}${suffix}`]}><Routes><Route path={returning ? '/order-groups/:orderGroupId/payment/return' : '/order-groups/:orderGroupId'} element={<OrderGroupPage returning={returning} />} /></Routes></MemoryRouter></ShopProvider></QueryClientProvider>) }
function Position() { const location = useLocation(); return <output aria-label="현재 위치">{location.pathname}{location.search}</output> }
function showSingle(returning = false, suffix = '') {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ShopProvider><MemoryRouter initialEntries={[`/orders/1/${returning ? 'payment/return' : 'checkout'}${suffix}`]}><Position /><Routes><Route path="/orders/:orderId/checkout" element={<OrderGroupPage single />} /><Route path="/orders/:orderId/payment/return" element={<OrderGroupPage single returning />} /><Route path="/order-groups/:orderGroupId" element={<OrderGroupPage />} /></Routes></MemoryRouter></ShopProvider></QueryClientProvider>)
}
beforeEach(() => {
  useAuthStore.getState().signIn({ id: 1, name: '고객', email: 'c@test.com', role: 'CONSUMER', walletAddress: null }, 'token')
  vi.mocked(getGroup).mockResolvedValue(group); vi.mocked(approveGroup).mockResolvedValue({ ...group, status: 'PAID' }); vi.mocked(cancelGroup).mockResolvedValue(undefined)
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { items: [], totalPrice: 0, totalQuantity: 0 } } as never)
})
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.resetAllMocks(); useAuthStore.getState().signOut() })
describe('order group checkout', () => {
  it('uses consumer-facing order wording without inventory reservation details', async () => {
    show()
    await screen.findByText('ORG-3')
    expect(screen.getByRole('heading', { name: '주문 확인' })).toBeInTheDocument()
    expect(screen.getByText('배송은 브랜드별로 진행됩니다.')).toBeInTheDocument()
    expect(screen.queryByText(/재고가 예약/)).not.toBeInTheDocument()
    expect(screen.queryByText('묶음 주문')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '주문 내역 보기' })).not.toBeInTheDocument()
  })
  it.each(['?pg_token=token', ''])('does not approve or cancel an already paid return (%s)', async suffix => {
    vi.mocked(getGroup).mockResolvedValue({ ...group, status: 'PAID' }); show(true, suffix)
    await screen.findByRole('heading', { name: '주문·결제 완료' })
    expect(approveGroup).not.toHaveBeenCalled(); expect(cancelGroup).not.toHaveBeenCalled()
  })
  it('recovers paid status after duplicate approval error', async () => {
    vi.mocked(getGroup).mockResolvedValueOnce(group).mockResolvedValue({ ...group, status: 'PAID' })
    vi.mocked(approveGroup).mockRejectedValue(createAppError(ERROR_KIND.API_ERROR, { code: 'PAYMENT_STATE_INVALID', status: 409 }))
    show(true, '?pg_token=token'); await screen.findByRole('heading', { name: '주문·결제 완료' }); expect(approveGroup).toHaveBeenCalledTimes(1)
  })
  it('keeps an unpaid token-less return and allows resuming or explicit cancellation', async () => {
    show(true); await screen.findByText(/결제가 완료되지 않았습니다/)
    expect(cancelGroup).not.toHaveBeenCalled(); expect(approveGroup).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '결제하기' })).toBeEnabled()
    expect(screen.getByRole('button', { name: '주문 전체 취소' })).toBeEnabled()
  })
  it('removes payment actions when the latest read says the order disappeared', async () => {
    vi.mocked(getGroup).mockResolvedValueOnce(group).mockRejectedValue(createAppError(ERROR_KIND.API_ERROR, { status: 404 }))
    show(); await screen.findByText('ORG-3'); fireEvent.click(screen.getByRole('button', { name: '결제하기' }))
    await screen.findByText(/주문을 확인할 수 없습니다/)
    expect(readyGroup).not.toHaveBeenCalled(); expect(screen.queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument()
  })
  it('does not prepare payment if the order became paid before the click', async () => {
    vi.mocked(getGroup).mockResolvedValueOnce(group).mockResolvedValue({ ...group, status: 'PAID' })
    show(); await screen.findByText('ORG-3'); fireEvent.click(screen.getByRole('button', { name: '결제하기' }))
    await screen.findByRole('heading', { name: '주문·결제 완료' }); expect(readyGroup).not.toHaveBeenCalled()
  })
  it('handles disappearance during payment preparation', async () => {
    vi.mocked(readyGroup).mockRejectedValue(createAppError(ERROR_KIND.API_ERROR, { status: 404 }))
    show(); await screen.findByText('ORG-3'); fireEvent.click(screen.getByRole('button', { name: '결제하기' }))
    await screen.findByText(/주문을 확인할 수 없습니다/)
    expect(screen.queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument()
  })
  it('shows every brand and total in whole-group cancellation confirmation', async () => {
    show(); await screen.findByText(/ORG-3/); fireEvent.click(screen.getByRole('button', { name: '주문 전체 취소' }))
    const dialog = screen.getByRole('dialog'); expect(dialog).toHaveTextContent('브랜드 A'); expect(dialog).toHaveTextContent('브랜드 B'); expect(dialog).toHaveTextContent('20,000원'); expect(dialog).toHaveTextContent('부분 취소 불가')
    fireEvent.click(screen.getByRole('button', { name: '전체 취소 확인' })); await waitFor(() => expect(cancelGroup).toHaveBeenCalledTimes(1))
  })
  it('disables cancellation based on server cancelable', async () => {
    vi.mocked(getGroup).mockResolvedValue({ ...group, cancelable: false }); show(); await screen.findByText(/ORG-3/); expect(screen.getByRole('button', { name: '주문 전체 취소' })).toBeDisabled()
  })
  it('supports explicit mock payment without a real redirect', async () => {
    vi.mocked(readyGroup).mockResolvedValue({ orderGroupId: 3, tid: 'mock', redirectUrl: '', totalPrice: 20000, live: false }); show(); await screen.findByText(/ORG-3/)
    fireEvent.click(screen.getByRole('button', { name: '결제하기' })); const approve = await screen.findByRole('button', { name: '결제 승인 (시연)' }); await waitFor(() => expect(approve).toBeEnabled()); expect(screen.getByRole('dialog')).toHaveTextContent('실제 금액은 청구되지 않습니다.'); fireEvent.click(approve)
    await screen.findByRole('heading', { name: '주문·결제 완료' }); expect(approveGroup).toHaveBeenCalledWith(3, 'MOCK-PG-TOKEN')
  })
  it('resumes the existing single order without creating another order', async () => {
    vi.mocked(apiClient.get).mockImplementation(async url => ({ data: url === '/v1/product-orders/me' ? { content: [{ ...group.orders[0], orderGroupCode: null }], hasNext: false } : { items: [] } }) as never)
    const post = vi.spyOn(apiClient, 'post').mockImplementation(async url => ({ data: url.endsWith('/ready') ? { live: false, redirectUrl: '', tid: 'mock' } : { ...group.orders[0], status: 'PAID', orderGroupCode: null } }) as never)
    showSingle(true); await screen.findByText(/결제가 완료되지 않았습니다/)
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '결제하기' }))
    const approve = await screen.findByRole('button', { name: '결제 승인 (시연)' }); await waitFor(() => expect(approve).toBeEnabled()); fireEvent.click(approve)
    await screen.findByRole('heading', { name: '주문·결제 완료' })
    expect(post.mock.calls.map(([url]) => url)).toEqual(['/v1/product-orders/1/payment/ready', '/v1/product-orders/1/payment/approve'])
    expect(readyGroup).not.toHaveBeenCalled()
  })
  it('uses the server group ID hint instead of parsing the displayed group code', async () => {
    vi.mocked(apiClient.get).mockImplementation(async url => ({ data: url === '/v1/product-orders/me' ? { content: [{ ...group.orders[0], orderGroupCode: 'CHANGED-CODE' }], hasNext: false } : { items: [] } }) as never)
    vi.spyOn(apiClient, 'post').mockRejectedValue(createAppError(ERROR_KIND.API_ERROR, { status: 409, code: 'PRODUCT_ORDER_BELONGS_TO_GROUP', details: { orderGroupId: 3 } }))
    showSingle(); await screen.findAllByText('ORD-1'); fireEvent.click(screen.getByRole('button', { name: '결제하기' }))
    await waitFor(() => expect(screen.getByLabelText('현재 위치')).toHaveTextContent('/order-groups/3'))
    await screen.findByText('ORG-3'); expect(readyGroup).not.toHaveBeenCalled()
  })
  it('only cancels a single order after explicit confirmation', async () => {
    vi.mocked(apiClient.get).mockImplementation(async url => ({ data: url === '/v1/product-orders/me' ? { content: [{ ...group.orders[0], orderGroupCode: null }], hasNext: false } : { items: [] } }) as never)
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: undefined } as never)
    showSingle(false, '?action=cancel'); await screen.findByRole('dialog')
    expect(post).not.toHaveBeenCalled(); fireEvent.click(screen.getByRole('button', { name: '주문 취소 확인' }))
    await screen.findByRole('heading', { name: '주문이 취소되었습니다' })
    expect(post).toHaveBeenCalledWith('/v1/product-orders/1/cancel', { reason: '구매자 주문 취소' })
  })
})
