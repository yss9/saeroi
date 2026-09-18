import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProductCheckoutPage } from './ProductCheckoutPage.tsx'
const data = vi.hoisted(() => ({ create: vi.fn(), addresses: [] as Array<{ addressId: number; alias: string; receiverName: string; receiverPhone: string; zipCode: string; address: string; isDefault: boolean }>, stock: 10 }))
vi.mock('../landing/components/Header.tsx', () => ({ Header: () => null }))
vi.mock('../landing/components/Footer.tsx', () => ({ Footer: () => null }))
vi.mock('../../features/product/hooks/usePublicProductModel.ts', () => ({ usePublicProductModel: () => ({ data: { name: '테스트 상품', price: 79000, status: 'ON_SALE', stockQuantity: data.stock }, isPending: false, isError: false }) }))
vi.mock('../../features/user/hooks/useConsumerAddresses.ts', () => ({ useConsumerAddresses: () => ({ data: data.addresses, isPending: false, isError: false }) }))
vi.mock('../../features/user/api/createProductOrder.ts', () => ({ createProductOrder: data.create }))
function Position() { return <output aria-label="현재 위치">{useLocation().pathname}</output> }
function show() { render(<QueryClientProvider client={new QueryClient()}><MemoryRouter initialEntries={['/products/31/checkout']}><Position /><Routes><Route path="/products/:productId/checkout" element={<ProductCheckoutPage />} /><Route path="/orders/:orderId/checkout" element={<p>생성된 주문 확인</p>} /></Routes></MemoryRouter></QueryClientProvider>) }
beforeEach(() => { data.create.mockReset().mockResolvedValue({ orderId: 12 }); data.stock = 10; data.addresses = [{ addressId: 9, alias: '집', receiverName: '고객', receiverPhone: '010', zipCode: '12345', address: '서울', isDefault: true }, { addressId: 10, alias: '회사', receiverName: '고객', receiverPhone: '010', zipCode: '12345', address: '부산', isDefault: false }] })
afterEach(cleanup)
describe('product address review', () => {
  it.each([true, false])('uses the default address or first address without leaving the page (%s)', async hasDefault => {
    data.addresses[0].isDefault = false; data.addresses[1].isDefault = hasDefault
    show()
    expect(screen.getAllByRole('radio')[hasDefault ? 1 : 0]).toBeChecked()
    fireEvent.click(screen.getByRole('button', { name: '주문 확인' }))
    await screen.findByText('생성된 주문 확인')
    expect(data.create).toHaveBeenCalledWith({ productModelId: 31, addressId: hasDefault ? 10 : 9 })
  })
  it('creates only after review with the selected address then opens existing-order checkout', async () => {
    show(); expect(data.create).not.toHaveBeenCalled()
    fireEvent.click(screen.getAllByRole('radio')[1]); fireEvent.click(screen.getByRole('button', { name: '주문 확인' }))
    await screen.findByText('생성된 주문 확인'); expect(data.create).toHaveBeenCalledWith({ productModelId: 31, addressId: 10 })
    expect(screen.getByLabelText('현재 위치')).toHaveTextContent('/orders/12/checkout')
  })
  it('blocks rapid duplicate order creation', async () => {
    data.create.mockImplementation(() => new Promise(() => {})); show()
    const button = screen.getByRole('button', { name: '주문 확인' }); fireEvent.click(button); fireEvent.click(button)
    await waitFor(() => expect(data.create).toHaveBeenCalledTimes(1)); expect(button).toBeDisabled()
  })
  it.each(['address', 'stock'])('blocks checkout with no %s', condition => {
    if (condition === 'address') data.addresses = []; else data.stock = 0
    show(); expect(screen.getByRole('button', { name: '주문 확인' })).toBeDisabled(); expect(data.create).not.toHaveBeenCalled()
  })
})
