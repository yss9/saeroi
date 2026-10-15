import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import { productionKeys } from '../api/productionKeys.ts'
import { removeProductionInput } from '../api/removeProductionInput.ts'
import type { ProductionBatch } from '../types/production.types.ts'

/**
 * 투입 취소.
 *
 * **보유 소재 캐시를 무효화한다.** 취소는 잠금을 풀어 가용량을 되돌리는데, 목록이 그대로면
 * 사용자는 되살아난 수량을 못 보고 "왜 아직 못 쓰지" 로 읽는다 — 투입 추가와 정확히 반대
 * 방향의 같은 문제다.
 */
export function useRemoveProductionInput(batchId: number) {
  const queryClient = useQueryClient()

  return useMutation<ProductionBatch, AppError, number>({
    mutationFn: (inputId) => removeProductionInput(batchId, inputId),

    onSuccess: (batch) => {
      queryClient.setQueryData(productionKeys.detail(batchId), batch)
      void queryClient.invalidateQueries({ queryKey: productionKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
      void queryClient.invalidateQueries({ queryKey: materialKeys.all })
    },
  })
}
