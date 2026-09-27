import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import { cancelProductionBatch } from '../api/productionBatchApi.ts'
import { productionKeys } from '../api/productionKeys.ts'
import type { ProductionBatch } from '../types/production.types.ts'

/**
 * 배치 취소.
 *
 * **소재 쪽도 함께 무효화한다.** 취소의 목적이 투입해 둔 소재의 잠금을 푸는 것이라, 배치
 * 상태만 갈아 끼우면 화면은 여전히 그 소재를 쓸 수 없는 것으로 보여준다 — 담당자는 취소가
 * 아무 일도 하지 않았다고 읽는다.
 *
 * `product` 는 무효화하지 않는다. 확정과 달리 개체가 만들어지거나 사라지지 않는다.
 *
 * 확정과 달리 응답의 `inputs` 는 채워져 있어(`ProductionBatchResponse.of(batch,
 * inputsOf(batchId))`) 그대로 캐시에 넣어도 투입이 사라지지 않는다.
 */
export function useCancelProductionBatch(batchId: number) {
  const queryClient = useQueryClient()

  return useMutation<ProductionBatch, AppError, void>({
    mutationFn: () => cancelProductionBatch(batchId),

    onSuccess: (batch) => {
      queryClient.setQueryData<ProductionBatch>(productionKeys.detail(batchId), batch)
      void queryClient.invalidateQueries({ queryKey: productionKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
      void queryClient.invalidateQueries({ queryKey: materialKeys.all })
    },
  })
}
