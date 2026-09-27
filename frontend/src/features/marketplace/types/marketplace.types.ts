// Marketplace 도메인 타입.
//
// Backend 원본: backend/src/main/java/com/ssafy/upcycle/trade/dto/ListingResponse.java
// 필드 이름을 서버 응답과 **정확히 같게** 둔다. 중간에 이름을 바꾸는 어댑터를 두면
// 서버가 필드를 하나 바꿀 때 화면이 아니라 어댑터가 조용히 어긋난다.
//
// 수량은 전부 **gram 정수**, 금액은 **원** 이다 (정책 03·08절).

import type { ProcessType } from '../../processing/types/processing.types.ts'
import type { MaterialGrade, MaterialRootOrigin, MaterialType,
  MaterialGenerationType,
} from '../../material/types/material.types.ts'

/** `material_listing.status` */
export type ListingStatus = 'ON_SALE' | 'SOLD_OUT' | 'CANCELED'

export const LISTING_STATUS_LABEL: Record<ListingStatus, string> = {
  ON_SALE: '판매 중',
  SOLD_OUT: '판매 완료',
  CANCELED: '판매 취소',
}

/**
 * 판매글. **목록과 상세가 같은 모양이다.**
 *
 * 서버가 두 응답을 나누지 않는다 — 목록 카드가 필요로 하는 값과 상세가 필요로 하는 값이
 * 사실상 같고, 상세에 붙을 추적 정보(Journey·Verification)는 어차피 별도 응답이다.
 *
 * ⚠️ 수량이 **두 개** 인 것이 이 도메인의 핵심이다.
 * 부분 구매를 지원하므로 30kg 을 올려 10kg 이 팔리면 `totalQuantity` 는 그대로 30kg,
 * `availableQuantity` 만 20kg 으로 줄어든다. 하나로 합치면 "얼마나 팔렸는지" 를 잃는다.
 */
export interface Listing {
  listingId: number
  /** 온체인 Listing 번호. 체인 등록 전에는 null — "등록됐지만 온체인에 없음" 의 신호다 */
  chainListingId: number | null
  materialId: number
  materialCode: string
  materialName: string
  /**
   * 소재의 설명이다. 판매글에는 설명이 없다 — 같은 소재를 두 번 올리면 설명이 갈라져
   * 어느 쪽이 그 소재의 사실인지 알 수 없게 되기 때문이다.
   */
  materialDescription: string | null
  materialType: MaterialType
  rootOrigin: MaterialRootOrigin
  /**
   * 이 소재가 어떻게 생겼는가 — 최초 등록 / 가공 산출물 / 생산 부산물.
   *
   * **`rootOrigin` 과 함께 두 축을 이룬다.** "폐기물 + 가공 산출물" 과 "폐기물 + 최초 등록"
   * 은 구매자에게 다른 물건이다 — 앞은 이미 한 번 손이 간 소재고 뒤는 원물이다.
   */
  generationType: MaterialGenerationType
  grade: MaterialGrade | null
  sellerId: number
  sellerName: string
  /** 최초 등록 수량 (gram) */
  totalQuantity: number
  /** 남은 판매 가능 수량 (gram). 구매 요청이 들어오면 줄어든다 */
  availableQuantity: number
  /** kg당 가격 (원) */
  pricePerKg: number
  status: ListingStatus
  createdAt: string
  /**
   * 소재가 거친 가공 종류(보유 목록·상세와 같은 값). 빈 배열이면 가공 전. 가공을 거쳐도 이름은
   * 그대로라 같은 이름의 판매글을 가르는 값이다. 이 값을 싣기 전의 서버도 있어 없을 수 있다.
   */
  completedProcessTypes?: ProcessType[]
}

/**
 * 판매 등록 요청.
 *
 * `materialId` 와 수량·가격만 보낸다. 소재 정보(종류·등급·출처·설명)는 서버가 Material 에서
 * 가져온다 — 판매자가 다시 입력하게 하면 실제 소재와 다른 값을 적을 수 있고,
 * 그러면 검색 필터가 거짓말을 하게 된다.
 *
 * 수량은 `quantity` + `unit` 두 필드다. 서버가 gram 으로 환산한다 —
 * 환산을 클라이언트에 맡기면 자릿수를 잘못 곱한 요청을 서버가 구분할 방법이 없다.
 */
export interface CreateListingRequest {
  materialId: number
  quantity: number
  unit: QuantityUnit
  /** kg당 가격 (원). 0 도 허용된다 (무상 양도) */
  pricePerKg: number
}

/** 서버가 받는 수량 단위. `MaterialQuantityUnit` 과 같다. */
export type QuantityUnit = 'G' | 'KG'

/**
 * 목록 검색 필터. Query Key 에 그대로 들어가므로 직렬화 가능해야 한다.
 *
 * 정책 FR-011 이 요구하는 조건 중 **검증 상태와 지역이 빠져 있다.**
 * 검증 상태는 판정 결과를 담는 컬럼이 아직 DB 에 없어서다 — 없는 값으로 거르면
 * "검증된 소재만" 이 실제로는 전부 또는 전무가 된다 (Verification 에픽 대기).
 * 지역은 판매자 회사 주소에서 오는데 기준이 정해지지 않았다.
 */
export interface ListingFilters {
  /** 판매자 필터. Supplier Market 이 내 판매글만 보는 데 쓴다 */
  generationType?: MaterialGenerationType
  sellerId?: number
  /** 소재 상세 화면이 "이 소재가 지금 판매 중인가" 를 물을 때 쓴다 (S15P21E104-438/439) */
  materialId?: number
  materialType?: MaterialType
  rootOrigin?: MaterialRootOrigin
  grade?: MaterialGrade
  /** 소재 이름 부분 일치 */
  keyword?: string
  /** kg당 최소·최대 가격 (원) */
  minPricePerKg?: number
  maxPricePerKg?: number
  /** 최소 판매 가능 수량 (gram) — 이만큼은 살 수 있는 판매글만 */
  minAvailableQuantity?: number
  /** 기본값은 판매 중인 것만 본다 */
  status?: ListingStatus
  /** 0-based */
  page?: number
  size?: number
}

/** 목록 정렬. 서버 `ListingSearchCondition.ListingSort` 와 같은 값만 둔다. */
export type ListingSort = 'LATEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'QUANTITY_DESC'

export const LISTING_SORT_LABEL: Record<ListingSort, string> = {
  LATEST: '최신순',
  PRICE_ASC: '가격 낮은순',
  PRICE_DESC: '가격 높은순',
  QUANTITY_DESC: '수량 많은순',
}
