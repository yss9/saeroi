import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { assignProductModel } from '../api/productionBatchApi.ts'
import { productionKeys } from '../api/productionKeys.ts'
import type { ProductionBatch } from '../types/production.types.ts'

/**
 * 생산이 무엇을 만들었는지 정한다.
 *
 * **확정된 뒤에 붙이는 것이 정상 순서다.** 브랜드의 실제 일은 "생산을 끝내고 → 그 결과물에
 * 제품 정보를 붙인다" 이고, 서버도 확정된 생산을 받아 그 생산이 만든 개체의 제품까지 같은
 * 트랜잭션에서 채운다. 작성 중인 생산에 미리 붙여 두는 것도 그대로 된다.
 *
 * **한 번 붙이면 바꿀 수 없다** (`PRODUCTION_PRODUCT_MODEL_ALREADY_SET`, 409). 개체가 팔린
 * 뒤 제품이 바뀌면 그 주문이 산 것과 재고가 가리키는 제품이 달라지고, 이미 인쇄된 QR 이
 * 다른 제품의 여권을 열게 된다.
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
