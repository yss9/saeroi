import type { ProductCategory, ProductModelStatus } from './product.types.ts'

/** 소비자 쇼핑몰에 공개되는 판매 상품. */
export interface PublicProductModel {
  productModelId: number
  name: string
  category: ProductCategory
  brandId: number
  brandName: string
  price: number
  description: string
  /** 공개 이미지 URL. 정적 데모 이미지와 공개 썸네일 API 경로를 모두 받을 수 있다. */
  thumbnailUrl: string
  status: ProductModelStatus
  stockQuantity: number
  createdAt: string
}

/** 공개 목록은 서버에서 항상 ON_SALE로 제한되므로 status 조건을 받지 않는다. */
export interface PublicProductModelFilters {
  category?: ProductCategory
  brandId?: number
  /** 0-based */
  page?: number
  /** 기본 12, 최대 100 */
  size?: number
}
