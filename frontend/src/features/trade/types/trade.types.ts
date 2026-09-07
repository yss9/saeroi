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
/**
 * 결제 수단. 정책 08절에서 **포인트 시스템이 폐지**되고 카카오페이 PG 직접 결제로 확정됐다.
 * 금액 단위도 포인트(P)가 아니라 원이다.
 */
export type PaymentMethod = 'KAKAOPAY'

/** `trade_dispute.reason_type` */
export type DisputeReason = 'NOT_DELIVERED' | 'QUANTITY_MISMATCH' | 'CONDITION_MISMATCH'

/** `trade_dispute.status` / `resolution` */
export type DisputeStatus = 'OPEN' | 'RESOLVED'
/**
 * 분쟁 결과. 정책 09.3절에서 **ADMIN 판정이 폐지**되고 당사자 합의 + 양측 서명 방식이 됐다.
 * 대금 예치가 없으므로 승패가 아니라 **Material 을 어디로 보낼지** 만 정한다.
 */
export type DisputeResolution = 'RETURN_TO_SELLER' | 'TRANSFER_TO_BUYER'

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

// TODO(3번/Marketplace 담당): 배송·분쟁 상세 타입을 추가한다.
// Escrow 는 정책 v3에서 폐지됐다 — 플랫폼이 대금을 예치하지 않고 PG 로 직접 결제하며,
// 배송 중 재판매는 MARKETPLACE Lock 유지로 막는다.
