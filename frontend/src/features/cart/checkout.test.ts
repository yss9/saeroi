import { describe, expect, it } from 'vitest'
import { checkoutIssues, selectedCart } from './checkout.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
import type { CartItem } from './useCart.ts'
describe('selected cart and checkout error data', () => {
  it('calculates current-price totals only for selected purchasable rows, without duplicate IDs', () => {
    const items = [{ product: { productModelId: 1, price: 300 }, quantity: 2, purchasable: true }, { product: { productModelId: 2, price: 100 }, quantity: 3, purchasable: false }] as CartItem[]
    expect(selectedCart(items, [1, 1, 2])).toMatchObject({ price: 600, quantity: 2 }); expect(selectedCart(items, []).price).toBe(0)
  })
  it.each([
    ['CART_CHECKOUT_ITEMS_UNAVAILABLE', { reason: 'HIDDEN_CATEGORY' }, '소비자 판매 대상'],
    ['CART_CHECKOUT_ITEMS_UNAVAILABLE', { reason: 'NOT_ON_SALE' }, '판매가 종료'],
    ['CART_CHECKOUT_PRICE_CHANGED', { addedPrice: 100, currentPrice: 200 }, '100원 → 현재 200원'],
    ['PRODUCT_ORDER_ITEMS_OUT_OF_STOCK', { requested: 3, available: 1 }, '남은 재고 1개'],
  ])('maps %s data to item messages', (code, detail, message) => {
    expect(checkoutIssues(createAppError(ERROR_KIND.API_ERROR, { code: code as string, details: [{ productModelId: 1, ...detail as object }] }))[1]).toContain(message)
  })
  it('tolerates missing, empty and malformed details', () => {
    expect(checkoutIssues(createAppError(ERROR_KIND.API_ERROR, { code: 'CART_CHECKOUT_PRICE_CHANGED', details: [] }))).toEqual({})
    expect(checkoutIssues({})).toEqual({})
  })
})
