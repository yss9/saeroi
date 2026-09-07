// Marketplace Mock 저장소.
//
// ⚠️ Backend API 가 나오면 **이 폴더를 통째로 지운다.**
// api/ 의 각 함수에서 mock 호출을 실제 apiClient 호출로 바꾸면 되고,
// 훅·컴포넌트·Query Key 는 손대지 않는다.
//
// 왜 상태를 가진 Mock 인가 — 고정 배열만 돌려주면 "등록했는데 목록에 안 뜬다",
// "부분 구매했는데 잔여 수량이 그대로다" 같은 것을 확인할 수 없다.
// 이 도메인의 핵심이 **부분 구매로 인한 수량 변화** 라서, 그게 눈에 보여야
// 화면이 맞게 만들어졌는지 판단할 수 있다.
//
// 새로고침하면 초기화된다. 그건 의도한 것이다 — Mock 이 영속되면 진짜 데이터처럼
// 취급하게 되고, 나중에 Backend 로 바꿀 때 차이를 놓친다.

import type { ListingDetail, ListingStatus } from '../../types/marketplace.types.ts'
import type { TradeDetail, TradeStatus } from '../../../trade/types/trade.types.ts'

/** 로그인 사용자가 없는 동안 쓰는 임시 주체. */
export const MOCK_ME = { id: 1, name: '데모 브랜드' } as const
export const MOCK_SELLER = { id: 2, name: '수거업체 A' } as const

let listingSeq = 100
let tradeSeq = 500

const listings = new Map<number, ListingDetail>()
const trades = new Map<number, TradeDetail>()

function seedListing(
  partial: Omit<ListingDetail, 'id' | 'sellerId' | 'sellerName' | 'createdAt' | 'status' | 'canceledAt' | 'cancelReason' | 'materialLockId'> &
    Partial<Pick<ListingDetail, 'status' | 'sellerId' | 'sellerName'>>,
): ListingDetail {
  const id = ++listingSeq
  const listing: ListingDetail = {
    id,
    sellerId: partial.sellerId ?? MOCK_SELLER.id,
    sellerName: partial.sellerName ?? MOCK_SELLER.name,
    status: partial.status ?? 'ON_SALE',
    createdAt: new Date(Date.now() - id * 3_600_000).toISOString(),
    materialLockId: 9000 + id,
    canceledAt: null,
    cancelReason: null,
    ...partial,
  }
  listings.set(id, listing)
  return listing
}

// ── 초기 데이터 ─────────────────────────────────────────
// 부분 구매가 일어난 판매글을 하나 섞어 둔다 (총 30kg 중 12kg 남음).
seedListing({
  materialId: 1,
  materialCode: 'MAT-000001',
  materialName: '세척된 폐데님',
  materialType: 'DENIM',
  rootOrigin: 'WASTE',
  grade: 'A',
  totalQuantity: 30_000,
  availableQuantity: 12_000,
  pricePerKg: 4_200,
  description: '봉제 공장에서 회수해 세척까지 마친 데님입니다.',
})
seedListing({
  materialId: 2,
  materialCode: 'MAT-000002',
  materialName: '재고 면 원단',
  materialType: 'COTTON',
  rootOrigin: 'VIRGIN',
  grade: 'B',
  totalQuantity: 8_000,
  availableQuantity: 8_000,
  pricePerKg: 6_500,
  description: null,
})
seedListing({
  materialId: 3,
  materialCode: 'MAT-000003',
  materialName: '분쇄 혼방 칩',
  materialType: 'MIXED',
  rootOrigin: 'WASTE',
  grade: 'C',
  totalQuantity: 50_000,
  availableQuantity: 50_000,
  pricePerKg: 1_800,
  description: '재생 원료화 대상입니다.',
})
seedListing({
  materialId: 4,
  materialCode: 'MAT-000004',
  materialName: '가죽 자투리',
  materialType: 'LEATHER',
  rootOrigin: 'WASTE',
  grade: 'B',
  totalQuantity: 5_000,
  availableQuantity: 0,
  pricePerKg: 12_000,
  status: 'SOLD_OUT',
  description: null,
})

// ── 조회 ────────────────────────────────────────────────

export function allListings(): ListingDetail[] {
  return [...listings.values()]
}

