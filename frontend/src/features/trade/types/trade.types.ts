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

/**
 * 구매자가 다음에 할 수 있는 행동이 있는 단계인지. 목록에서 강조 표시에 쓴다.
 *
 * **배송 중은 넣지 않는다.** 물건이 오는 동안 구매자가 할 수 있는 일은 없고, 수령 확인은
 * 실물을 받은 뒤의 일이다 — 배송 중인 줄마다 "확인 필요" 가 붙으면 정작 결제가 남은 건이
 * 묻힌다.
 */
export const BUYER_ACTION_REQUIRED: readonly TradeStatus[] = ['CREATED']

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
 * 결제 수단 이름.
 *
 * `MOCK` 은 PG 연동 전 경로다. "카카오페이" 로 적으면 실제로 결제된 것처럼 읽히므로
 * 값을 그대로 구분한다.
 *
 * <b>아직 `PaymentPanel` 은 이 상수를 쓰지 않는다.</b> 같은 두 값을 인라인 삼항으로
 * 들고 있다 — 그 파일은 구매자(브랜드) 화면이라 담당이 다르다. 그쪽을 손볼 때 여기로
 * 모으면 된다. 지금 옮기지 않는 이유는 남의 화면을 말없이 바꾸지 않기 위해서다.
 */
export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  KAKAO_PAY: '카카오페이',
  MOCK: '테스트 결제',
}

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
  /**
   * 취소 시각. **취소 여부와 무관하게 취소가 일어났다면 항상 있다** —
   * 사유를 적지 않은 취소도 시각은 남는다.
   */
  canceledAt: string | null
  /**
   * 취소 사유. 사람이 적었을 때만 있다.
   *
   * 대부분의 취소는 사유가 없다 — 결제창을 닫고 나온 구매자에게 이유를 적으라고 요구하면
   * 취소가 막히거나 의미 없는 문자열이 쌓인다. 판매자가 재고 문제로 취소하는 경우처럼
   * 상대가 알아야 하는 상황에서만 채운다.
   */
  cancelReason: string | null
  /**
   * 배송지와 송장.
   *
   * **배송지가 추가되기 전에 만들어진 거래는 `null` 이다.** 그 거래에는 `trade_shipping`
   * 행이 없어서 발송·수령이 서버에서 404(`TRADE_SHIPPING_NOT_FOUND`)로 막힌다 —
   * 화면에서 되살릴 방법이 없으므로 판매자에게 사유를 알리는 것까지가 할 수 있는 일이다.
   *
   * 서버가 빈 객체 대신 `null` 을 주므로 "주소가 비어 있는 배송지" 로 잘못 읽을 일은 없다.
   */
  shipping: TradeShippingInfo | null
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

  // ── 배송지 ──
  //
  // **구매 시점에 함께 받는다.** 나중에 채우는 별도 API 가 없고, 배송 행이 없는 거래는
  // 발송·수령이 모두 404 로 막힌다. 그래서 상세 주소를 뺀 나머지는 전부 필수다.
  //
  // 길이 제한은 서버 검증과 같은 값이다 (`TradeCreateRequest`). 화면에서 먼저 막지 않으면
  // 사용자가 다 입력하고 제출한 뒤에야 400 을 본다.
  /** 필수, 100자 이하 */
  receiverName: string
  /** 필수, 20자 이하 */
  receiverPhone: string
  /** 필수, 20자 이하 */
  zipCode: string
  /** 필수, 255자 이하 */
  address: string
  /** 선택, 255자 이하 */
  addressDetail?: string
}

/**
 * 거래 취소 요청 (`POST /v1/trades/{tradeId}/cancel`).
 *
 * 당사자 양쪽 모두 부를 수 있고 **발송 전(`CREATED`·`PAID`)까지만** 가능하다.
 * 그 뒤의 되돌리기는 분쟁 절차다 (정책 09).
 *
 * 사유는 선택이고 바디 자체를 생략할 수 있다. 서버가 빈 문자열·공백을 null 로
 * 정규화하지만, 보낼 값이 없으면 바디를 아예 만들지 않는다 — 사유 없는 취소가
 * 정상 경로라는 것을 요청 모양이 그대로 말하게 둔다.
 */
