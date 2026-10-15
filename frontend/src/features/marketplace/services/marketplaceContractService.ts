// Marketplace 온체인 호출.
//
// 📌 **컨트랙트는 가격을 모른다.** `createListing(materialId, quantity)` 에 가격 인자가
// 없고 `Listing`·`Order` 구조체에도 금액 필드가 없다. 결제가 전적으로 오프체인이라
// (정책 08절) 온체인에는 수량과 소유권만 남는다.
//
// 그래서 금액에 관한 모든 것은 서버가 답한다. 화면이 온체인 값과 금액을 대조하려 들면
// 대조할 대상이 없다 — 예전에 이 파일에 있던 `calculateTotalPrice` 는 컨트랙트에
// 존재하지 않는 함수였다.
//
// 분쟁(`openDispute`·`settleDispute`·`getDispute`)은 여기 없다. 에픽 -6 의 범위이고
// 담당이 다르다. ABI 에는 있으므로 그쪽에서 같은 방식으로 붙이면 된다.

import type { ContractTransactionResponse } from 'ethers'
import {
  LISTING_STATUS_ON_CHAIN,
  ORDER_STATUS_ON_CHAIN,
  decodeEnum,
} from '../../../config/abi/onchainEnums.ts'
import { getReadContract, getWriteContract } from '../../../services/blockchain/contractFactory.ts'
import { requireOnChainRole } from '../../../services/blockchain/onChainRole.ts'

// ── 쓰기 ────────────────────────────────────────────────

/**
 * 판매 등록. 등록과 동시에 MARKETPLACE Lock 이 생성된다 (정책 06.3).
 *
 * 가격을 넘기지 않는다 — 컨트랙트가 받지 않는다. kg 단가는 서버의
 * `material_listing.price_per_kg` 에만 있다.
 *
 * @param quantity gram 정수
 *
 * listingId 는 반환값이지만 트랜잭션에서는 읽을 수 없다 —
 * `ListingCreated` 이벤트에서 꺼낸다 (contractFactory.readEventArg).
 */
export async function createListing(
  materialId: number,
  quantity: number,
  operationId: string,
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  // 컨트랙트가 materialToken.hasRole(SUPPLIER_ROLE, msg.sender) 를 검사한다
  // (Marketplace.sol:139). 없으면 영문 revert 만 보이므로 미리 확인한다.
  await requireOnChainRole('SUPPLIER_ROLE')
  return contract.createListing(materialId, quantity, operationId) as Promise<ContractTransactionResponse>
}

/** 판매 취소. 남은 수량의 Lock 이 해제된다. */
export async function cancelListing(listingId: number, operationId: string): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  return contract.cancelListing(listingId, operationId) as Promise<ContractTransactionResponse>
}

/**
 * 구매 요청. 해당 수량이 예약되어 다른 구매자가 살 수 없게 된다 (정책 07.2).
 * 대금 결제는 이 트랜잭션과 완전히 별개로 PG 에서 진행된다.
 */
export async function createOrder(
  listingId: number,
  quantity: number,
  operationId: string,
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  // 컨트랙트가 materialToken.hasRole(BRAND_ROLE, msg.sender) 를 검사한다
  // (Marketplace.sol:208). 앱에서 BRAND 로 가입했어도 그 지갑에 권한이 없을 수 있다.
  await requireOnChainRole('BRAND_ROLE')
  return contract.createOrder(listingId, quantity, operationId) as Promise<ContractTransactionResponse>
}

export async function cancelOrder(orderId: number, operationId: string): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  return contract.cancelOrder(orderId, operationId) as Promise<ContractTransactionResponse>
}

/** Seller 배송 처리. 이 시점에도 Lock 은 유지된다 — 배송 중 재판매를 막는다. */
export async function markShipped(orderId: number, operationId: string): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  return contract.markShipped(orderId, operationId) as Promise<ContractTransactionResponse>
}

/**
 * Buyer 수령 확인.
 *
 * **여기서 소유권이 이전된다.** 배송 시점이 아니라 수령 확인 시점이다 (정책 07.2).
 * Lock 이 CONSUMED 로 바뀌고 Balance 가 Seller → Buyer 로 옮겨간다.
 */
export async function confirmReceipt(orderId: number, operationId: string): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  return contract.confirmReceipt(orderId, operationId) as Promise<ContractTransactionResponse>
}

// ── 조회 ────────────────────────────────────────────────

export interface OnChainListing {
  id: bigint
  materialId: bigint
  materialLockId: bigint
  seller: string
  initialQuantity: bigint
  remainingQuantity: bigint
  /** 팔렸지만 아직 수령 확인되지 않은 수량. 서버에는 대응 컬럼이 없다 */
  reservedQuantity: bigint
  /** `null` 이면 그 번호의 판매글이 없다 (컨트랙트의 NONE) */
  status: 'ON_SALE' | 'SOLD_OUT' | 'CANCELED' | null
}

export async function getListing(listingId: number): Promise<OnChainListing> {
  const contract = getReadContract('marketplace')
  const raw = await contract.getListing(listingId)
  return {
    id: raw.id,
    materialId: raw.materialId,
    materialLockId: raw.materialLockId,
    seller: raw.seller,
    initialQuantity: raw.initialQuantity,
    remainingQuantity: raw.remainingQuantity,
    reservedQuantity: raw.reservedQuantity,
    status: decodeEnum(LISTING_STATUS_ON_CHAIN, raw.status),
  }
}

export interface OnChainOrder {
  id: bigint
  listingId: bigint
  seller: string
  buyer: string
  materialId: bigint
  quantity: bigint
  /**
   * `null` 이면 그 번호의 주문이 없다.
   *
   * ⚠️ `PAID` 가 없다 — 컨트랙트는 대금을 모른다. 결제 여부는 서버의 거래 상태를 본다.
   */
  status: 'CREATED' | 'SHIPPED' | 'RECEIVED' | 'COMPLETED' | 'CANCELED' | 'DISPUTED' | null
}

export async function getOrder(orderId: number): Promise<OnChainOrder> {
  const contract = getReadContract('marketplace')
  const raw = await contract.getOrder(orderId)
  return {
    id: raw.id,
    listingId: raw.listingId,
    seller: raw.seller,
    buyer: raw.buyer,
    materialId: raw.materialId,
    quantity: raw.quantity,
    status: decodeEnum(ORDER_STATUS_ON_CHAIN, raw.status),
  }
}

/**
 * 온체인 가용 수량 (보유량 − 잠긴 수량).
 *
 * 서버의 `MaterialAvailability` 와 같은 값을 답해야 한다. 어긋나면 DB 는 팔 수 있다고
 * 하는데 트랜잭션이 되돌려지므로, 판매 등록 직전 확인에 쓴다.
 */
export async function getAvailableBalance(
  owner: string,
  materialId: number,
): Promise<bigint> {
  const contract = getReadContract('marketplace')
  return contract.availableBalance(owner, materialId) as Promise<bigint>
}
