import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getProductionBatch } from '../api/productionBatchApi.ts'
import { productionKeys } from '../api/productionKeys.ts'
import type { ProductionBatch } from '../types/production.types.ts'

/** 배치 상세. id 가 없으면(주소가 잘못됨) 요청하지 않는다. */
export function useProductionBatch(batchId: number | null) {
  return useQuery<ProductionBatch, AppError>({
    queryKey: productionKeys.detail(batchId ?? 0),
    queryFn: () => getProductionBatch(batchId as number),
    enabled: batchId !== null,
  })
}