export interface CancelTradeRequest {
  /** 선택, 255자 이하 */
  reason?: string
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
 * 결제 준비 결과 (`POST /v1/trades/{tradeId}/payment/ready`).
 *
 * `redirectUrl` 이 비어 있으면 **보낼 결제창이 없다**는 뜻이다. 서버에 카카오페이 키가
 * 없어 Mock 게이트웨이가 붙은 경우이며, 그때는 이동하지 않고 바로 승인으로 넘어간다.
 *
 * 그 판단을 `redirectUrl` 의 빈 문자열로 하지 않고 `live` 로 한다 — "비어 있으면 Mock"
 * 같은 규칙은 언젠가 서버·화면 한쪽에서만 바뀐다.
 *
 * `tid` 는 다시 보내라는 값이 아니다. 승인에는 `pgToken` 만 필요하고 tid 는 서버가
 * 거래에서 읽는다. 결제 문의 때 사용자가 말할 수 있도록 화면에 노출하는 용도다.
 */
export interface PaymentReady {
  tradeId: number
  tid: string
  /** 카카오페이 결제창 주소. Mock 이면 빈 문자열 */
  redirectUrl: string
  totalPrice: number
  /** 실제 PG 가 붙어 있는가. false 면 청구가 일어나지 않는다 */
  live: boolean
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
  /** ready 호출 중. tid 와 결제창 주소를 받는 구간 */
  | 'PREPARING'
  /**
   * 결제창으로 이동함. 사용자가 돌아오기를 기다린다.
   *
   * 실제 PG 라면 이 상태에서 브라우저가 카카오페이로 떠나므로 화면이 사라진다.
   * Mock 이면 떠나지 않고 이 상태에 머물며 "승인" 버튼을 기다린다.
   */
  | 'AWAITING_PG'
  /** approve 호출 중. 이 구간에서 실제 청구가 일어난다 */
  | 'CONFIRMING'
  | 'SUCCEEDED'
  /** 사용자가 결제창에서 취소했거나 거래를 취소함 */
  | 'CANCELED'
  | 'FAILED'

// ── 배송 · 수령 ─────────────────────────────────────────

/**
 * 배송 정보 (`trade_shipping`). `trade_id` 가 UNIQUE 라 **거래당 한 건**이고
 * 분할 배송은 없다.
 *
 * 배송지는 구매 시점에 확정되고 송장은 판매자가 발송할 때 붙는다. 그래서 수취인·주소는
 * 항상 값이 있고 `carrier`·`trackingNumber`·`shippedAt` 은 발송 전까지 null 이다.
 */
export interface TradeShippingInfo {
  receiverName: string
  receiverPhone: string
  zipCode: string
  address: string
  addressDetail: string | null
  /** 택배사. 송장 없이 전달한 경우 null */
  carrier: string | null
  trackingNumber: string | null
  shippedAt: string | null
  /** 실제 배송 완료 시각. 구매자 수령 확인 시 기록된다 */
  deliveredAt: string | null
}

/**
 * 발송 처리 요청 (`PATCH /v1/trades/{tradeId}/ship`).
 *
 * 택배사·운송장 **둘 다 선택**이다. 직접 수령이나 화물처럼 송장이 없는 전달 방식이 있어
 * 서버 스키마도 두 컬럼 모두 NULL 을 허용한다.
 *
 * 다만 운송장만 보내면 `TRADE_CARRIER_REQUIRED` 로 거절된다 — 택배사를 모르면 조회할
 * 방법이 없어 기록해도 쓸모가 없다. 화면에서 먼저 막는다.
 *
 * 빈 값은 보내지 않는다. 서버도 blank 를 null 로 정규화하므로 보내도 결과는 같지만,
 * 앞뒤 공백까지 지우는 것은 화면 쪽 일이다 — 공백만 입력한 택배사가 그대로 저장되면
 * 송장이 있는 것처럼 보인다. 그 정리는 `shipTrade()` 가 한다.
 */
export interface ShipTradeRequest {
  carrier?: string
  trackingNumber?: string
}

/**
 * 발송 처리 결과.
 *
 * ⚠️ **`Trade` 가 아니다.** 거래 전체가 아니라 바뀐 값만 온다. 그래서 상세 캐시를
 * `setQueryData` 로 통째로 덮을 수 없고 이 값들만 병합해야 한다 (`useShipTrade` 참고).
 */
export interface TradeShipResult {
  tradeId: number
  tradeCode: string
  status: TradeStatus
  shippedAt: string
  carrier: string | null
  trackingNumber: string | null
}

/**
 * 수령 확인 결과 (`POST /v1/trades/{tradeId}/receive/confirm`).
 *
 * 요청 하나로 서버가 `RECEIVED` 를 거쳐 `COMPLETED` 까지 끝낸다 — 잠금 소비, 판매자 차감,
 * 구매자 증가가 한 트랜잭션이다. 그래서 중간 상태를 화면에서 볼 일이 없고,
 * **이전 수량과 구매자 보유량이 결과로 온다.**
 *
 * 수량 단위는 gram 이다.
 */
export interface TradeReceiptResult {
  tradeId: number
  tradeCode: string
  status: TradeStatus
  receivedAt: string
  /** Wallet 서명 시각. 지금은 서버가 수령 시각과 같은 값으로 남긴다 */
  buyerSignedAt: string
  completedAt: string
  /** 이번에 이전된 수량 (gram) */
  transferredQuantity: number
  /**
   * 이전 후 구매자 보유량 (gram).
   *
   * 판매자 보유량은 오지 않는다 — 이 응답을 받는 사람은 구매자이고, 판매자가 그 소재를
   * 통틀어 얼마나 갖고 있는지는 이 거래와 무관한 재고 정보다.
   */
  buyerQuantity: number
}

/**
 * "지금 상태로는 처리할 수 없다" 는 거절 코드.
 *
 * 이 코드가 오면 **화면이 들고 있는 거래가 낡았다는 뜻**이다. 다른 창에서 이미 발송했거나
 * 구매자가 먼저 수령을 확인한 경우다. 사유만 보여주고 끝내면 사용자는 같은 버튼을 다시
 * 누르므로, 거래를 다시 읽어 현재 상태로 화면을 맞춘다.
 */
export const STALE_TRADE_ERROR_CODES: readonly string[] = [
  'TRADE_ALREADY_SHIPPED',
  'TRADE_NOT_SHIPPABLE',
  'TRADE_ALREADY_RECEIVED',
  'TRADE_NOT_RECEIVABLE',
  'PAYMENT_STATE_INVALID',
]
