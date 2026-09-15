// 생산 온체인 2~4단계.
//
// 1단계(`useCreateChainBatch`)와 달리 **번호를 채번하지 않는다.** 세 단계 모두 이미 있는
// 배치의 상태를 앞으로 밀 뿐이라 `useOnChainLink` 의 모양(이벤트에서 id 를 꺼내 알린다)에
// 맞지 않는다. 그래서 트랜잭션 훅을 직접 쓰고, 보고할 값만 단계마다 다르게 만든다.
//
//   2) submitPlanAndLock  투입 잠금 + 계획 해시 → 잠금 번호들을 보고
//   3) confirmProduction  소재 소각 + 자투리 발행 → 트랜잭션만 보고
//   4) linkProductItems   개체 연결 → 트랜잭션만 보고
//
// **순서는 컨트랙트가 강제한다.** 앞 단계를 건너뛰면 revert 하고, 서버도 보고 순서를
// 검사한다. 그래서 화면은 "지금 할 수 있는 단계" 만 열어 주면 된다.

import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useBlockchainTransaction } from '../../../hooks/useBlockchainTransaction.ts'
import { readEvents } from '../../../services/blockchain/contractFactory.ts'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import {
  getProductionManifest,
  linkChainConsume,
  linkChainItems,
  linkChainPlan,
  type LockedMaterialPayload,
} from '../api/productionChainApi.ts'
import { productionKeys } from '../api/productionKeys.ts'
import type { ProductionBatch, ProductionItem } from '../types/production.types.ts'

/**
 * 한 단계가 화면에 내주는 것. 세 단계가 같은 모양이라 패널이 한 컴포넌트로 그린다.
 */
export interface ChainStepAction {
  run: () => Promise<void>
  status: ReturnType<typeof useBlockchainTransaction>['status']
  hash: string | null
  error: unknown
  isBusy: boolean
  isRejected: boolean
}

/** 서명이 끝나고 서버 보고까지 마쳤을 때 비울 캐시. */
function useRefreshAfterChain(batchId: number) {
  const queryClient = useQueryClient()

  return useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: productionKeys.detail(batchId) })
    void queryClient.invalidateQueries({ queryKey: ['chain', 'production-batch'] })
    // 3단계는 소재를 태우고 자투리를 발행한다 — 보유 목록과 소재 상세가 함께 낡는다.
    void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
    void queryClient.invalidateQueries({ queryKey: materialKeys.all })
  }, [batchId, queryClient])
}

/**
 * 2단계 — 투입을 잠그고 계획을 기록한다.
 *
 * **manifest 를 서명 직전에 받는다.** 화면을 열 때 받아 두면 그 사이 투입이 바뀌었을 때
 * 옛 계획에 서명하게 된다 — 체인에 남은 계획과 실제 투입이 어긋나고, 그 어긋남은 나중에
 * 대조할 때만 드러난다.
 */
export function useSubmitProductionPlan(batch: ProductionBatch): ChainStepAction {
  const refresh = useRefreshAfterChain(batch.productionBatchId)

  const transaction = useBlockchainTransaction()

  const run = useCallback(async () => {
    const { manifest, evidenceHash } = await getProductionManifest(batch.productionBatchId)

    const receipt = await transaction.execute(async () => {
      const { submitPlanAndLock } = await import('../services/productionContractService.ts')
      return submitPlanAndLock(batch.chainBatchId as number, evidenceHash, {
        tokenIds: manifest.inputs.map((input) => BigInt(input.materialTokenId)),
        inputAmounts: manifest.inputs.map((input) => BigInt(input.inputQuantity)),
        productUsed: manifest.inputs.map((input) => BigInt(input.productUsedQuantity)),
        scrap: manifest.inputs.map((input) => BigInt(input.reusableScrapQuantity)),
        waste: manifest.inputs.map((input) => BigInt(input.wasteQuantity)),
        scrapMetadataUris: manifest.inputs.map((input) => input.scrapMetadataUri),
        scrapEvidenceHashes: manifest.inputs.map((input) => input.scrapEvidenceHash),
      })
    })
    if (receipt === null) return

    await linkChainPlan(batch.productionBatchId, {
      txHash: receipt.hash,
      blockNumber: receipt.blockNumber,
      evidenceHash,
      lockedMaterials: toLockedMaterials(receipt, batch, manifest.inputs),
    })
    refresh()
  }, [batch, refresh, transaction])

  return toAction(transaction, run)
}

