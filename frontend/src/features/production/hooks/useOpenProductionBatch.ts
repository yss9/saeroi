import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { openProductionBatch } from '../api/productionBatchApi.ts'
import { productionKeys } from '../api/productionKeys.ts'
import type { OpenProductionBatchRequest, ProductionBatch } from '../types/production.types.ts'

/**
 * 배치 열기.
 *
 * 새로 열린 배치는 투입이 없는 것이 사실이므로 응답을 상세 캐시에 그대로 심어도 된다
 * (확정 응답과 다르다 — 그쪽은 투입이 있는데도 빈 배열로 온다).
 */
export function useOpenProductionBatch() {
  const queryClient = useQueryClient()

  return useMutation<ProductionBatch, AppError, OpenProductionBatchRequest>({
    mutationFn: openProductionBatch,
    onSuccess: (batch) => {
      queryClient.setQueryData(productionKeys.detail(batch.productionBatchId), batch)
      void queryClient.invalidateQueries({ queryKey: productionKeys.lists() })
    },
  })
}
