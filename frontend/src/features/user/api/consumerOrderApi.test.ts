import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { getConsumerOrderThumbnail } from './getConsumerOrderThumbnail.ts'
import { getMyProductOrder, getMyProductOrders } from './getMyProductOrders.ts'
import { getMyProductOrderSummary } from './getMyProductOrderSummary.ts'

afterEach(() => vi.restoreAllMocks())

describe('consumer order API', () => {
  it('requests the authenticated consumer order page with filters', async () => {
    const page = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    }
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: page } as AxiosResponse)

    await expect(getMyProductOrders({ status: 'PAID', page: 0, size: 20 })).resolves.toBe(page)
    expect(get).toHaveBeenCalledWith('/v1/product-orders/me', {
      params: { status: 'PAID', page: 0, size: 20 },
    })
  })

  it('requests one authenticated consumer order by id', async () => {
    const order = { orderId: 12 }
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: order } as AxiosResponse)

    await expect(getMyProductOrder(12)).resolves.toBe(order)
    expect(get).toHaveBeenCalledWith('/v1/product-orders/12')
  })

  it('requests the authenticated consumer order summary', async () => {
    const summary = { created: 1, paid: 2, shipped: 3, delivered: 4 }
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: summary } as AxiosResponse)

    await expect(getMyProductOrderSummary()).resolves.toBe(summary)
    expect(get).toHaveBeenCalledWith('/v1/product-orders/me/summary')
  })

  it('normalizes the backend thumbnail path before requesting its blob', async () => {
    const blob = new Blob(['image'])
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: blob } as AxiosResponse)

    await expect(getConsumerOrderThumbnail('/api/v1/product-models/10/thumbnail')).resolves.toBe(blob)
    expect(get).toHaveBeenCalledWith('/v1/product-models/10/thumbnail', {
      responseType: 'blob',
    })
  })
})
