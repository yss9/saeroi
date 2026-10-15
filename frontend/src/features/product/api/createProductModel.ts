import { apiClient } from '../../../services/apiClient.ts'
import type { CreateProductModelRequest, ProductModel } from '../types/product.types.ts'

/**
 * 제품 등록 — `POST /v1/product-models`
 *
 * `BRAND` 만 가능하다. 역할 검사를 서버가 **DB 를 다시 읽어** 하므로(토큰의 역할 클레임을
 * 믿지 않는다) 정지된 계정은 토큰이 살아 있어도 거절된다 — `PRODUCT_ROLE_NOT_ALLOWED`,
 * `PRODUCT_BRAND_SUSPENDED`.
 *
 * **응답의 `status` 는 언제나 `DRAFT`, `stockQuantity` 는 0 이다.** 등록만으로는 팔 개체가
 * 없기 때문이다. 판매 시작은 `PATCH /v1/product-models/{id}/status` 로 따로 한다 —
 * 화면은 등록 완료를 "판매가 시작됐다" 로 말하면 안 된다.
 */
export async function createProductModel(
  payload: CreateProductModelRequest,
): Promise<ProductModel> {
  const { data } = await apiClient.post<ProductModel>('/v1/product-models', payload)
  return data
}
