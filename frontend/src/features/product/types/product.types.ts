// Product 도메인 타입. Backend `product_model` / `product_item` / `product_order` ENUM 과 일치한다.

/** `product_model.status` */
export type ProductModelStatus = 'DRAFT' | 'ON_SALE' | 'SOLD_OUT' | 'DISCONTINUED'

/** `product_item.status` — 개별 제품 단위 */
export type ProductItemStatus = 'IN_STOCK' | 'RESERVED' | 'SOLD'

/** `product_order.status` */
export type ProductOrderStatus = 'CREATED' | 'PAID' | 'SHIPPED' | 'DELIVERED'

/** `product_model.category` — 정책 v3 부록에서 6종으로 확정 */
export type ProductCategory = 'BAG' | 'WALLET' | 'CLOTHING' | 'ACCESSORY' | 'HOME' | 'ETC'

/** 화면 표시용 카테고리 라벨. 로직에서 이 문자열로 분기하지 말 것. */
export const PRODUCT_CATEGORIES: readonly ProductCategory[] = [
  'BAG',
  'WALLET',
  'CLOTHING',
  'ACCESSORY',
  'HOME',
  'ETC',
]

export const PRODUCT_CATEGORY_LABEL: Record<ProductCategory, string> = {
  BAG: '가방',
  WALLET: '지갑',
  CLOTHING: '의류',
  ACCESSORY: '액세서리',
  HOME: '홈·리빙',
  ETC: '기타',
}

export const PRODUCT_MODEL_STATUS_LABEL: Record<ProductModelStatus, string> = {
  DRAFT: '판매 준비',
  ON_SALE: '판매 중',
  SOLD_OUT: '품절',
  DISCONTINUED: '단종',
}

/**
 * 제품 모델 한 건.
 *
 * Backend 원본: `product/dto/ProductModelResponse.java`. 필드 이름을 서버와 정확히 같게 둔다
 * (`materialName` 을 그대로 쓰는 것과 같은 이유).
 *
 * `stockQuantity` 는 판매 가능한 **개체 수**다. 등록 직후에는 항상 0 이다 — 제품 모델은
 * 설계도이고 팔 물건은 생산에서 나온다.
 */
export interface ProductModel {
  productModelId: number
  name: string
  category: ProductCategory
  brandId: number
  /** 판매가 (원) */
  price: number
  description: string
  /**
   * 저장 키. **화면에 그대로 붙일 주소가 아니다.**
   *
   * 이미지를 보는 경로는 `GET /v1/product-models/{id}/thumbnail` 이고 그 응답은 인증을
   * 요구한다. 브라우저가 `<img src>` 에 Authorization 헤더를 붙이지 않으므로, 목록·상세는
   * fetch 로 받아 blob URL 로 바꿔 써야 한다.
   */
  thumbnailUrl: string
  status: ProductModelStatus
  /** 판매 가능한 개체 수. 등록 직후 0 */
  stockQuantity: number
  createdAt: string
}

/**
 * 제품 등록 요청 (`POST /v1/product-models`).
 *
 * 요청이 정할 수 없는 값이 둘 있다 — `brandId`(JWT 에서 꺼낸다)와 `status`(언제나 `DRAFT`).
 * 개체가 하나도 없는 제품이 마켓에 뜨는 것을 막기 위해 등록과 판매 시작을 나눠 두었다.
 *
 * ⚠️ **`thumbnailUrl` 이 필수다.** 이미지를 먼저 업로드해 받은 `storageKey` 를 넣는다.
 * 즉 등록은 두 번의 요청이고, 이미지 없이는 제품을 만들 수 없다.
 */
export interface CreateProductModelRequest {
  /** 필수, 200자 이하 */
  name: string
  category: ProductCategory
  /** 판매가 (원). 0 이상 정수 */
  price: number
  /** 필수. 길이 제한은 없다 */
  description: string
  /** 업로드로 받은 `storageKey`. 필수, 500자 이하 */
  thumbnailUrl: string
}

/**
 * 이미지 업로드 결과 (`POST /v1/product-images`).
 *
 * `storageKey` 를 등록 요청의 `thumbnailUrl` 에 그대로 넣는다.
 *
 * `hash` 는 기록된 바이트의 SHA-256 이다. 같은 사진을 다시 올렸는지 판단할 수 있지만
 * 지금 화면은 쓰지 않는다.
 */
export interface ProductImageUpload {
  storageKey: string
  originalFilename: string
  contentType: string
  /** 바이트 */
  size: number
  hash: string
}

/**
 * 판매 상태 변경 동작 (`ProductModelStatusRequest.Action`).
 *
 * **상태가 아니라 동작이다.** `SOLD_OUT` 을 지정할 수 없고, `DISCONTINUE` 는 되돌릴 수 없다.
 */
export type ProductStatusAction = 'ON_SALE' | 'DISCONTINUE'

/** 서버가 받는 이미지 형식. 이 밖의 형식은 `PRODUCT_IMAGE_TYPE_NOT_ALLOWED` 로 거절된다. */
export const PRODUCT_IMAGE_CONTENT_TYPES: readonly string[] = [
  'image/jpeg',
  'image/png',
  'image/webp',
]

/** 서버 상한과 같은 값 (`ProductImageService.MAX_FILE_SIZE_BYTES`). */
export const PRODUCT_IMAGE_MAX_BYTES = 10 * 1024 * 1024

// TODO(5번/Product 담당): 주문·배송 타입을 추가한다.
