import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useOnChainLink } from '../../../hooks/useOnChainLink.ts'
import { linkChainBatch } from '../api/productionChainApi.ts'
import { productionKeys } from '../api/productionKeys.ts'
import { createBatch } from '../services/productionContractService.ts'

/**
 * 온체인 배치 만들기 — 4단계 중 첫 번째.
 *
 * `useOnChainLink` 를 그대로 쓴다. 소재 발행·판매 등록·주문 생성과 모양이 같다 — 이벤트에서
 * 채번된 번호를 꺼내 서버에 알린다. 뒤의 세 단계(`submitPlanAndLock` · `confirmProduction` ·
 * `linkProductItems`)는 번호를 채번하지 않아 이 훅에 맞지 않는다.
 *
 * **다른 도메인과 순서가 반대다.** 소재·판매글은 서버에 행을 만들고 체인에 서명하지만,
 * 생산은 배치가 이미 서버에 있고 확정까지 끝난 상태다. 비어 있는 쪽이 체인이라 서명이
 * 먼저이고 그 결과만 알린다.
 *
 * ⚠️ **`findExisting` 을 주지 않는다.** 다른 도메인은 서버 보고가 실패했을 때 체인에서
 * "이미 만들어져 있는지" 찾아 중복 서명을 막지만, `ProductionRegistry` 에는 그 조회가
 * 없다 — "내 배치 목록" 이 없고 `getBatch(batchId)` 는 번호를 이미 알아야 부를 수 있다.
 * 번호를 잃은 상황이 정확히 그 번호를 모르는 상황이다.
 *
 * 그래서 중복 방지는 서버에 맡긴다. 두 번째 보고는
 * `PRODUCTION_CHAIN_BATCH_ALREADY_LINKED` 로 거절되므로 **서버가 가리키는 번호는 하나로
 * 유지된다** — 체인에 고아 배치가 남는 것이 그 대가다. 조회 경로가 생기면 여기를 채운다.
 */
export function useCreateChainBatch(batchId: number, producedQuantity: number) {
  const queryClient = useQueryClient()

  const link = useOnChainLink({
    contract: 'productionRegistry',
    eventName: 'ProductionBatchCreated',
    argName: 'batchId',
    operationContract: 'PRODUCTION_REGISTRY',
    functionName: 'createBatch',
    referenceType: 'PRODUCTION',
    link: (targetId, payload) =>
      linkChainBatch(targetId, {
        chainBatchId: payload.onChainId,
        txHash: payload.txHash,
        blockNumber: payload.blockNumber,
      }),
    // 배치에 chainBatchId 가 붙으면 다음 단계로 넘어갈 수 있다. 상세를 다시 읽는다.
    onLinked: () => {
      void queryClient.invalidateQueries({ queryKey: productionKeys.detail(batchId) })
      void queryClient.invalidateQueries({ queryKey: productionKeys.lists() })
    },
  })

  const create = useCallback(
    () => link.run(batchId, (operationId) => createBatch(producedQuantity, operationId)),
    [link, batchId, producedQuantity],
  )

  return { ...link, create }
}
