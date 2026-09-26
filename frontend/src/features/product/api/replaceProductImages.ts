import { apiClient } from '../../../services/apiClient.ts'
import type { ProductImageItem, ProductImageRef } from '../types/product.types.ts'

/**
 * 상세 갤러리 교체 — `PUT /v1/product-models/{productModelId}/images`
 *
 * **보낸 목록이 곧 갤러리 전체다.** 서버는 추가·삭제·순서변경을 따로 받지 않는다 —
 * 갤러리는 자리가 곧 의미라, 세 요청이 섞이면 중간 상태에서 자리가 겹치거나 빈다.
 * 화면은 이미 "지금 이 순서" 를 들고 있으므로 통째로 보낸다.
 *
 * **순서는 배열의 순서다.** 대표 사진을 맨 앞에 둔다.
 *
 * 거절 조건: 10장 초과(`PRODUCT_IMAGE_TOO_MANY`), 같은 저장 키 둘
 * (`PRODUCT_IMAGE_DUPLICATED`), 대표 둘 이상(`PRODUCT_IMAGE_MAIN_NOT_UNIQUE`), 올라오지
 * 않은 저장 키(`PRODUCT_IMAGE_NOT_STORED`), 단종된 제품(`PRODUCT_MODEL_DISCONTINUED`).
 *
 * 업로드는 이 요청이 하지 않는다. `uploadProductImage` 가 돌려준 저장 키를 참조할 뿐이다 —
 * 등록 요청이 `thumbnailUrl` 을 다루는 방식과 같다.
 */
export async function replaceProductImages(
  productModelId: number,
  images: readonly ProductImageRef[],
): Promise<ProductImageItem[]> {
  const { data } = await apiClient.put<ProductImageItem[]>(
    `/v1/product-models/${productModelId}/images`,
    { images },
  )
  return data
}
