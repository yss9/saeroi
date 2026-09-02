// Trade 도메인 타입. Backend `material_trade` / `trade_dispute` ENUM 과 일치한다.

/** `material_trade.status` — 2-Track 확정 정책(보낸 측 → 받은 측 확인)을 따른다 */
export type TradeStatus =
  | 'CREATED'
  | 'PAID'
  | 'SHIPPED'
  | 'RECEIVED'
  | 'COMPLETED'
  | 'CANCELED'
  | 'DISPUTED'

/** `material_trade.payment_method` */
export type PaymentMethod = 'POINT' | 'MOCK'

/** `trade_dispute.reason_type` */
export type DisputeReason = 'NOT_DELIVERED' | 'QUANTITY_MISMATCH' | 'CONDITION_MISMATCH'

/** `trade_dispute.status` / `resolution` */
export type DisputeStatus = 'OPEN' | 'RESOLVED'
export type DisputeResolution = 'BUYER_WIN' | 'SELLER_WIN'

export interface TradeSummary {
  id: number
  listingId: number
  materialName: string
  quantity: number
  totalPrice: number
  status: TradeStatus
  paymentMethod: PaymentMethod
  createdAt: string
}

// TODO(3번/Marketplace·Escrow 담당): Escrow 예치·정산 상태 타입을 추가한다.
