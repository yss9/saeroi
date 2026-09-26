import { apiClient } from '../../../services/apiClient.ts'
import type { ProductModel } from '../types/product.types.ts'

/**
 * 제품 수정 요청. **보낸 필드만 바뀐다.**
 *
 * 카테고리와 판매 상태는 없다 — 카테고리를 바꾸면 이미 그 분류로 팔린 제품의 이력이
 * 흔들리고, 상태는 재고를 봐야 정해지는 값이라 `PATCH /{id}/status` 가 따로 맡는다.
 */
export interface UpdateProductModelRequest {
  name?: string
  /** 원 단위 정수 */
  price?: number
  description?: string
  /** 이미지 업로드가 돌려준 저장 키. 바꾸지 않으면 보내지 않는다 */
  thumbnailUrl?: string
}

/**
 * 제품 수정 — `PATCH /v1/product-models/{productModelId}`
 *
 * **부분 수정이다.** 가격만 고치려면 `{ price }` 하나만 보낸다. "안 바꿈" 은 필드를 아예
 * 보내지 않는 것으로 말한다 — 빈 문자열은 지우기가 아니라 실수로 보고 서버가 거절한다
 * (`PRODUCT_MODEL_NOTHING_TO_UPDATE`).
 *
 * **판매 중에도 가격을 바꿀 수 있다.** 세일을 막으면 가격을 내리려고 제품을 새로 등록하게
 * 되고, 그러면 이미 만든 개체가 옛 제품에 붙는다. 이미 결제된 주문은 영향받지 않는다 —
 * 주문 시점 단가가 스냅샷으로 남는다.
 *
 * 거절 조건: 아무것도 안 보냄·빈 문자열(`PRODUCT_MODEL_NOTHING_TO_UPDATE`), 업로드되지 않은
 * 이미지 키(`PRODUCT_THUMBNAIL_NOT_STORED`), 남의 제품(`NOT_PRODUCT_MODEL_OWNER`),
 * 단종된 제품(`PRODUCT_MODEL_DISCONTINUED`).
 */
export async function updateProductModel(
  productModelId: number,
  request: UpdateProductModelRequest,
): Promise<ProductModel> {
  const { data } = await apiClient.patch<ProductModel>(
    `/v1/product-models/${productModelId}`,
    normalize(request),
  )
  return data
}

/**
 * 빈 값을 빼고 보낸다.
 *
 * 폼은 언제나 문자열을 준다(안 고친 칸도 `''` 가 아니라 원래 값이지만, 지운 칸은 `''` 다).
 * 그 빈 문자열을 그대로 보내면 서버가 400 으로 거절하는데, 사용자 입장에서는 "이름을
 * 지우려 했다" 가 아니라 실수인 경우가 대부분이다 — 보내지 않으면 원래 값이 남는다.
 */
function normalize(request: UpdateProductModelRequest): UpdateProductModelRequest {
  const name = request.name?.trim()
  const description = request.description?.trim()
  const thumbnailUrl = request.thumbnailUrl?.trim()

  return {
    ...(name ? { name } : {}),
    ...(request.price !== undefined ? { price: request.price } : {}),
    ...(description ? { description } : {}),
    ...(thumbnailUrl ? { thumbnailUrl } : {}),
  }
}
