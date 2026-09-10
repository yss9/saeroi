import { apiClient } from '../../../services/apiClient.ts'
import type { ProductModel, ProductStatusAction } from '../types/product.types.ts'

/**
 * 판매 시작 / 판매 중단 — `PATCH /v1/product-models/{id}/status`
 *
 * **`SOLD_OUT` 을 지정할 수 없다.** 품절은 브랜드가 정하는 값이 아니라 개체를 세어 나오는
 * 사실이고, 판매·취소 처리가 알아서 전환한다. 손으로 바꿀 수 있게 두면 재고가 있는데
 * 품절이거나 그 반대인 상태가 만들어진다.
 *
 * 서버가 거절하는 두 경우를 화면이 미리 알아야 한다.
 *
 * | 코드 | 뜻 |
 * | --- | --- |
 * | `PRODUCT_MODEL_HAS_NO_STOCK` | 재고 0 인 제품은 판매를 시작할 수 없다 — 들어오자마자 살 수 없는 제품이 마켓에 걸린다 |
 * | `PRODUCT_MODEL_DISCONTINUED` | 단종은 **되돌릴 수 없다** |
 *
 * 본인 제품만 바꿀 수 있다 (`NOT_PRODUCT_MODEL_OWNER`). 역할만 보면 브랜드 아무나 남의
 * 제품을 단종시킬 수 있어서, 서버가 소유자를 대조한다.
 */
export async function changeProductModelStatus(
  productModelId: number,
  action: ProductStatusAction,
): Promise<ProductModel> {
  const { data } = await apiClient.patch<ProductModel>(
    `/v1/product-models/${productModelId}/status`,
    { action },
  )
  return data
}
