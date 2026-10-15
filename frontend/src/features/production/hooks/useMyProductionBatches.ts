import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getMyProductionBatches } from '../api/productionBatchApi.ts'
import { productionKeys } from '../api/productionKeys.ts'
import type { ProductionBatch } from '../types/production.types.ts'

/** 내 생산 배치 목록. 페이징이 없다 — 서버가 배열을 그대로 준다. */
export function useMyProductionBatches() {
  return useQuery<ProductionBatch[], AppError>({
    queryKey: productionKeys.list({ mine: true }),
    queryFn: getMyProductionBatches,
  })
}
