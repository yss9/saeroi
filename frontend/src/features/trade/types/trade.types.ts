// Trade 도메인 타입. Backend `material_trade` / `trade_dispute` ENUM 과 일치한다.
//
// 수량은 gram 정수, 금액은 원이다 (정책 03·08절).

/**
 * `material_trade.status` — 2-Track 확정 정책을 따른다.
 *
 *   CREATED → PAID → SHIPPED → RECEIVED → COMPLETED
 *                                      ↘ DISPUTED
 *   (배송 전 취소는 어느 단계에서든 CANCELED)
 *
 * ⚠️ 소유권은 **RECEIVED 시점** 에 이전된다. 배송(SHIPPED)이 아니다.
 * 배송 중에도 Seller 의 MARKETPLACE Lock 이 유지되어 재판매를 막는다 (정책 07.2).
 */
export type TradeStatus =
  | 'CREATED'
  | 'PAID'
  | 'SHIPPED'
  | 'RECEIVED'
  | 'COMPLETED'
  | 'CANCELED'
  | 'DISPUTED'

export const TRADE_STATUS_LABEL: Record<TradeStatus, string> = {
  CREATED: '결제 대기',
  PAID: '결제 완료',
  SHIPPED: '배송 중',
  RECEIVED: '수령 확인',
  COMPLETED: '거래 완료',
  CANCELED: '거래 취소',
  DISPUTED: '분쟁 진행 중',
}

/** 구매자가 다음에 할 수 있는 행동이 있는 단계인지. 목록에서 강조 표시에 쓴다. */
export const BUYER_ACTION_REQUIRED: readonly TradeStatus[] = ['CREATED', 'SHIPPED']

/** 판매자가 다음에 할 수 있는 행동이 있는 단계인지. */
export const SELLER_ACTION_REQUIRED: readonly TradeStatus[] = ['PAID']

/**
 * 결제 수단. 정책 08절에서 **포인트 시스템이 폐지**되고 카카오페이 PG 직접 결제로 확정됐다.
 * 금액 단위도 포인트(P)가 아니라 원이다.
 */
export type PaymentMethod = 'KAKAOPAY'

/** `trade_dispute.reason_type` */
export type DisputeReason = 'NOT_DELIVERED' | 'QUANTITY_MISMATCH' | 'CONDITION_MISMATCH'

export const DISPUTE_REASON_LABEL: Record<DisputeReason, string> = {
  NOT_DELIVERED: '소재 미수령',
  QUANTITY_MISMATCH: '수량 불일치',
  CONDITION_MISMATCH: '상태 불일치',
}

/** `trade_dispute.status` */
export type DisputeStatus = 'OPEN' | 'RESOLVED'

/**
 * 분쟁 결과. 정책 09.3절에서 **ADMIN 판정이 폐지**되고 당사자 합의 + 양측 서명 방식이 됐다.
 * 대금 예치가 없으므로 승패가 아니라 **Material 을 어디로 보낼지** 만 정한다.
 */
export type DisputeResolution = 'RETURN_TO_SELLER' | 'TRANSFER_TO_BUYER'

export interface TradeSummary {
  id: number
  tradeCode: string
  listingId: number
  materialId: number
  materialCode: string
  materialName: string
  /** 구매 수량 (gram) */
  quantity: number
  /** 거래 금액 (원). 주문 시점 스냅샷이다 */
  totalPrice: number
  status: TradeStatus
  sellerId: number
  sellerName: string
  buyerId: number
  buyerName: string
  createdAt: string
}

export interface TradeDetail extends TradeSummary {
  /** kg당 단가 (원). 주문 시점 스냅샷 */
  pricePerKg: number
  paymentMethod: PaymentMethod | null
  /** PG 승인 tid. 결제 전에는 null */
  paymentReference: string | null
  /** PG 취소 tid */
  cancelReference: string | null
  /** 거래 시점 지갑 주소 스냅샷 */
  sellerWallet: string | null
  buyerWallet: string | null
  paidAt: string | null
  shippedAt: string | null
  receivedAt: string | null
  /** Buyer 수령 서명 시각. 이게 있어야 TRANSACTION_VERIFIED 가 된다 */
  buyerSignedAt: string | null
  completedAt: string | null
  canceledAt: string | null
  cancelReason: string | null
}

/**
 * 구매 요청.
 *
 * 금액을 클라이언트가 보내지 않는다 — 서버가 Listing 의 `pricePerKg` 로 계산한다.
 * 보내게 하면 값을 조작해 싸게 살 수 있다. 화면에 보여주는 금액은 어디까지나 미리보기다.
 */
export interface CreateTradeRequest {
  listingId: number
  /** 구매 수량 (gram). 판매 가능 수량을 넘을 수 없다 */
  quantity: number
}

// ── 결제 ────────────────────────────────────────────────

/**
 * 결제 준비 응답 (`POST /trades/{tradeId}/payment`).
 *
 * 카카오페이는 2단계다 — `ready` 로 결제창 URL 을 받고, 사용자가 인증한 뒤
 * `approve` 로 승인한다. 이 응답이 1단계 결과다.
 */
export interface PaymentReadyResponse {
  /** 카카오페이가 발급한 거래 식별자 */
  tid: string
  /** 사용자를 보낼 결제창 주소 */
  redirectUrl: string
}

/** 결제 승인 요청 (`POST /trades/{tradeId}/payment/confirm`). */
export interface PaymentConfirmRequest {
  tid: string
  /** 결제창이 돌려주는 인증 토큰 */
  pgToken: string
}

/**
 * 화면이 보는 결제 진행 상태.
 *
 * Backend 의 `TradeStatus` 와 별개다 — 결제창으로 나갔다 돌아오는 구간은
 * 서버 상태로는 여전히 `CREATED` 인데, 사용자에게는 "결제 진행 중" 으로 보여야 한다.
 */
export type PaymentPhase =
  /** 아직 시작하지 않음 */
  | 'IDLE'
  /** 결제 준비 요청 중 (redirectUrl 을 받는 중) */
  | 'PREPARING'
  /** 결제창으로 이동함. 사용자가 돌아오기를 기다린다 */
  | 'AWAITING_PG'
  /** 승인 요청 중 */
  | 'CONFIRMING'
  | 'SUCCEEDED'
  /** 사용자가 결제창에서 취소 */
  | 'CANCELED'
  | 'FAILED'
