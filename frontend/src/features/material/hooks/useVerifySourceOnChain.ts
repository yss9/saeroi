import { useCallback, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { confirmSourceVerification } from '../../../services/blockchain/chainLinkApi.ts'
import { useBlockchainTransaction } from '../../../hooks/useBlockchainTransaction.ts'
import { materialKeys } from '../api/materialKeys.ts'
import { verifySource } from '../services/materialContractService.ts'

export interface VerifySourceOnChainParams {
  /** 우리 DB 의 소재 id. 확정 결과를 붙일 대상이다. */
  materialId: number
  /** 이미 발행된 소재의 온체인 번호. mint 가 끝나 있어야 한다. */
  tokenId: number
  evidenceHash: string
}

/**
 * 이미 발행된 소재의 출처를 온체인에서 검증한다.
 *
 * <b>{@link useOnChainLink} 을 쓰지 않는다.</b> 그 훅은 확정 이벤트에서 **새로 채번된
 * 번호**를 꺼내는 것을 전제한다(mint 의 `tokenId`, 판매 등록의 `listingId` 처럼).
 * `verifySource` 는 이미 아는 `tokenId` 를 검증하는 것뿐이라 꺼낼 새 번호가 없다 —
 * 그래서 `useBlockchainTransaction` 위에 직접 짓는다.
 */
export function useVerifySourceOnChain() {
  const queryClient = useQueryClient()

  // onConfirmed 콜백은 verify() 의 인자를 볼 수 없다. useMintMaterialOnChain 이
  // link() 에 materialId 를 직접 넘기는 것과 달리, 이 훅은 서버 호출 자체를
  // useBlockchainTransaction 안에 두므로 대상 id 를 ref 로 건네야 한다.
  const pendingMaterialId = useRef(0)

  const transaction = useBlockchainTransaction({
    onConfirmed: async (receipt) => {
      await confirmSourceVerification(pendingMaterialId.current, {
        txHash: receipt.hash,
        blockNumber: receipt.blockNumber ?? undefined,
      })
      void queryClient.invalidateQueries({ queryKey: materialKeys.all })
    },
  })

  const verify = useCallback(
    ({ materialId, tokenId, evidenceHash }: VerifySourceOnChainParams) => {
      pendingMaterialId.current = materialId
      return transaction.execute(() => verifySource(tokenId, evidenceHash))
    },
    [transaction],
  )

  return { ...transaction, verify }
}
