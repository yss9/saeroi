// Marketplace 온체인 호출.
//
// 📌 결제는 여기 없다 (정책 08절). 대금은 카카오페이 PG 로 오프체인 처리하고
// 온체인에는 소유권 이전 결과만 남는다. `markPaid` 는 PAYMENT_MANAGER_ROLE 이 필요한
// **서버 전용** 함수라 이 파일에 노출하지 않는다 — 프론트에서 부르면 권한 오류가 난다.

import type { ContractTransactionResponse } from 'ethers'
import {
  LISTING_STATUS_ON_CHAIN,
  ORDER_STATUS_ON_CHAIN,
  decodeEnum,
} from '../../../config/abi/onchainEnums.ts'
import { getReadContract, getWriteContract } from '../../../services/blockchain/contractFactory.ts'

// ── 쓰기 ────────────────────────────────────────────────

/**
 * 판매 등록. 등록과 동시에 MARKETPLACE Lock 이 생성된다 (정책 06.3).
 *
 * @param quantity  gram 정수
 * @param pricePerKg  kg당 가격 (원)
 *
 * listingId 는 반환값이지만 트랜잭션에서는 읽을 수 없다 —
 * `ListingCreated` 이벤트에서 꺼낸다 (contractFactory.readEventArg).
 */
export async function createListing(
  materialId: number,
  quantity: number,
  pricePerKg: number,
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  return contract.createListing(materialId, quantity, pricePerKg) as Promise<ContractTransactionResponse>
}

/** 판매 취소. 남은 수량의 Lock 이 해제된다. */
export async function cancelListing(listingId: number): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  return contract.cancelListing(listingId) as Promise<ContractTransactionResponse>
}

/**
 * 구매 요청. 해당 수량이 예약되어 다른 구매자가 살 수 없게 된다 (정책 07.2).
 * 대금 결제는 이 트랜잭션과 별개로 PG 에서 진행된다.
 */
export async function createOrder(
  listingId: number,
  quantity: number,
): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  return contract.createOrder(listingId, quantity) as Promise<ContractTransactionResponse>
}

export async function cancelOrder(orderId: number): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  return contract.cancelOrder(orderId) as Promise<ContractTransactionResponse>
}

/** Seller 배송 처리. 이 시점에도 Lock 은 유지된다 — 배송 중 재판매를 막는다. */
export async function markShipped(orderId: number): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  return contract.markShipped(orderId) as Promise<ContractTransactionResponse>
}

/**
 * Buyer 수령 확인.
 *
 * **여기서 소유권이 이전된다.** 배송 시점이 아니라 수령 확인 시점이다 (정책 07.2).
 * Lock 이 CONSUMED 로 바뀌고 Balance 가 Seller → Buyer 로 옮겨간다.
 */
export async function confirmReceipt(orderId: number): Promise<ContractTransactionResponse> {
  const contract = await getWriteContract('marketplace')
  return contract.confirmReceipt(orderId) as Promise<ContractTransactionResponse>
}

// ── 조회 ────────────────────────────────────────────────

export interface OnChainListing {
  id: bigint
  materialId: bigint
  materialLockId: bigint
  seller: string
  initialQuantity: bigint
  remainingQuantity: bigint
  reservedQuantity: bigint
  pricePerKg: bigint
  status: (typeof LISTING_STATUS_ON_CHAIN)[number] | null
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
    pricePerKg: raw.pricePerKg,
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
  totalPrice: bigint
  /** PG 결제 참조(tid)의 해시. 원문은 온체인에 올리지 않는다. */
  paymentReferenceHash: string
  status: (typeof ORDER_STATUS_ON_CHAIN)[number] | null
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
    totalPrice: raw.totalPrice,
    paymentReferenceHash: raw.paymentReferenceHash,
    status: decodeEnum(ORDER_STATUS_ON_CHAIN, raw.status),
  }
}

/**
 * 총액을 컨트랙트에게 물어본다.
 *
 * 프론트에서 직접 곱하지 않고 이걸 쓰는 이유 — 반올림 규칙이 다르면 화면 금액과
 * 실제 체결 금액이 어긋난다. 미리보기 단계에서는 utils/quantity 의 로컬 계산을 쓰고,
 * 확정 직전에는 이 값으로 한 번 맞춰본다.
 */
export async function calculateTotalPrice(
  quantityGram: number,
  pricePerKg: number,
): Promise<bigint> {
  const contract = getReadContract('marketplace')
  return contract.calculateTotalPrice(quantityGram, pricePerKg) as Promise<bigint>
}
