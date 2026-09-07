// Trade 도메인 타입.
//
// Backend 원본: backend/src/main/java/com/ssafy/upcycle/trade/dto/TradeResponse.java
// 필드 이름을 서버 응답과 정확히 같게 둔다 (marketplace.types.ts 와 같은 이유).
//
// 수량은 gram 정수, 금액은 원이다 (정책 03·08절).

import type { QuantityUnit } from '../../marketplace/types/marketplace.types.ts'

/**
 * `material_trade.status` — 2-Track 확정 정책을 따른다.
 *
 *   CREATED → PAID → SHIPPED → RECEIVED → COMPLETED
 *   (발송 전까지는 어느 단계에서든 CANCELED)
 *
 * ⚠️ 소유권은 **RECEIVED 시점** 에 이전된다. 배송(SHIPPED)이 아니다.
 * 배송 중에도 Seller 의 MARKETPLACE 잠금이 유지되어 재판매를 막는다 (정책 07.2).
 *
 * ⚠️ `DISPUTED` 가 없다. 정책 v3 의 상태 목록에는 있지만 `schema.sql` 의 ENUM 에는
 * 아직 들어가지 않았다 — 서버가 돌려줄 수 없는 값을 화면 타입에 두면 도달하지 않는
 * 분기가 생긴다. 분쟁 스키마가 들어오면 여기와 서버 enum 을 같은 커밋에서 바꾼다.
 */
export type TradeStatus =
  | 'CREATED'
  | 'PAID'
  | 'SHIPPED'
  | 'RECEIVED'
  | 'COMPLETED'
  | 'CANCELED'

export const TRADE_STATUS_LABEL: Record<TradeStatus, string> = {
  CREATED: '결제 대기',
  PAID: '결제 완료',
  SHIPPED: '배송 중',
  RECEIVED: '수령 확인',
  COMPLETED: '거래 완료',
  CANCELED: '거래 취소',
}

/** 구매자가 다음에 할 수 있는 행동이 있는 단계인지. 목록에서 강조 표시에 쓴다. */
export const BUYER_ACTION_REQUIRED: readonly TradeStatus[] = ['CREATED', 'SHIPPED']

/** 판매자가 다음에 할 수 있는 행동이 있는 단계인지. */
export const SELLER_ACTION_REQUIRED: readonly TradeStatus[] = ['PAID']

/**
 * 결제 수단. `material_trade.payment_method` ENUM 과 같다.
 *
 * 정책 08절에서 포인트 시스템이 폐지되고 카카오페이 PG 직접 결제로 확정됐다.
 * `MOCK` 은 PG 연동 전 시연·테스트 경로다 — 정책 문서에는 없지만 스키마에 있고,
 * 지금 화면이 실제로 쓰는 값이다.
 */
export type PaymentMethod = 'MOCK' | 'KAKAO_PAY'

/**
 * 거래 한 건. 목록과 상세가 같은 모양이다.
 *
 * 소재 이름과 양쪽 이름, kg 단가는 `material_trade` 에 없는 값이지만 서버가 조인해서
 * 함께 내려보낸다 — 없으면 화면이 행마다 소재와 사용자를 다시 조회하게 된다.
 */
export interface Trade {
  tradeId: number
  tradeCode: string
  /** 온체인 주문 번호. 체인 등록 전에는 null */
  chainTradeId: number | null
  listingId: number
  materialId: number
  materialCode: string
  materialName: string
  sellerId: number
  sellerName: string
  buyerId: number
  buyerName: string
  /** 거래 시점 지갑 주소 스냅샷. 지갑을 교체해도 이 값은 바뀌지 않는다 */
  sellerWallet: string
  buyerWallet: string
  /** 구매 수량 (gram) */
  quantity: number
  /** kg당 단가 (원) */
  pricePerKg: number
  /** 거래 금액 (원). 서버가 계산한 값이다 */
  totalPrice: number
  paymentMethod: PaymentMethod
  /**
   * PG 승인 tid. 결제 전에는 null.
   *
   * 이 필드가 채워졌는지가 "결제가 실제로 승인되었는가" 의 답이다 —
   * `status` 만 보면 Mock 결제와 실제 승인을 구분할 수 없다.
   */
  paymentReference: string | null
  /** 실제 승인된 금액 (원). 결제 전에는 0 */
  paymentAmount: number
  status: TradeStatus
  createdAt: string
  paidAt: string | null
  shippedAt: string | null
  receivedAt: string | null
  /** Buyer 수령 서명 시각. 이게 있어야 TRANSACTION_VERIFIED 가 된다 */
  buyerSignedAt: string | null
  completedAt: string | null
}

/**
 * 구매 요청.
 *
 * 금액을 보내지 않는다 — 서버가 판매글의 `pricePerKg` 로 계산한다. 보내게 하면 값을
 * 조작해 싸게 살 수 있다. 화면에 보여주는 금액은 어디까지나 미리보기다.
 */
export interface CreateTradeRequest {
  listingId: number
  quantity: number
  unit: QuantityUnit
  paymentMethod: PaymentMethod
}

/** 내 거래 목록 필터. */
export interface TradeFilters {
  /** BUY = 내가 산 것(기본), SELL = 내가 판 것 */
  side?: 'BUY' | 'SELL'
  status?: TradeStatus
  /** 0-based */
  page?: number
  size?: number
}

// ── 결제 ────────────────────────────────────────────────

/**
 * 구매 전 금액 미리보기 (`GET /v1/trades/quote`).
 *
 * 화면도 `calculateTotalPrice` 로 같은 계산을 한다. 그런데도 이 호출이 있는 이유는
 * **단가와 잔여 수량이 그 사이에 바뀔 수 있기 때문**이다 — 목록을 읽은 뒤 다른 구매자가
 * 먼저 사면 화면의 계산은 이미 없는 수량에 대한 금액이다.
 *
 * 이 응답도 약속은 아니다. 실제 금액은 구매 요청 시점에 다시 계산된다.
 */
export interface TradeQuote {
  listingId: number
  /** gram */
  quantity: number
  pricePerKg: number
  totalPrice: number
  availableQuantity: number
  purchasable: boolean
}

/**
 * 결제 승인 통보 (`POST /v1/trades/{tradeId}/payment`).
 *
 * ⚠️ **지금은 클라이언트가 승인 결과를 알려주는 구조다.** PG 연동 전이라 그렇고,
 * 그래서 서버가 `paymentAmount` 를 거래 금액과 반드시 대조한다
 * (`PAYMENT_AMOUNT_MISMATCH`). 실제 카카오페이를 붙이면 서버가 PG 에 직접 물어보고
 * 이 요청은 `pgToken` 하나로 줄어든다 — 바뀌는 것은 서버 안쪽이고 화면 흐름은 그대로다.
 */
export interface PaymentConfirmRequest {
  /** PG 승인 tid. Mock 결제도 값을 채운다 */
  paymentReference: string
  /** 승인 금액 (원). 거래 금액과 같아야 한다 */
  paymentAmount: number
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
  /** 결제 준비 중 */
  | 'PREPARING'
  /** 결제창으로 이동함. 사용자가 돌아오기를 기다린다 */
  | 'AWAITING_PG'
  /** 승인 요청 중 */
  | 'CONFIRMING'
  | 'SUCCEEDED'
  /** 사용자가 결제창에서 취소 */
  | 'CANCELED'
  | 'FAILED'
