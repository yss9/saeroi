import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { linkMaterialOnChain } from '../../../services/blockchain/chainLinkApi.ts'
import { useOnChainLink } from '../../../hooks/useOnChainLink.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../api/materialKeys.ts'
import { mintMaterial } from '../services/materialContractService.ts'
import type { MaterialRootOrigin } from '../types/material.types.ts'

export interface MintOnChainParams {
  /** 우리 DB 의 소재 id. 서버에 결과를 붙일 대상이다. */
  materialId: number
  /** 발행 대상 지갑. 등록자 본인이다 — 남에게 발행하면 보유 행과 어긋난다. */
  to: string
  /** gram 정수 */
  amount: number
  rootOrigin: MaterialRootOrigin
  metadataUri: string
  /** `0x` + 64 hex. 컨트랙트가 0 을 거절한다. */
  evidenceHash: string
}

/**
 * 등록된 소재를 온체인에 발행한다.
 *
 * <b>등록과 발행이 나뉘어 있다.</b> 등록은 서버 트랜잭션 하나로 끝나지만 발행은 사용자가
 * 지갑 팝업을 확인하고 블록이 채굴되기를 기다려야 한다. 그 사이 이탈하면 소재는
 * `token_id` 가 NULL 인 채로 남고, 그것이 "등록됐지만 아직 온체인에 없음" 상태다.
 *
 * 그래서 이 훅은 <b>등록 직후에도, 나중에 다시도</b> 부를 수 있어야 한다.
 */
export function useMintMaterialOnChain() {
  const queryClient = useQueryClient()

  const link = useOnChainLink({
    contract: 'materialToken',
    eventName: 'MaterialMinted',
    argName: 'tokenId',
    link: linkMaterialOnChain,
    onLinked: () => {
      void queryClient.invalidateQueries({ queryKey: materialKeys.all })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
    },
  })

  const mint = useCallback(
    ({ materialId, to, amount, rootOrigin, metadataUri, evidenceHash }: MintOnChainParams) =>
      link.run(materialId, () =>
        mintMaterial({ to, amount, rootOrigin, metadataUri, evidenceHash }),
      ),
    [link],
  )

  return { ...link, mint }
}
