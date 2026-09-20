import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { ProductPurchasePanel } from './ProductPurchasePanel.tsx'
afterEach(cleanup)
function Position() { return <output aria-label="현재 위치">{useLocation().pathname}</output> }
describe('product purchase navigation', () => {
  it('navigates without a delivery or payment panel in product detail', () => {
    render(<MemoryRouter><ProductPurchasePanel productModelId={31} purchasable /><Position /></MemoryRouter>)
    expect(screen.queryByText('받을 곳')).not.toBeInTheDocument()
    expect(screen.queryByText(/카카오페이 결제창/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '구매하기' }))
    expect(screen.getByLabelText('현재 위치')).toHaveTextContent('/products/31/checkout')
  })
  it('keeps unavailable products disabled with the reason visible', () => {
    render(<MemoryRouter><ProductPurchasePanel productModelId={31} purchasable={false} blockedReason="품절입니다" /></MemoryRouter>)
    expect(screen.getByText('품절입니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '구매하기' })).toBeDisabled()
  })
})
