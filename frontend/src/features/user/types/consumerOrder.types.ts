import type { PageResponse } from '../../../types/api.types.ts'
import type {
  ProductCategory,
  ProductOrderStatus,
} from '../../product/types/product.types.ts'

/** Backend `ProductOrderItemResponse`와 같은 소비자 주문상품 응답. */
export interface ConsumerOrderItemResponse {
  itemCode: string
  serialNumber: number
  productName: string
  category: ProductCategory
  price: number
  /** 인증이 필요한 이미지 API 경로 */
  imageUrl: string
  /** 결제 전 주문은 null */
  journeyUrl: string | null
}

/** Backend `ProductOrderResponse`와 같은 소비자 본인 주문 응답. */
export interface ConsumerOrderResponse {
  orderCode: string
  brandName: string
  totalPrice: number
  status: ProductOrderStatus
  orderedAt: string
  paidAt: string | null
  shippedAt: string | null
  deliveredAt: string | null
  items: ConsumerOrderItemResponse[]
}

export interface ConsumerOrderFilters {
  status?: ProductOrderStatus
  /** 0-based */
  page?: number
  size?: number
}

export type ConsumerOrderPage = PageResponse<ConsumerOrderResponse>

/** Backend `ProductOrderStatusSummaryResponse`와 같은 상태별 전체 건수. */
export interface ConsumerOrderStatusSummaryResponse {
  created: number
  paid: number
  shipped: number
  delivered: number
}
