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

/**
 * 주문에 붙은 배송 정보.
 *
 * 송장(`carrier`·`trackingNumber`)은 브랜드가 발송 처리하며 적은 값 그대로다. 둘 다 선택이라
 * — 직접 전달·퀵처럼 송장이 없는 방식이 있다 — 발송된 주문이어도 null 일 수 있다.
 *
 * 수령인·주소(2026-09-16 추가)는 주문 시점에 확정되므로 배송 여부와 무관하게 항상 값이
 * 있다 — "배송조회" 화면이 본인이 주문할 때 고른 배송지를 다시 보여줄 수 있게 한다.
 */
export interface ConsumerOrderShipping {
  receiverName: string
  zipCode: string
  address: string
  addressDetail: string | null
  carrier: string | null
  trackingNumber: string | null
  shippedAt: string | null
}

/** Backend `ProductOrderResponse`와 같은 소비자 본인 주문 응답. */
export interface ConsumerOrderResponse {
  /**
   * 결제 2단계(`/payment/ready`·`/approve`)와 취소가 경로에 쓰는 번호.
   *
   * ⚠️ **서버 응답에 아직 없다.** `ProductOrderResponse` 는 `orderCode`(사람이 읽는 코드)만
   * 담는데 결제 경로는 `{orderId}`(숫자)를 받는다 — 주문을 만들고 나서 그 번호를 알 방법이
   * 없다. 추가를 요청해 둔 상태라, 화면은 값이 없을 때를 따로 처리한다
   * (`useProductOrderPayment`).
   */
  orderId: number
  orderCode: string
  orderGroupCode?: string | null
  brandName: string
  totalPrice: number
  status: ProductOrderStatus
  orderedAt: string
  paidAt: string | null
  shippedAt: string | null
  deliveredAt: string | null
  items: ConsumerOrderItemResponse[]
  /** 배송 정보(송장 + 2026-09-16부터 수령인·주소). 주문이 있으면 항상 함께 만들어진다. */
  shipping?: ConsumerOrderShipping | null
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

// ── 결제 ────────────────────────────────────────────────

/**
 * 결제 준비 응답. Backend `PaymentReadyResponse` 의 제품 주문판이다.
 *
 * 소재 거래와 같은 모양이지만 **다른 도메인이라 타입을 따로 둔다** — 한쪽이 필드를 늘릴 때
 * 다른 쪽이 조용히 끌려가지 않게 한다.
 *
 * **주문 번호를 여기서 읽지 않는다.** 서버가 소재 거래용 `PaymentReadyResponse` 를 그대로
 * 쓰고 있어 그 자리 이름이 `tradeId` 다. 어차피 이 요청을 부를 때 주문 번호를 이미 알고
 * 있으므로, 이름이 어느 쪽이든 상관없게 아예 읽지 않는다.
 */
export interface ProductOrderPaymentReady {
  /**
   * 카카오 거래 번호. 다시 보낼 필요는 없다 — 서버가 주문에 저장해 둔다.
   * 결제 문의 때 사용자가 말할 수 있게 화면에 적는 값이다.
   */
  tid: string
  /** 카카오페이 결제창 주소. 결제 키가 없는 환경이면 빈 문자열 */
  redirectUrl: string
  totalPrice: number
  /** 실제 PG 가 붙어 있는가. false 면 청구가 일어나지 않는다 */
  live: boolean
}

/**
 * 화면이 보는 결제 진행 상태.
 *
 * **서버의 `ProductOrderStatus` 로는 이 구간을 말할 수 없다.** 결제창에 나가 있는 동안에도
 * 서버는 여전히 `CREATED` 이기 때문이다.
 */
export type ProductOrderPaymentPhase =
  | 'IDLE'
  /** 주문을 만드는 중. 이 순간 개체가 RESERVED 로 잡힌다 */
  | 'CREATING'
  | 'PREPARING'
  /** 결제창으로 떠났거나(실제 PG), 떠나지 않고 승인을 기다리는 중(Mock) */
  | 'AWAITING_PG'
  | 'CONFIRMING'
  | 'SUCCEEDED'
  | 'CANCELED'
  | 'FAILED'
