import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getMyProductModels } from '../api/getMyProductModels.ts'
import { productKeys } from '../api/productKeys.ts'
import type { ProductModel } from '../types/product.types.ts'

/**
 * 내가 등록한 제품 목록.
 *
 * 키에 `mine` 을 넣는다. 마켓 목록과 **같은 캐시를 쓰면 안 된다** — 이쪽은 `DRAFT` 를
 * 포함하고 저쪽은 `ON_SALE` 만 담는다. 한 키를 공유하면 마켓 화면에 아직 공개하지 않은
 * 제품이 스쳐 보인다.
 */
export function useMyProductModels() {
  return useQuery<ProductModel[], AppError>({
    queryKey: productKeys.list({ mine: true }),
    queryFn: getMyProductModels,
  })
}
