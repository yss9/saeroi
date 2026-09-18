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
  /**
   * 계정 연락처(`users.phone`). 배송지의 수령인 연락처와는 다른 값이라 선택이다 —
   * `useConsumerProfile`(마이페이지 홈)만 채우고, 주문·배송지 화면이 쓰는
   * `useConsumerMyPage`는 이 값이 필요 없어 비워 둔다.
   */
  phone?: string
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
  /**
   * 수령 확인이 쓰는 주문 번호.
   *
   * **이 목록의 한 줄은 주문이 아니라 개체다** — 주문 하나에 제품이 여럿이면 줄이 여럿이
   * 된다. 그런데 수령 확인은 주문 단위라, 어느 줄에서 눌러도 그 주문 전체가 완료된다.
   */
  orderId: number
  orderGroupCode?: string | null
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
  /**
   * 배송 현황에 찍히는 시각들. 아직 지나지 않은 단계는 null 이다.
   *
   * **상태 하나만으로는 "언제" 를 말할 수 없다.** 배송 중이라는 배지만 보면 어제 떠난
   * 것인지 방금 떠난 것인지 알 수 없어, 사용자는 결국 브랜드에 묻게 된다.
   */
  paidAt: string | null
  shippedAt: string | null
  deliveredAt: string | null
  /**
   * 송장. 브랜드가 발송 처리하며 적은 값이다.
   *
   * **배송 중이라는 배지만으로는 어디쯤 왔는지 알 수 없다** — 이 값이 없으면 사용자는
   * 브랜드에 물어보는 수밖에 없다. 직접 전달처럼 송장 없이 보낸 주문도 있어 null 이 정상이다.
   */
  carrier: string | null
  trackingNumber: string | null
  /**
   * 수령인·배송지. 주문 시점에 확정되므로 배송 여부와 무관하게 항상 값이 있다.
   *
   * "배송조회" 패널이 본인이 주문할 때 고른 배송지를 다시 보여줄 때 쓴다.
   */
  receiverName: string
  zipCode: string
  address: string
  addressDetail: string | null
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
