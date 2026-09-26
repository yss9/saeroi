import { useQuery } from '@tanstack/react-query'
import { getReadContract } from '../../../services/blockchain/contractFactory.ts'
import { isContractConfigured } from '../../../config/contracts.ts'

/**
 * 컨트랙트가 말하는 배치 상태. `ProductionRegistry.Status` 와 같은 순서다.
 *
 *   NONE      아직 만들어지지 않음
 *   DRAFT     createBatch 까지 (1단계)
 *   SIGNING   submitPlanAndLock 까지 (2단계)
 *   CONFIRMED confirmProduction 까지 (3단계)
 */
export type ChainBatchStatus = 'NONE' | 'DRAFT' | 'SIGNING' | 'CONFIRMED' | 'FAILED' | 'CANCELED'

const STATUS: readonly ChainBatchStatus[] = [
  'NONE',
  'DRAFT',
  'SIGNING',
  'CONFIRMED',
  'FAILED',
  'CANCELED',
]

export interface ProductionChainState {
  status: ChainBatchStatus
  /** 연결된 제품 개체 수. 0 이면 4단계가 아직이다 */
  linkedItemCount: number
}

/**
 * 온체인 진행 상태.
 *
 * **서버 응답이 아니라 체인에 직접 묻는다.** 배치 응답으로는 `chainBatchId` 와
 * `evidenceHash` 까지만 알 수 있어 3·4단계가 끝났는지 알 방법이 없고, 무엇보다 **서명은
 * 됐는데 서버 보고가 실패한 경우** 서버 기준으로는 영원히 "아직 안 함" 으로 보인다.
 * 그 상태에서 다시 서명하면 컨트랙트가 거절하므로 화면이 막다른 길이 된다.
 *
 * 체인을 읽으면 그 어긋남이 생기지 않는다 — 다음에 할 수 있는 일은 언제나 체인이 정한다.
 *
 * 읽기 전용 호출이라 지갑 서명이 필요 없다. `staleTime` 을 짧게 두는 이유는 서명 직후
 * 무효화로 다시 읽기 때문이다.
 */
export function useProductionChainStatus(chainBatchId: number | null) {
  return useQuery<ProductionChainState>({
    queryKey: ['chain', 'production-batch', chainBatchId],
    enabled: chainBatchId !== null && isContractConfigured('productionRegistry'),
    staleTime: 10_000,
    queryFn: async () => {
      const contract = getReadContract('productionRegistry')
      const [batch, items] = await Promise.all([
        contract.getBatch(chainBatchId) as Promise<{ status: bigint }>,
        contract.getProductItems(chainBatchId) as Promise<unknown[]>,
      ])
      return {
        status: STATUS[Number(batch.status)] ?? 'NONE',
        linkedItemCount: items.length,
      }
    },
  })
}
