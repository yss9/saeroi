import { useQuery } from '@tanstack/react-query'
import type { ProductOrderStatus } from '../../product/types/product.types.ts'
import { consumerOrderKeys } from '../api/consumerOrderKeys.ts'
import { getMyProductOrders } from '../api/getMyProductOrders.ts'
import { getMyProductOrderSummary } from '../api/getMyProductOrderSummary.ts'
import type {
  ConsumerMyPageData,
  ConsumerOrderViewStatus,
  ConsumerProfileSummary,
} from '../types/consumerMyPage.types.ts'
import type {
  ConsumerOrderPage,
  ConsumerOrderStatusSummaryResponse,
} from '../types/consumerOrder.types.ts'

const RECENT_ORDER_SIZE = 20

/** 로그인 정보와 실제 주문 API 두 건을 마이페이지 View Model로 조립한다. */
export function useConsumerMyPage(profile: ConsumerProfileSummary | null) {
  const enabled = profile !== null
  const orders = useQuery({
    queryKey: consumerOrderKeys.list({ page: 0, size: RECENT_ORDER_SIZE }),
    queryFn: () => getMyProductOrders({ page: 0, size: RECENT_ORDER_SIZE }),
    enabled,
  })
  const summary = useQuery({
    queryKey: [...consumerOrderKeys.all, 'summary'],
    queryFn: getMyProductOrderSummary,
    enabled,
  })

  const data = profile && orders.data && summary.data
    ? toConsumerMyPageData(profile, orders.data, summary.data)
    : null

  return {
    data,
    isPending: enabled && (orders.isPending || summary.isPending),
    isError: orders.isError || summary.isError,
    error: orders.error ?? summary.error,
    refetch: () => Promise.all([orders.refetch(), summary.refetch()]),
  }
}

/** Backend 도메인 상태를 소비자 화면 문구에 맞는 상태로 변환한다. */
function toViewStatus(status: ProductOrderStatus): ConsumerOrderViewStatus {
  switch (status) {
    case 'CREATED':
      return 'PAYMENT_PENDING'
    case 'PAID':
      return 'PREPARING'
    case 'SHIPPED':
      return 'SHIPPING'
    case 'DELIVERED':
      return 'DELIVERED'
  }
}

export function toConsumerMyPageData(
  profile: ConsumerProfileSummary,
  orders: ConsumerOrderPage,
  summary: ConsumerOrderStatusSummaryResponse,
): ConsumerMyPageData {
  return {
    profile,
    orderStages: [
      { stage: 'PAYMENT_PENDING', count: summary.created },
      { stage: 'PREPARING', count: summary.paid },
      { stage: 'SHIPPING', count: summary.shipped },
      { stage: 'DELIVERED', count: summary.delivered },
    ],
    recentOrders: orders.content.flatMap((order) => order.items.map((item) => ({
      id: `${order.orderCode}:${item.itemCode}`,
      orderNumber: order.orderCode,
      orderedAt: order.orderedAt,
      productName: item.productName,
      brandName: order.brandName,
      thumbnailUrl: item.imageUrl,
      thumbnailAlt: `${item.productName} 대표 이미지`,
      itemCode: item.itemCode,
      itemCount: order.items.length,
      itemPrice: item.price,
      totalAmount: order.totalPrice,
      status: toViewStatus(order.status),
      journeyUrl: item.journeyUrl,
    }))),
    // 현재 Backend에는 consumer_address 조회 API가 없다. 목 주소를 보여주는 것보다
    // 빈 상태를 정확히 표시하고 배송지 API가 추가될 때 연결하는 편이 안전하다.
    defaultAddress: null,
  }
}