export function findListing(id: number): ListingDetail | undefined {
  return listings.get(id)
}

export function allTrades(): TradeDetail[] {
  return [...trades.values()]
}

export function findTrade(id: number): TradeDetail | undefined {
  return trades.get(id)
}

// ── 변경 ────────────────────────────────────────────────

export interface InsertListingInput {
  materialId: number
  /** gram */
  quantity: number
  /** 원/kg */
  pricePerKg: number
  description?: string
}

export function insertListing(input: InsertListingInput): ListingDetail {
  // 실제로는 서버가 Material 에서 가져오는 값들이다. Mock 이라 임시로 채운다.
  return seedListing({
    materialId: input.materialId,
    materialCode: `MAT-${String(input.materialId).padStart(6, '0')}`,
    materialName: '등록한 소재',
    materialType: 'DENIM',
    rootOrigin: 'WASTE',
    grade: null,
    totalQuantity: input.quantity,
    availableQuantity: input.quantity,
    pricePerKg: input.pricePerKg,
    description: input.description ?? null,
    sellerId: MOCK_ME.id,
    sellerName: MOCK_ME.name,
  })
}

export function updateListingStatus(
  id: number,
  status: ListingStatus,
  reason?: string,
): ListingDetail | undefined {
  const listing = listings.get(id)
  if (!listing) return undefined
  const next: ListingDetail = {
    ...listing,
    status,
    canceledAt: status === 'CANCELED' ? new Date().toISOString() : listing.canceledAt,
    cancelReason: reason ?? listing.cancelReason,
    // 취소하면 남은 수량은 판매 대상이 아니다. Lock 도 해제되므로 0 으로 만든다.
    availableQuantity: status === 'CANCELED' ? 0 : listing.availableQuantity,
  }
  listings.set(id, next)
  return next
}

/**
 * 구매 요청. 판매 가능 수량을 줄이고 거래를 만든다.
 *
 * 수량을 먼저 줄이는 이유 — 실제 서버도 그렇게 한다. 예약하지 않으면 같은 수량을
 * 두 사람이 살 수 있다 (정책 07.2 "다른 구매자가 동시에 구매할 수 없도록").
 */
export function insertTrade(listingId: number, quantity: number): TradeDetail {
  const listing = listings.get(listingId)
  if (!listing) throw new Error('listing not found')

  const id = ++tradeSeq
  const totalPrice = Math.floor((quantity * listing.pricePerKg) / 1000)

  listings.set(listingId, {
    ...listing,
    availableQuantity: listing.availableQuantity - quantity,
    status: listing.availableQuantity - quantity === 0 ? 'SOLD_OUT' : listing.status,
  })

  const trade: TradeDetail = {
    id,
    tradeCode: `TRD-${id}`,
    listingId,
    materialId: listing.materialId,
    materialCode: listing.materialCode,
    materialName: listing.materialName,
    quantity,
    pricePerKg: listing.pricePerKg,
    totalPrice,
    status: 'CREATED',
    sellerId: listing.sellerId,
    sellerName: listing.sellerName,
    buyerId: MOCK_ME.id,
    buyerName: MOCK_ME.name,
    paymentMethod: null,
    paymentReference: null,
    cancelReference: null,
    sellerWallet: null,
    buyerWallet: null,
    createdAt: new Date().toISOString(),
    paidAt: null,
    shippedAt: null,
    receivedAt: null,
    buyerSignedAt: null,
    completedAt: null,
    canceledAt: null,
    cancelReason: null,
  }
  trades.set(id, trade)
  return trade
}

export function updateTrade(id: number, patch: Partial<TradeDetail>): TradeDetail | undefined {
  const trade = trades.get(id)
  if (!trade) return undefined
  const next = { ...trade, ...patch }
  trades.set(id, next)
  return next
}

/** 거래 취소 시 판매 수량을 되돌린다 (정책 07.3). */
export function restoreListingQuantity(listingId: number, quantity: number): void {
  const listing = listings.get(listingId)
  if (!listing) return
  listings.set(listingId, {
    ...listing,
    availableQuantity: listing.availableQuantity + quantity,
    status: listing.status === 'SOLD_OUT' ? 'ON_SALE' : listing.status,
  })
}

export function tradeStatusIs(id: number, status: TradeStatus): boolean {
  return trades.get(id)?.status === status
}
