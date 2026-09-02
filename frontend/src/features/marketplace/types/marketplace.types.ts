// Marketplace 도메인 타입. 문자열은 Backend `material_listing` ENUM 과 일치한다.

/** `material_listing.status` */
export type ListingStatus = 'ON_SALE' | 'SOLD_OUT' | 'CANCELED'

export interface ListingSummary {
  id: number
  materialId: number
  materialName: string
  quantity: number
  unitPrice: number
  status: ListingStatus
  sellerId: number
  createdAt: string
}

// TODO(3번/Marketplace 담당): 상세·검색 필터·등록 요청 타입을 여기에 추가한다.
