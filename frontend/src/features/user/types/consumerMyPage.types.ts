/**
 * 소비자 마이페이지 화면이 사용하는 View Model.
 *
 * Backend DTO를 화면에서 직접 사용하지 않고 이 타입으로 한 번 변환하면,
 * API 응답 필드가 바뀌어도 화면 컴포넌트는 영향을 받지 않는다.
 */
export interface ConsumerProfileSummary {
  id: number
  name: string
  email: string
}

export const CONSUMER_ORDER_STAGES = [
  'PAYMENT_PENDING',
  'PREPARING',
  'SHIPPING',
  'DELIVERED',
] as const

export type ConsumerOrderStage = (typeof CONSUMER_ORDER_STAGES)[number]

export interface ConsumerOrderStageSummary {
  stage: ConsumerOrderStage
  count: number
}

export const CONSUMER_ORDER_VIEW_STATUSES = [
  'PAYMENT_PENDING',
  'PREPARING',
  'SHIPPING',
  'DELIVERED',
] as const

export type ConsumerOrderViewStatus = (typeof CONSUMER_ORDER_VIEW_STATUSES)[number]

export interface ConsumerRecentOrder {
  id: string
  orderNumber: string
  orderedAt: string
  productName: string
  brandName: string
  thumbnailUrl: string
  thumbnailAlt: string
  itemCode: string
  itemCount: number
  itemPrice: number
  totalAmount: number
  status: ConsumerOrderViewStatus
  journeyUrl: string | null
}

export interface ConsumerAddressSummary {
  id: number
  recipientName: string
  phone: string
  postalCode: string
  addressLine1: string
  addressLine2?: string
  isDefault: boolean
}

export interface ConsumerMyPageData {
  profile: ConsumerProfileSummary
  orderStages: ConsumerOrderStageSummary[]
  recentOrders: ConsumerRecentOrder[]
  defaultAddress: ConsumerAddressSummary | null
}
