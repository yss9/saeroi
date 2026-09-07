// 컨트랙트가 `uint8` 로 받는 enum 값.
//
// ⚠️ Solidity enum 은 **선언 순서가 곧 숫자** 다. 순서가 배포된 컨트랙트와 다르면
// 컴파일도 통과하고 트랜잭션도 성공하는데 **의미만 조용히 뒤바뀐다.**
// (WASTE 로 발행하려 했는데 VIRGIN 으로 기록되는 식이다. 되돌릴 수 없다.)
//
// 그래서 값을 직접 쓰지 않고 아래 상수를 통해서만 넘긴다.
//
// 순서는 `blockchain/contracts/*.sol` 의 enum 선언이 기준이다. 값을 **중간에 끼워 넣으면**
// 뒤 값이 전부 밀리므로 추가는 반드시 맨 끝에만 한다.
//
// 아래 배열형 상수들은 `abi.test.ts` 가 길이와 위치를 고정한다. 실제로 여기가 어긋난 적이
// 있다 — ListingStatus 의 0번(NONE)을 빼먹어 모든 값이 한 칸씩 밀렸고, OrderStatus 에는
// 온체인에 없는 PAID 를 넣어 두어 결제하지 않은 주문이 결제됨으로 읽혔다.

/** MaterialToken.RootOrigin — 정책 v3 기준 2종 (RECOVERED 는 MVP 제외) */
export const ROOT_ORIGIN_ON_CHAIN = {
  VIRGIN: 0,
  WASTE: 1,
} as const

/** MaterialToken.LockPurpose */
export const LOCK_PURPOSE_ON_CHAIN = {
  MARKETPLACE: 0,
  PROCESSING: 1,
  PRODUCTION: 2,
} as const

/** MaterialToken.BurnReason */
export const BURN_REASON_ON_CHAIN = {
  PROCESSING_LOSS: 0,
  PRODUCTION_WASTE: 1,
  PRODUCT_REDEEM: 2,
} as const

/**
 * MaterialToken.VerificationState — `verificationRecord(tokenId, state)` 조회 인자.
 *
 * 서버가 내려주는 검증 단계(파생 계산값)와 이름은 같지만 **다른 계층의 값**이다.
 * 이쪽은 온체인 조회용 숫자이고, 화면 표시는 서버 응답을 쓴다.
 */
export const VERIFICATION_STATE_ON_CHAIN = {
  REGISTERED: 0,
  SOURCE_VERIFIED: 1,
  TRANSACTION_VERIFIED: 2,
  PROCESSING_VERIFIED: 3,
  PRODUCT_LINKED: 4,
} as const

/**
 * Marketplace.ListingStatus
 *
 * ⚠️ **0번은 `NONE` 이다.** Solidity 는 매핑에서 없는 키를 읽으면 0 을 돌려주므로,
 * 존재하지 않는 판매글과 실제 상태를 구분하려고 0 을 비워둔 것이다.
 * 이 자리를 빼면 이후 값이 전부 한 칸씩 밀려 **ACTIVE 가 SOLD_OUT 으로 읽힌다.**
 *
 * 이름이 우리 도메인과 다르다 — 컨트랙트는 `ACTIVE`/`CANCELLED`(L 두 개), DB·화면은
 * `ON_SALE`/`CANCELED` 다. 여기서 위치로 맞춰 번역한다.
 */
export const LISTING_STATUS_ON_CHAIN = [
  null, // NONE — 존재하지 않는 판매글
  'ON_SALE', // ACTIVE
  'SOLD_OUT',
  'CANCELED', // CANCELLED
] as const

/**
 * Marketplace.OrderStatus
 *
 * ⚠️ **`PAID` 가 없다.** 결제는 전적으로 오프체인이라 컨트랙트가 대금을 알지 못한다.
 * `CREATED` 다음이 바로 `SHIPPED` 이고, 결제 여부는 `material_trade` 만 안다.
 * 여기에 PAID 를 끼워 넣으면 **결제하지 않은 주문이 결제됨으로 읽힌다.**
 *
 * `DISPUTED`(6) 는 컨트랙트에 예약되어 있지만 `schema.sql` 의 ENUM 에는 아직 없다.
 * 온체인 값이 먼저 도착할 수 있으므로 여기서는 그대로 둔다 — 서버 상태로 옮길 때
 * 다루면 된다.
 */
export const ORDER_STATUS_ON_CHAIN = [
  null, // NONE — 존재하지 않는 주문
  'CREATED',
  'SHIPPED',
  'RECEIVED',
  'COMPLETED',
  'CANCELED', // CANCELLED
  'DISPUTED',
] as const

export type RootOriginOnChain = (typeof ROOT_ORIGIN_ON_CHAIN)[keyof typeof ROOT_ORIGIN_ON_CHAIN]
export type LockPurposeOnChain = (typeof LOCK_PURPOSE_ON_CHAIN)[keyof typeof LOCK_PURPOSE_ON_CHAIN]
export type BurnReasonOnChain = (typeof BURN_REASON_ON_CHAIN)[keyof typeof BURN_REASON_ON_CHAIN]

/**
 * 컨트랙트가 돌려준 uint8 을 문자열로 되돌린다.
 *
 * `null` 이 나오는 경우가 **두 가지**다.
 *   1. 표에 없는 값 — 컨트랙트가 우리보다 새 상태를 알고 있다는 뜻이다 (재배포 신호)
 *   2. 표의 그 자리가 `null` — Solidity 의 `NONE` 이고, 존재하지 않는 레코드를 읽었다는 뜻이다
 *
 * 둘 다 "모르는 값" 이라 화면에서는 같게 다뤄도 되지만, 로그를 볼 때 구분이 필요하면
 * 호출부가 raw 값을 함께 남긴다.
 */
export function decodeEnum<T extends string>(
  table: readonly (T | null)[],
  raw: bigint | number,
): T | null {
  const index = Number(raw)
  return index >= 0 && index < table.length ? table[index] : null
}
