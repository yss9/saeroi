// Marketplace 도메인 타입.
//
// 문자열 값은 Backend `material_listing` ENUM 과 정확히 일치시킨다.
// 수량은 전부 **gram 정수**, 금액은 **원** 이다 (정책 03·08절).

import type { MaterialGrade, MaterialRootOrigin, MaterialType } from '../../material/types/material.types.ts'

/** `material_listing.status` */
export type ListingStatus = 'ON_SALE' | 'SOLD_OUT' | 'CANCELED'

export const LISTING_STATUS_LABEL: Record<ListingStatus, string> = {
  ON_SALE: '판매 중',
  SOLD_OUT: '판매 완료',
  CANCELED: '판매 취소',
}

/**
 * 판매글 요약. 목록 카드에 필요한 만큼만 담는다.
 *
 * ⚠️ 수량이 **두 개** 인 것이 이 도메인의 핵심이다.
 * 부분 구매를 지원하므로 30kg 을 올려 10kg 이 팔리면 `totalQuantity` 는 그대로 30kg,
 * `availableQuantity` 만 20kg 으로 줄어든다. 하나로 합치면 "얼마나 팔렸는지" 를 잃는다.
 */
export interface ListingSummary {
  id: number
  materialId: number
  materialCode: string
  materialName: string
  materialType: MaterialType
  rootOrigin: MaterialRootOrigin
  grade: MaterialGrade | null
  /** 최초 등록 수량 (gram) */
  totalQuantity: number
  /** 남은 판매 가능 수량 (gram). 구매 요청이 들어오면 줄어든다 */
  availableQuantity: number
  /** kg당 가격 (원) */
  pricePerKg: number
  status: ListingStatus
  sellerId: number
  sellerName: string
  createdAt: string
}

export interface ListingDetail extends ListingSummary {
  description: string | null
  /** 판매 등록 시 생성된 Lock. 취소하면 이 Lock 이 해제된다 */
  materialLockId: number | null
  canceledAt: string | null
  cancelReason: string | null
}

/**
 * 판매 등록 요청.
 *
 * `materialId` 와 수량만 받는다. 소재 정보(종류·등급·출처)는 서버가 Material 에서
 * 가져온다 — 판매자가 다시 입력하게 하면 실제 소재와 다른 값을 적을 수 있고,
 * 그러면 검색 필터가 거짓말을 하게 된다.
 */
export interface CreateListingRequest {
  materialId: number
  /** 판매 수량 (gram). 보유 가용 수량을 넘을 수 없다 */
  quantity: number
  /** kg당 가격 (원) */
  pricePerKg: number
  description?: string
}

/**
 * 목록 검색 필터. Query Key 에 그대로 들어가므로 직렬화 가능해야 한다.
 *
 * 정책 FR-011 이 요구하는 조건: 소재 종류 · 출처 유형 · 가격 · 수량 · 지역 · 검증 상태.
 * 지역은 `processor_profile.region` 과 달리 판매자 회사 주소에서 오는데 아직 정해지지
 * 않아 넣지 않았다.
 *
 * TODO(3번): 지역 필터 기준을 Backend 와 합의한 뒤 추가한다.
 */
export interface ListingFilters {
  materialType?: MaterialType
  rootOrigin?: MaterialRootOrigin
  grade?: MaterialGrade
  /** kg당 최소·최대 가격 (원) */
  minPricePerKg?: number
  maxPricePerKg?: number
  /** 최소 판매 가능 수량 (gram) — 이만큼은 살 수 있는 판매글만 */
  minAvailableQuantity?: number
  /** 기본값은 판매 중인 것만 본다 */
  status?: ListingStatus
  page?: number
  size?: number
}

/** 목록 정렬. 서버가 지원하는 값만 둔다. */
export type ListingSort = 'LATEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'QUANTITY_DESC'

export const LISTING_SORT_LABEL: Record<ListingSort, string> = {
  LATEST: '최신순',
  PRICE_ASC: '가격 낮은순',
  PRICE_DESC: '가격 높은순',
  QUANTITY_DESC: '수량 많은순',
}
