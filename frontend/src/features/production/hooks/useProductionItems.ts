import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getProductionItems } from '../api/productionBatchApi.ts'
import { productionItemKeys } from '../api/productionKeys.ts'
import type { ProductionItem } from '../types/production.types.ts'

/**
 * 배치가 만들어낸 제품 개체.
 *
 * `enabled` 로 확정 전 호출을 막는다. 확정 전에는 개체가 없어 언제나 빈 배열이고, 그걸
 * 부르면 화면마다 의미 없는 요청이 한 번씩 늘어난다.
 *
 * 키를 배치 상세와 분리한다 — 확정으로 개체가 생기면 개체만 다시 읽으면 되고, 배치를
 * 무효화할 때마다 개체까지 다시 받을 이유가 없다.
 */
export function useProductionItems(batchId: number, enabled = true) {
  return useQuery<ProductionItem[], AppError>({
    queryKey: productionItemKeys.all(batchId),
    queryFn: () => getProductionItems(batchId),
    enabled,
  })
}
