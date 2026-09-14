import { describe, expect, it } from 'vitest'
import type {
  ConsumerOrderPage,
  ConsumerOrderStatusSummaryResponse,
} from '../types/consumerOrder.types.ts'
import { toConsumerMyPageData } from './useConsumerMyPage.ts'

const orders: ConsumerOrderPage = {
  content: [
    {
      orderCode: 'PO-001',
      brandName: 'ReWeave',
      totalPrice: 178000,
      status: 'PAID',
      orderedAt: '2026-09-12T10:30:00',
      paidAt: '2026-09-12T10:35:00',
      shippedAt: null,
      deliveredAt: null,
      items: [
        {
          itemCode: 'ITEM-001',
          serialNumber: 1,
          productName: '데님 서클 백',
          category: 'BAG',
          price: 129000,
          imageUrl: '/api/v1/product-models/10/thumbnail',
          journeyUrl: 'http://localhost:5173/qr/first-token',
        },
        {
          itemCode: 'ITEM-002',
          serialNumber: 2,
          productName: '업사이클 파우치',
          category: 'ACCESSORY',
          price: 49000,
          imageUrl: '/api/v1/product-models/11/thumbnail',
          journeyUrl: 'http://localhost:5173/qr/second-token',
        },
      ],
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
}

const summary: ConsumerOrderStatusSummaryResponse = {
  created: 1,
  paid: 2,
  shipped: 3,
  delivered: 4,
}

describe('toConsumerMyPageData', () => {
  it('maps order counts and creates one journey entry per purchased product', () => {
    const data = toConsumerMyPageData(
      { id: 5, name: '새로이 고객', email: 'consumer@saeroi.test' },
      orders,
      summary,
    )

    expect(data.orderStages).toEqual([
      { stage: 'PAYMENT_PENDING', count: 1 },
      { stage: 'PREPARING', count: 2 },
      { stage: 'SHIPPING', count: 3 },
      { stage: 'DELIVERED', count: 4 },
    ])
    expect(data.recentOrders).toHaveLength(2)
    expect(data.recentOrders[0]).toMatchObject({
      id: 'PO-001:ITEM-001',
      status: 'PREPARING',
      itemCount: 2,
      journeyUrl: 'http://localhost:5173/qr/first-token',
    })
    expect(data.recentOrders[1]).toMatchObject({
      id: 'PO-001:ITEM-002',
      journeyUrl: 'http://localhost:5173/qr/second-token',
    })
    expect(data.defaultAddress).toBeNull()
  })
})
