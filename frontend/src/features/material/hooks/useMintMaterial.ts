import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useBlockchainTransaction } from '../../../hooks/useBlockchainTransaction.ts'
import { materialKeys } from '../api/materialKeys.ts'
import { mintMaterialToken } from '../services/materialContractService.ts'

/**
 * Material Token 발행 훅 — 온체인 쓰기 + 오프체인 캐시 갱신을 잇는 예시.
 *
 * 여기서 보여주려는 패턴:
 *   1) 트랜잭션 상태 기계는 공통 훅(useBlockchainTransaction)이 관리한다
 *   2) 도메인은 "무엇을 보낼지"(contractService 호출)만 넘긴다
 *   3) CONFIRMED 이후에 서버 조회를 무효화한다 — Backend 가 이벤트를 수집해
 *      MySQL 조회 모델을 갱신하므로, 확정 전에 다시 조회해도 옛 값이 온다
 */
export function useMintMaterial(materialId: number) {
  const queryClient = useQueryClient()

  const transaction = useBlockchainTransaction({
    onConfirmed: () => {
      void queryClient.invalidateQueries({ queryKey: materialKeys.detail(materialId) })
      void queryClient.invalidateQueries({ queryKey: materialKeys.lists() })
    },
  })

  const mint = useCallback(
    (quantity: number) => transaction.execute(() => mintMaterialToken(materialId, quantity)),
    [transaction, materialId],
  )

  return { ...transaction, mint }
}
