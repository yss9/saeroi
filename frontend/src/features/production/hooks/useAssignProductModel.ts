import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { assignProductModel } from '../api/productionBatchApi.ts'
import { productionKeys } from '../api/productionKeys.ts'
import type { ProductionBatch } from '../types/production.types.ts'

/**
 * 배치가 무엇을 만들지 정한다.
 *
 * **배치를 열 때가 아니라 여기서 정한다.** 실제 순서가 "배치 생성 → 소재 선택 → 사용량
 * 입력" 이라 소재도 고르기 전에 완성 제품을 확정할 이유가 없다. 확정 전까지만 채워지면
 * 되고, 비어 있으면 서버가 `PRODUCTION_PRODUCT_MODEL_REQUIRED` 로 확정을 거절한다.
 *
 * 응답은 배치 전체이고 **투입 목록도 함께 온다** — 확정 응답과 달리 여기서는 서버가
 * 목록을 채워 주므로 그대로 캐시에 넣어도 투입이 사라지지 않는다.
 *
 * 목록도 함께 비운다. 배치를 고르는 쪽(제품 등록 화면)이 "아직 제품이 없는 배치" 만
 * 추려 보여주므로, 목록을 갱신하지 않으면 방금 연결한 배치가 계속 후보로 남는다.
 */
export interface AssignProductModelVariables {
  batchId: number
  productModelId: number
}

export function useAssignProductModel() {
  const queryClient = useQueryClient()

  return useMutation<ProductionBatch, AppError, AssignProductModelVariables>({
    mutationFn: ({ batchId, productModelId }) =>
      assignProductModel(batchId, { productModelId }),

    onSuccess: (batch) => {
      queryClient.setQueryData<ProductionBatch>(
        productionKeys.detail(batch.productionBatchId),
        batch,
      )
      void queryClient.invalidateQueries({ queryKey: productionKeys.lists() })
    },
  })
}
