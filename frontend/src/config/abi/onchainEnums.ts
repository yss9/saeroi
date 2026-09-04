// 컨트랙트가 `uint8` 로 받는 enum 값.
//
// ⚠️ Solidity enum 은 **선언 순서가 곧 숫자** 다. 순서가 배포된 컨트랙트와 다르면
// 컴파일도 통과하고 트랜잭션도 성공하는데 **의미만 조용히 뒤바뀐다.**
// (WASTE 로 발행하려 했는데 VIRGIN 으로 기록되는 식이다. 되돌릴 수 없다.)
//
// 그래서 값을 직접 쓰지 않고 아래 상수를 통해서만 넘긴다.
//
// TODO(1번/Blockchain 담당): blockchain/master 의 Solidity 원본과 이 순서가 같은지
// 배포 때마다 대조한다. enum 에 값을 **중간에 끼워 넣으면** 뒤 값이 전부 밀리므로
// 추가는 반드시 맨 끝에만 한다.

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

/** Marketplace.ListingStatus */
export const LISTING_STATUS_ON_CHAIN = ['ON_SALE', 'SOLD_OUT', 'CANCELED'] as const

/** Marketplace.OrderStatus */
export const ORDER_STATUS_ON_CHAIN = [
  'CREATED',
  'PAID',
  'SHIPPED',
  'RECEIVED',
  'COMPLETED',
  'CANCELED',
] as const

export type RootOriginOnChain = (typeof ROOT_ORIGIN_ON_CHAIN)[keyof typeof ROOT_ORIGIN_ON_CHAIN]
export type LockPurposeOnChain = (typeof LOCK_PURPOSE_ON_CHAIN)[keyof typeof LOCK_PURPOSE_ON_CHAIN]
export type BurnReasonOnChain = (typeof BURN_REASON_ON_CHAIN)[keyof typeof BURN_REASON_ON_CHAIN]

/** 컨트랙트가 돌려준 uint8 을 문자열로 되돌린다. 범위를 벗어나면 null. */
export function decodeEnum<T extends string>(
  table: readonly T[],
  raw: bigint | number,
): T | null {
  const index = Number(raw)
  return index >= 0 && index < table.length ? table[index] : null
}
