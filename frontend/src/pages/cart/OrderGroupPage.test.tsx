import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
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
beforeEach(() => {
  useAuthStore.getState().signIn({ id: 1, name: '고객', email: 'c@test.com', role: 'CONSUMER', walletAddress: null }, 'token')
  vi.mocked(getGroup).mockResolvedValue(group); vi.mocked(approveGroup).mockResolvedValue({ ...group, status: 'PAID' }); vi.mocked(cancelGroup).mockResolvedValue(undefined)
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { items: [], totalPrice: 0, totalQuantity: 0 } } as never)
})
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.resetAllMocks(); useAuthStore.getState().signOut() })
describe('order group checkout', () => {
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
  it('cancels unpaid token-less return only', async () => {
    show(true); await screen.findByRole('heading', { name: '결제가 취소되었습니다' }); expect(cancelGroup).toHaveBeenCalledWith(3)
  })
  it('shows every brand and total in whole-group cancellation confirmation', async () => {
    show(); await screen.findByText(/ORG-3/); fireEvent.click(screen.getByRole('button', { name: '묶음 전체 취소' }))
    const dialog = screen.getByRole('dialog'); expect(dialog).toHaveTextContent('브랜드 A'); expect(dialog).toHaveTextContent('브랜드 B'); expect(dialog).toHaveTextContent('20,000원'); expect(dialog).toHaveTextContent('부분 취소 불가')
    fireEvent.click(screen.getByRole('button', { name: '전체 취소 확인' })); await waitFor(() => expect(cancelGroup).toHaveBeenCalledTimes(1))
  })
  it('disables cancellation based on server cancelable', async () => {
    vi.mocked(getGroup).mockResolvedValue({ ...group, cancelable: false }); show(); await screen.findByText(/ORG-3/); expect(screen.getByRole('button', { name: '묶음 전체 취소' })).toBeDisabled()
  })
  it('supports explicit mock payment without a real redirect', async () => {
    vi.mocked(readyGroup).mockResolvedValue({ orderGroupId: 3, tid: 'mock', redirectUrl: '', totalPrice: 20000, live: false }); show(); await screen.findByText(/ORG-3/)
    fireEvent.click(screen.getByRole('button', { name: '결제하기' })); const approve = await screen.findByRole('button', { name: '결제 승인 (시연)' }); await waitFor(() => expect(approve).toBeEnabled()); fireEvent.click(approve)
    await screen.findByRole('heading', { name: '주문·결제 완료' }); expect(approveGroup).toHaveBeenCalledWith(3, 'MOCK-PG-TOKEN')
  })
})
