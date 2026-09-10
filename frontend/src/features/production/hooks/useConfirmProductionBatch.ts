import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import { productKeys } from '../../product/api/productKeys.ts'
import { confirmProductionBatch } from '../api/productionBatchApi.ts'
import { productionKeys } from '../api/productionKeys.ts'
import type { ProductionBatch } from '../types/production.types.ts'

/**
 * 배치 확정.
 *
 * **이 요청 하나가 세 도메인을 바꾼다.** 그래서 무효화도 세 곳이다.
 *
 * | 무효화 | 왜 |
 * | --- | --- |
 * | inventory · material | 투입한 소재가 소비되고, 자투리가 **새 소재로 발행**된다 |
 * | product | 개체가 만들어져 `stockQuantity` 가 오른다 — 그래야 판매 시작을 누를 수 있다 |
 * | production | 배치 상태가 `CONFIRMED` 로 바뀐다 |
 *
 * product 를 빼면 방금 생산한 브랜드가 "내 제품" 에서 여전히 재고 0 을 보고 판매 시작을
 * 누를 수 없다 — 확정의 목적이 그것인데 화면이 그 사실을 모른다.
 *
 * ⚠️ **응답의 `inputs` 가 빈 배열이라 그대로 덮으면 화면에서 투입이 사라진다.** 확정
 * 서비스가 목록을 다시 만들지 않기 때문이고, 투입이 지워진 것이 아니다. 캐시에 있던 투입을
 * 유지한 채 배치 값만 갈아 끼우고, 이어서 상세를 무효화해 서버 값으로 맞춘다.
 */
export function useConfirmProductionBatch(batchId: number) {
  const queryClient = useQueryClient()

  return useMutation<ProductionBatch, AppError, void>({
    mutationFn: () => confirmProductionBatch(batchId),

    onSuccess: (batch) => {
      queryClient.setQueryData<ProductionBatch>(productionKeys.detail(batchId), (current) =>
        current ? { ...batch, inputs: current.inputs } : batch,
      )
      void queryClient.invalidateQueries({ queryKey: productionKeys.detail(batchId) })
      void queryClient.invalidateQueries({ queryKey: productionKeys.lists() })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
      void queryClient.invalidateQueries({ queryKey: materialKeys.all })
      void queryClient.invalidateQueries({ queryKey: productKeys.all })
    },
  })
}
