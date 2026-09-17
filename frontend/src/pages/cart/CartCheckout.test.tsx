import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CartCheckout } from './CartCheckout.tsx'
import { checkoutCart } from '../../features/cart/checkout.ts'
import type { CartItem } from '../../features/cart/useCart.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
vi.mock('../../features/cart/checkout.ts', async importOriginal => ({ ...await importOriginal<object>(), checkoutCart: vi.fn() }))
vi.mock('../../features/user/hooks/useConsumerAddresses.ts', () => ({ useConsumerAddresses: () => ({ data: [{ addressId: 5, isDefault: true, alias: '집', receiverName: '고객', zipCode: '12345', address: '서울', receiverPhone: '010' }], isPending: false, isError: false }) }))
const row = { product: { productModelId: 1 }, quantity: 2, purchasable: true } as CartItem
const refresh = vi.fn().mockResolvedValue(undefined)
const issues = vi.fn()
function Position() { return <output>{useLocation().pathname}</output> }
function show() { render(<MemoryRouter><CartCheckout items={[row]} total={20000} blocked={false} refresh={refresh} onIssues={issues} onBusy={vi.fn()} /><Position /></MemoryRouter>); fireEvent.click(screen.getByRole('button', { name: '주문하기' })) }
beforeEach(() => { vi.mocked(checkoutCart).mockResolvedValue({ orderGroupId: 3 } as never); refresh.mockClear(); issues.mockClear() })
afterEach(() => { cleanup(); vi.mocked(checkoutCart).mockReset() })
describe('cart checkout confirmation', () => {
  it('sends selected IDs, chosen address and displayed total without quantity', async () => {
    show(); fireEvent.click(screen.getByRole('button', { name: '선택한 배송지로 결제 진행' }))
    await screen.findByText('/order-groups/3'); expect(checkoutCart).toHaveBeenCalledWith([1], 5, 20000)
  })
  it('locks rapid duplicate checkout clicks', async () => {
    vi.mocked(checkoutCart).mockImplementation(() => new Promise(() => {})); show()
    const button = screen.getByRole('button', { name: '선택한 배송지로 결제 진행' }); fireEvent.click(button); fireEvent.click(button)
    expect(checkoutCart).toHaveBeenCalledTimes(1); expect(button).toBeDisabled()
  })
  it.each(['CART_CHECKOUT_PRICE_CHANGED', 'CART_ITEM_NOT_FOUND'])('refreshes and requires review for %s, including empty details', async code => {
    vi.mocked(checkoutCart).mockRejectedValue(createAppError(ERROR_KIND.API_ERROR, { code, details: [], message: '변경 확인 필요' })); show()
    fireEvent.click(screen.getByRole('button', { name: '선택한 배송지로 결제 진행' }))
    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1)); expect(screen.getByRole('button', { name: '선택한 배송지로 결제 진행' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '변경된 항목과 합계 확인' })); expect(screen.getByRole('button', { name: '선택한 배송지로 결제 진행' })).toBeEnabled()
  })
  it('preserves stock problem details for per-item rendering', async () => {
    vi.mocked(checkoutCart).mockRejectedValue(createAppError(ERROR_KIND.API_ERROR, { code: 'PRODUCT_ORDER_ITEMS_OUT_OF_STOCK', details: [{ productModelId: 1, requested: 3, available: 1 }] })); show()
    fireEvent.click(screen.getByRole('button', { name: '선택한 배송지로 결제 진행' }))
    await waitFor(() => expect(issues).toHaveBeenCalledWith({ 1: expect.stringContaining('남은 재고 1개') }))
  })
})
