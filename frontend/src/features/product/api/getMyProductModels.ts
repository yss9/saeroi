import { apiClient } from '../../../services/apiClient.ts'
import type { ProductModel } from '../types/product.types.ts'

/**
 * 내가 등록한 제품 — `GET /v1/product-models/me`
 *
 * **`DRAFT` 를 포함한다.** 브랜드는 여기서 판매 시작을 눌러야 하는데 준비 중인 제품이
 * 안 보이면 그 버튼을 놓을 자리가 없다. 마켓 목록(`GET /v1/product-models`)은 반대로
 * `ON_SALE` 만 보여준다 — 두 목록은 같은 것이 아니다.
 *
 * 브랜드 id 를 보내지 않는다. 서버가 JWT 에서 강제한다 — 파라미터로 받으면 남의 제품
 * 목록을 볼 수 있고, 거기에는 아직 공개하지 않은 제품이 들어 있다.
 *
 * 페이징이 없다. 서버가 배열을 그대로 준다 (`List<ProductModelResponse>`).
 */
export async function getMyProductModels(): Promise<ProductModel[]> {
  const { data } = await apiClient.get<ProductModel[]>('/v1/product-models/me')
  return data
}
