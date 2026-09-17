import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProductOrderReturnPage } from './ProductOrderReturnPage.tsx'
import { approveProductOrderPayment, cancelProductOrder } from '../../features/user/api/productOrderPaymentApi.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
vi.mock('../../features/user/api/productOrderPaymentApi.ts', () => ({ approveProductOrderPayment: vi.fn(), cancelProductOrder: vi.fn() }))
afterEach(() => { cleanup(); vi.resetAllMocks() })
describe('single payment return server group hint', () => {
  it.each(['?pg_token=token', ''])('redirects %s without list preflight or a group approve/cancel', async suffix => {
    const error = createAppError(ERROR_KIND.API_ERROR, { code: 'PRODUCT_ORDER_BELONGS_TO_GROUP', status: 409, details: { orderGroupId: 3, orderGroupCode: 'ORG-3' } })
    vi.mocked(approveProductOrderPayment).mockRejectedValue(error); vi.mocked(cancelProductOrder).mockRejectedValue(error)
    render(<QueryClientProvider client={new QueryClient()}><MemoryRouter initialEntries={[`/orders/1/payment/return${suffix}`]}><Routes><Route path="/orders/:orderId/payment/return" element={<ProductOrderReturnPage />} /><Route path="/order-groups/3" element={<p>일반 묶음 화면</p>} /></Routes></MemoryRouter></QueryClientProvider>)
    await screen.findByText('일반 묶음 화면')
    expect(approveProductOrderPayment).toHaveBeenCalledTimes(suffix ? 1 : 0)
    expect(cancelProductOrder).toHaveBeenCalledTimes(suffix ? 0 : 1)
  })
})
