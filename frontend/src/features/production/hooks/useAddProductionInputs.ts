import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import { addProductionInputs } from '../api/addProductionInputs.ts'
import { productionKeys } from '../api/productionKeys.ts'
import type { ProductionBatch, ProductionInputRequest } from '../types/production.types.ts'

/**
 * 배치에 소재 투입.
 *
 * **보유 소재 캐시를 반드시 무효화한다.** 투입은 그 수량을 잠그므로 가용량이 줄어드는데,
 * 목록이 그대로면 사용자는 이미 잠긴 수량을 또 투입하려 하고 서버가 거절한다.
 *
 * 배치 상세는 응답으로 받은 값을 그대로 심는다 — 응답이 투입 목록까지 담은 배치 전체라
 * 반쪽짜리가 되지 않는다.
 */
export function useAddProductionInputs(batchId: number) {
  const queryClient = useQueryClient()

  return useMutation<ProductionBatch, AppError, ProductionInputRequest[]>({
    mutationFn: (inputs) => addProductionInputs(batchId, inputs),

    onSuccess: (batch) => {
      queryClient.setQueryData(productionKeys.detail(batchId), batch)
      void queryClient.invalidateQueries({ queryKey: productionKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
      void queryClient.invalidateQueries({ queryKey: materialKeys.all })
    },
  })
}