/**
 * 영수증의 잠금 번호를 투입에 짝지운다.
 *
 * **토큰 번호로 맞춘다.** 이벤트 순서가 투입 순서와 같을 것 같지만 그것에 기대지 않는다 —
 * 순서가 어긋나면 엉뚱한 투입에 남의 잠금 번호가 붙고, 그 상태로 3단계를 부르면 태우려던
 * 소재가 아닌 것이 타거나 트랜잭션이 통째로 실패한다.
 *
 * manifest 는 소재 코드를, 배치 투입은 `productionInputId` 를 갖고 있어 소재 코드로 잇는다.
 */
function toLockedMaterials(
  receipt: { logs: readonly { topics: readonly string[]; data: string }[] },
  batch: ProductionBatch,
  inputs: readonly { materialCode: string; materialTokenId: number }[],
): LockedMaterialPayload[] {
  const lockByToken = new Map<string, number>()
  for (const event of readEvents(receipt, 'materialToken', 'MaterialLockCreated')) {
    lockByToken.set(String(event.tokenId), Number(event.lockId))
  }

  const inputIdByCode = new Map(
    batch.inputs.map((input) => [input.materialCode, input.productionInputId]),
  )

  const locked: LockedMaterialPayload[] = []
  for (const input of inputs) {
    const chainLockId = lockByToken.get(String(input.materialTokenId))
    const productionInputId = inputIdByCode.get(input.materialCode)
    // 짝을 못 찾은 투입은 보내지 않는다. 서버가 못 받은 잠금은 3단계에서 드러나지만,
    // 잘못된 짝을 보내면 엉뚱한 소재가 타고 그건 되돌릴 수 없다.
    if (chainLockId === undefined || productionInputId === undefined) continue
    locked.push({ productionInputId, chainLockId })
  }
  return locked
}

/**
 * 3단계 — 생산을 확정한다.
 *
 * **이 서비스에서 가장 무거운 트랜잭션이다.** 잠긴 소재가 실제로 소각되고, 자투리가 있으면
 * 새 소재 토큰이 발행된다. 되돌릴 수 없다.
 */
export function useConfirmProductionOnChain(batch: ProductionBatch): ChainStepAction {
  const refresh = useRefreshAfterChain(batch.productionBatchId)
  const transaction = useBlockchainTransaction()

  const run = useCallback(async () => {
    const receipt = await transaction.execute(async () => {
      const { confirmProduction } = await import('../services/productionContractService.ts')
      return confirmProduction(batch.chainBatchId as number)
    })
    if (receipt === null) return

    await linkChainConsume(batch.productionBatchId, {
      txHash: receipt.hash,
      blockNumber: receipt.blockNumber,
    })
    refresh()
  }, [batch, refresh, transaction])

  return toAction(transaction, run)
}

/**
 * 4단계 — 개체를 배치에 연결한다.
 *
 * **개수가 선언한 생산 수량과 정확히 같아야 한다.** 다르면 컨트랙트가 revert 하므로, 개체
 * 목록을 다 받지 못한 상태로는 부르지 않는다.
 *
 * 일련번호 순으로 보낸다 — 서버가 그 순서를 기대한다.
 */
export function useLinkProductionItems(
  batch: ProductionBatch,
  items: readonly ProductionItem[],
): ChainStepAction {
  const refresh = useRefreshAfterChain(batch.productionBatchId)
  const transaction = useBlockchainTransaction()

  const run = useCallback(async () => {
    const productItemIds = [...items]
      .sort((left, right) => left.serialNumber - right.serialNumber)
      .map((item) => item.productItemId)

    const receipt = await transaction.execute(async () => {
      const { linkProductItems } = await import('../services/productionContractService.ts')
      return linkProductItems(batch.chainBatchId as number, productItemIds)
    })
    if (receipt === null) return

    await linkChainItems(batch.productionBatchId, {
      txHash: receipt.hash,
      blockNumber: receipt.blockNumber,
    })
    refresh()
  }, [batch, items, refresh, transaction])

  return toAction(transaction, run)
}

function toAction(
  transaction: ReturnType<typeof useBlockchainTransaction>,
  run: () => Promise<void>,
): ChainStepAction {
  return {
    run,
    status: transaction.status,
    hash: transaction.hash,
    error: transaction.error,
    isBusy: transaction.isBusy,
    isRejected: transaction.isRejected,
  }
}
