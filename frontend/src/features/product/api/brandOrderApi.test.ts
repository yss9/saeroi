// 발송 처리 요청이 서버로 나가는 모양.
//
// **빈 값을 그대로 보내면 안 된다.** 서버는 빈 문자열을 "택배사를 적었다" 로 읽을 수 있고,
// 그러면 조회할 수 없는 송장이 저장된다. 화면의 입력 칸은 언제나 문자열을 주므로(빈 폼도
// `''`) 다듬는 자리는 여기다.

import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { shipBrandOrder } from './brandOrderApi.ts'
import type { BrandOrderShipResult } from '../types/brandOrder.types.ts'

const SHIPPED: BrandOrderShipResult = {
  orderId: 12,
  orderCode: 'ORD-00000012',
  status: 'SHIPPED',
  shippedAt: '2026-09-15T14:00:00',
  carrier: null,
  trackingNumber: null,
}

function mockPatch() {
  return vi
    .spyOn(apiClient, 'patch')
    .mockResolvedValue({ data: SHIPPED } as AxiosResponse<BrandOrderShipResult>)
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('shipBrandOrder', () => {
  // 직접 전달·퀵처럼 송장이 없는 방식이 있다. 빈 문자열이 아니라 아예 빼고 보낸다.
  it('빈 송장은 요청에서 뺀다', async () => {
    const patch = mockPatch()

    await shipBrandOrder(12, { carrier: '', trackingNumber: '' })

    expect(patch).toHaveBeenCalledWith('/v1/product-orders/12/ship', {})
  })

  it('공백만 적은 값도 뺀다', async () => {
    const patch = mockPatch()

    await shipBrandOrder(12, { carrier: '   ', trackingNumber: '  ' })

    expect(patch).toHaveBeenCalledWith('/v1/product-orders/12/ship', {})
  })

  it('입력한 값은 앞뒤 공백을 떼고 보낸다', async () => {
    const patch = mockPatch()

    await shipBrandOrder(12, { carrier: ' CJ대한통운 ', trackingNumber: ' 123456789012 ' })

    expect(patch).toHaveBeenCalledWith('/v1/product-orders/12/ship', {
      carrier: 'CJ대한통운',
      trackingNumber: '123456789012',
    })
  })
})
