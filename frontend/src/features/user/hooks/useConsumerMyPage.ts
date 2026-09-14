import { useQuery } from '@tanstack/react-query'
import type { ProductOrderStatus } from '../../product/types/product.types.ts'
import { consumerOrderKeys } from '../api/consumerOrderKeys.ts'
import { getMyProductOrders } from '../api/getMyProductOrders.ts'
import { getMyProductOrderSummary } from '../api/getMyProductOrderSummary.ts'
import { useConsumerAddresses } from './useConsumerAddresses.ts'
import type { ConsumerAddress } from '../types/consumerAddress.types.ts'
import type {
  ConsumerAddressSummary,
  ConsumerMyPageData,
  ConsumerOrderViewStatus,
  ConsumerProfileSummary,
} from '../types/consumerMyPage.types.ts'
import type {
  ConsumerOrderPage,
  ConsumerOrderStatusSummaryResponse,
} from '../types/consumerOrder.types.ts'

const RECENT_ORDER_SIZE = 20

/**
 * 로그인 정보와 주문·배송지 API를 마이페이지 View Model로 조립한다.
 *
 * **배송지는 이 화면을 막지 않는다.** 주문 현황을 보러 온 사용자를 배송지 조회 실패로
 * 세우지 않는다 — 못 받으면 기본 배송지 자리가 "등록된 배송지가 없습니다"가 될 뿐이고,
 * 그 자리에 관리 화면으로 가는 링크가 있다.
 */
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

  // 주소록은 이 화면 말고 배송지 관리 화면도 쓴다. 같은 키를 보므로 한 번만 받는다.
  const addresses = useConsumerAddresses()

  const data = profile && orders.data && summary.data
    ? toConsumerMyPageData(
        profile,
        orders.data,
        summary.data,
        toAddressSummary(addresses.data?.find((address) => address.isDefault)),
      )
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
  defaultAddress: ConsumerAddressSummary | null = null,
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
      orderId: order.orderId,
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
      paidAt: order.paidAt,
      shippedAt: order.shippedAt,
      deliveredAt: order.deliveredAt,
    }))),
    // 없으면 null 이다. 주소를 받기 전에 가입한 계정은 배송지가 0건이고 그것은 정상이다.
    defaultAddress,
  }
}

/**
 * 배송지 응답을 화면 View Model 로.
 *
 * 필드 이름이 다르다 (`receiverName` ↔ `recipientName`). 화면이 Backend DTO 를 직접 쓰지
 * 않기로 한 규칙 때문인데, 그 덕에 응답이 바뀌어도 카드 컴포넌트는 그대로다.
 */
function toAddressSummary(address: ConsumerAddress | undefined): ConsumerAddressSummary | null {
  if (!address) return null
  return {
    id: address.addressId,
    recipientName: address.receiverName,
    phone: address.receiverPhone,
    postalCode: address.zipCode,
    addressLine1: address.address,
    addressLine2: address.addressDetail ?? undefined,
    isDefault: address.isDefault,
  }
}
