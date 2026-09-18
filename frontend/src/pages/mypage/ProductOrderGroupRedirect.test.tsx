import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProductOrderReturnPage } from './ProductOrderReturnPage.tsx'
import { approveProductOrderPayment, cancelProductOrder, readyProductOrderPayment } from '../../features/user/api/productOrderPaymentApi.ts'
import { getMyProductOrders } from '../../features/user/api/getMyProductOrders.ts'
import { approveGroup, cancelGroup, readyGroup } from '../../features/cart/checkout.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'

vi.mock('../../features/user/api/productOrderPaymentApi.ts', () => ({ approveProductOrderPayment: vi.fn(), cancelProductOrder: vi.fn(), readyProductOrderPayment: vi.fn() }))
vi.mock('../../features/user/api/getMyProductOrders.ts', () => ({ getMyProductOrders: vi.fn() }))
vi.mock('../../features/cart/checkout.ts', async importOriginal => ({ ...await importOriginal<object>(), approveGroup: vi.fn(), cancelGroup: vi.fn(), readyGroup: vi.fn() }))
vi.mock('../landing/components/Header.tsx', () => ({ Header: () => null }))
vi.mock('../landing/components/Footer.tsx', () => ({ Footer: () => null }))

const hint = createAppError(ERROR_KIND.API_ERROR, { code: 'PRODUCT_ORDER_BELONGS_TO_GROUP', status: 409, details: { orderGroupId: 3, orderGroupCode: 'CHANGED-CODE' } })

beforeEach(() => {
  vi.mocked(getMyProductOrders).mockResolvedValue({ content: [{ orderId: 1, orderCode: 'ORD-1', orderGroupCode: 'CHANGED-CODE', brandName: '브랜드', totalPrice: 10000, status: 'CREATED', orderedAt: '2026-09-18', paidAt: null, shippedAt: null, deliveredAt: null, items: [] }], page: 0, size: 100, totalElements: 1, totalPages: 1, hasNext: false })
})
afterEach(() => { cleanup(); vi.resetAllMocks() })

function show(suffix = '') {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter initialEntries={[`/orders/1/payment/return${suffix}`]}><Routes><Route path="/orders/:orderId/payment/return" element={<ProductOrderReturnPage />} /><Route path="/order-groups/3" element={<p>일반 묶음 화면</p>} /></Routes></MemoryRouter></QueryClientProvider>)
}
function expectNoGroupPayment() {
  expect(approveGroup).not.toHaveBeenCalled()
  expect(cancelGroup).not.toHaveBeenCalled()
  expect(readyGroup).not.toHaveBeenCalled()
}

describe('single payment return server group hint', () => {
  it('reads current status then follows the approval hint without automatically paying the group', async () => {
    vi.mocked(approveProductOrderPayment).mockRejectedValue(hint)
    show('?pg_token=token')
    await screen.findByText('일반 묶음 화면')
    expect(getMyProductOrders).toHaveBeenCalledWith({ page: 0, size: 100 })
    expect(approveProductOrderPayment).toHaveBeenCalledWith(1, 'token')
    expect(cancelProductOrder).not.toHaveBeenCalled()
    expectNoGroupPayment()
  })
  it('keeps a token-less return pending without payment or automatic cancellation', async () => {
    show()
    await screen.findByText(/결제가 완료되지 않았습니다/)
    expect(screen.getByRole('button', { name: '결제하기' })).toBeEnabled()
    expect(approveProductOrderPayment).not.toHaveBeenCalled()
    expect(cancelProductOrder).not.toHaveBeenCalled()
    expect(readyProductOrderPayment).not.toHaveBeenCalled()
    expectNoGroupPayment()
  })
  it('follows the ready hint only when the customer explicitly retries payment', async () => {
    vi.mocked(readyProductOrderPayment).mockRejectedValue(hint)
    show()
    await screen.findByText(/결제가 완료되지 않았습니다/)
    fireEvent.click(screen.getByRole('button', { name: '결제하기' }))
    await screen.findByText('일반 묶음 화면')
    expect(readyProductOrderPayment).toHaveBeenCalledWith(1)
    expect(cancelProductOrder).not.toHaveBeenCalled()
    expectNoGroupPayment()
  })
})
