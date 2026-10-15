import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../services/apiClient.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'
import { getExistingProductOrder } from './orderCheckout.ts'

afterEach(() => vi.restoreAllMocks())

describe('existing single order lookup', () => {
  it('uses the direct owner order endpoint', async () => {
    const order = { orderId: 12, status: 'CREATED' }
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: order } as never)
    await expect(getExistingProductOrder(12)).resolves.toBe(order)
    expect(get).toHaveBeenCalledOnce()
    expect(get).toHaveBeenCalledWith('/v1/product-orders/12')
  })
  it('keeps the existing not-found guidance for a 404', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue(createAppError(ERROR_KIND.API_ERROR, { status: 404 }))
    await expect(getExistingProductOrder(12)).rejects.toMatchObject({
      code: 'CHECKOUT_ORDER_NOT_FOUND',
      message: '주문 내역에서 이 주문을 찾을 수 없습니다. 주문 내역으로 돌아가 확인해 주세요.',
    })
  })
  it('preserves network or endpoint errors rather than calling them expiry', async () => {
    const error = new Error('unavailable')
    vi.spyOn(apiClient, 'get').mockRejectedValue(error)
    await expect(getExistingProductOrder(12)).rejects.toBe(error)
  })
})
