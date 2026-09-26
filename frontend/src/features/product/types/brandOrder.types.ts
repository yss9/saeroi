import type { ProductOrderStatus } from './product.types.ts'

/**
 * 브랜드가 보는 주문 하나.
 *
 * Backend 원본: `product/dto/BrandProductOrderResponse.java`.
 *
 * 구매자용 응답(`ConsumerOrderResponse`)과 **다른 타입이다.** 이쪽에는 배송지가 실려
 * 나가고 제품 이미지가 없다 — 브랜드가 하려는 일이 "받을 곳으로 물건을 보내는 것" 이라서다.
 */
export interface BrandOrder {
  orderId: number
  orderCode: string
  status: ProductOrderStatus
  /** 주문한 사람. **송장에 쓰는 이름이 아니다** — `shipping.receiverName` 을 쓴다 */
  buyerName: string
  totalPrice: number
  orderedAt: string
  paidAt: string | null
  shippedAt: string | null
  deliveredAt: string | null
  items: BrandOrderItem[]
  shipping: BrandOrderShipping
}

/**
 * 주문에 묶인 개체 하나.
 *
 * **`itemCode` 와 `serialNumber` 를 화면에서 지우면 안 된다.** 같은 모델이라도 개체마다
 * 다른 물건이고(QR 이 다르다), 제품 이름만 보고 아무거나 집으면 다른 제품이 나간다.
 */
export interface BrandOrderItem {
  itemCode: string
  serialNumber: number
  productName: string
  price: number
}

/**
 * 받을 곳과 송장.
 *
 * ⚠️ **`receiverName` 이 송장에 쓰는 이름이다.** 주문자(`buyerName`)와 다를 수 있다 —
 * 선물이나 가족 집으로 보내는 주문이 그렇다. 주문자 이름을 적으면 물건이 엉뚱한 사람
 * 앞으로 간다.
 */
export interface BrandOrderShipping {
  receiverName: string
  receiverPhone: string
  zipCode: string
  address: string
  addressDetail: string | null
  /** 발송 전에는 null */
  carrier: string | null
  trackingNumber: string | null
  shippedAt: string | null
}

/**
 * 발송 처리 요청.
 *
 * **둘 다 선택이다.** 직접 전달·퀵처럼 송장이 없는 방식이 있어 빈 객체로도 발송된다.
 * 다만 **운송장 번호만 보내고 택배사를 비우면 400** 이다 — 조회할 방법이 없는 조합이라
 * 서버가 막는다 (`PRODUCT_ORDER_TRACKING_WITHOUT_CARRIER`).
 */
export interface BrandOrderShipRequest {
  carrier?: string
  trackingNumber?: string
}

/** 발송 처리 응답. 목록 응답과 다른 타입이다 — 바뀐 값만 돌려준다. */
export interface BrandOrderShipResult {
  orderId: number
  orderCode: string
  status: ProductOrderStatus
  shippedAt: string
  carrier: string | null
  trackingNumber: string | null
}

export interface BrandOrderFilters {
  status?: ProductOrderStatus
  /** 0-based */
  page?: number
  size?: number
}
