import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { getExistingProductOrder } from './orderCheckout.ts'

afterEach(() => vi.restoreAllMocks())

describe('existing single order lookup', () => {
  it('uses the real owner list endpoint and follows pagination', async () => {
    const order = { orderId: 12, status: 'CREATED' }
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: { content: [{ orderId: 99 }], hasNext: true } } as never)
      .mockResolvedValueOnce({ data: { content: [order], hasNext: false } } as never)
    await expect(getExistingProductOrder(12)).resolves.toBe(order)
    expect(get.mock.calls).toEqual([
      ['/v1/product-orders/me', { params: { page: 0, size: 100 } }],
      ['/v1/product-orders/me', { params: { page: 1, size: 100 } }],
    ])
  })
  it('only reports missing after searching every page', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { content: [], hasNext: false } } as never)
    await expect(getExistingProductOrder(12)).rejects.toMatchObject({ code: 'CHECKOUT_ORDER_NOT_FOUND' })
  })
  it('preserves network or endpoint errors rather than calling them expiry', async () => {
    const error = new Error('unavailable')
    vi.spyOn(apiClient, 'get').mockRejectedValue(error)
    await expect(getExistingProductOrder(12)).rejects.toBe(error)
  })
})
