import type { ContractTransactionReceipt, ContractTransactionResponse } from 'ethers'
import { confirmProductionBatch, getProductionItems } from '../api/productionBatchApi.ts'
import {
  getProductionManifest,
  linkChainBatch,
  linkChainConsume,
  linkChainItems,
  linkChainPlan,
} from '../api/productionChainApi.ts'
import type { ProductionBatch } from '../types/production.types.ts'
import { getWalletAddress } from '../../wallet/services/walletService.ts'
import {
  lockedMaterialsFrom,
  readCreatedBatchId,
  readProductionChainState,
} from './productionChainState.ts'
import {
  confirmProduction,
  createBatch,
  linkProductItems,
  submitPlanAndLock,
} from './productionContractService.ts'
import { sendTrackedOperation } from '../../../services/blockchain/operationApi.ts'

export type ProductionFlowStep = 'CONFIRM' | 'CREATE_BATCH' | 'PLAN' | 'CONSUME' | 'LINK_ITEMS'

/** 진행 표시에 쓰는 단계 이름. 서명이 필요한 단계는 뒤의 넷이다. */
export const PRODUCTION_FLOW_STEPS: readonly { step: ProductionFlowStep; label: string }[] = [
  { step: 'CONFIRM', label: '생산 확정' },
  { step: 'CREATE_BATCH', label: '생산 이력 기록' },
  { step: 'PLAN', label: '생산에 사용할 소재 확정' },
  { step: 'CONSUME', label: '사용 소재 차감·자투리 등록' },
  { step: 'LINK_ITEMS', label: '생산 제품 등록' },
]

/** 진행 중인 단계보다 앞선 단계. 처음부터 진행하는 흐름이라 앞 단계는 끝난 것이다. */
export function stepsBefore(current: ProductionFlowStep | null): Set<ProductionFlowStep> {
  const done = new Set<ProductionFlowStep>()
  if (current === null) return done
  for (const { step } of PRODUCTION_FLOW_STEPS) {
    if (step === current) break
    done.add(step)
  }
  return done
}

/**
 * 생산 확정을 **한 번의 누름으로** 끝까지 진행한다.
 *
 * <h2>왜 필요한가</h2>
 *
 * 생산은 서버 확정 한 번과 체인 서명 네 번(createBatch → submitPlanAndLock → confirmProduction →
 * linkProductItems)이다. 컨트랙트가 네 함수로 받으므로 서명 수는 줄일 수 없지만, 사용자가 다섯 번
 * 따로 누를 이유는 없다 — 한 번 누르면 차례로 서명 창이 뜬다.
 *
 * <h2>끊겨도 이어간다</h2>
 *
 * 각 단계는 시작 전에 **체인에게 먼저 묻는다.** 이미 지난 단계는 건너뛰므로 서명을 취소하거나 창을
 * 닫은 뒤 다시 누르면 남은 단계부터 진행한다. 순서는 컨트랙트와 서버가 강제한다.
 *
 * 서명을 취소하면 그 자리에서 예외로 멈춘다 — 다음 단계로 넘어가 엉뚱한 서명을 요청하지 않는다.
 */
export async function runProductionFlow(
  batch: ProductionBatch,
  onStep: (step: ProductionFlowStep) => void,
): Promise<void> {
  let current = batch
  const tracked = (functionName: string, send: (operationId: string) => Promise<ContractTransactionResponse>) =>
    sendTrackedOperation({ contract: 'PRODUCTION_REGISTRY', functionName, referenceType: 'PRODUCTION', referenceId: current.productionBatchId }, send)

  if (current.status === 'DRAFT') {
    onStep('CONFIRM')
    current = await confirmProductionBatch(current.productionBatchId)
  }
  if (current.status !== 'CONFIRMED') {
    throw new Error('확정된 생산만 이력을 기록할 수 있습니다.')
  }

  // 배치를 연 지갑이 아니면 서명하지 않는다 — 다른 지갑으로 남긴 기록은 배치와 어긋나고 되돌릴 수 없다.
  // 아직 연결 전이면 서명 단계가 연결부터 묻는다.
  const signer = await getWalletAddress()
  if (signer && signer.toLowerCase() !== current.producerWallet.toLowerCase()) {
    throw new Error(`이 생산을 시작한 지갑(${current.producerWallet})으로 연결한 뒤 '남은 기록 이어서 진행'을 눌러 주세요.`)
  }

  let chainBatchId = current.chainBatchId
  if (chainBatchId === null) {
    onStep('CREATE_BATCH')
    const receipt = await confirmed(tracked('createBatch', (op) => createBatch(current.producedQuantity, op)))
    chainBatchId = readCreatedBatchId(receipt)
    if (chainBatchId === null) throw new Error('생산 이력 번호를 확인하지 못했습니다. 관리자에게 문의해 주세요.')
    await linkChainBatch(current.productionBatchId, {
      chainBatchId,
      txHash: receipt.hash,
      blockNumber: receipt.blockNumber,
    })
  }

  const state = await readProductionChainState(chainBatchId)

  if (state.status === 'DRAFT') {
    onStep('PLAN')
    // manifest 는 서명 직전에 받는다 — 미리 받아 두면 그 사이 바뀐 투입 대신 옛 계획에 서명하게 된다.
    const { manifest, evidenceHash } = await getProductionManifest(current.productionBatchId)
    const receipt = await confirmed(tracked('submitPlanAndLock', (op) => submitPlanAndLock(chainBatchId, evidenceHash, {
      tokenIds: manifest.inputs.map((input) => BigInt(input.materialTokenId)),
      inputAmounts: manifest.inputs.map((input) => BigInt(input.inputQuantity)),
      productUsed: manifest.inputs.map((input) => BigInt(input.productUsedQuantity)),
      scrap: manifest.inputs.map((input) => BigInt(input.reusableScrapQuantity)),
      waste: manifest.inputs.map((input) => BigInt(input.wasteQuantity)),
      scrapMetadataUris: manifest.inputs.map((input) => input.scrapMetadataUri),
      scrapEvidenceHashes: manifest.inputs.map((input) => input.scrapEvidenceHash),
    }, op)))
    await linkChainPlan(current.productionBatchId, {
      txHash: receipt.hash,
      blockNumber: receipt.blockNumber,
      evidenceHash,
      lockedMaterials: lockedMaterialsFrom(receipt, current, manifest.inputs),
    })
    state.status = 'SIGNING'
  }

  if (state.status === 'SIGNING') {
    onStep('CONSUME')
    const receipt = await confirmed(tracked('confirmProduction', (op) => confirmProduction(chainBatchId, op)))
    await linkChainConsume(current.productionBatchId, { txHash: receipt.hash, blockNumber: receipt.blockNumber })
    state.status = 'CONFIRMED'
  }

  if (state.status === 'CONFIRMED' && state.linkedItemCount === 0) {
    onStep('LINK_ITEMS')
    const items = await getProductionItems(current.productionBatchId)
    // 개수가 어긋나면 컨트랙트가 되돌린다. 서명까지 하고 실패하지 않게 먼저 멈춘다.
    if (items.length !== current.producedQuantity) {
      throw new Error(
        `등록할 생산 제품 수가 맞지 않습니다 (${items.length} / ${current.producedQuantity}개). 새로 고친 뒤 다시 시도해 주세요.`,
      )
    }
    const productItemIds = [...items]
      .sort((left, right) => left.serialNumber - right.serialNumber)
      .map((item) => item.productItemId)
    const receipt = await confirmed(tracked('linkProductItems', (op) => linkProductItems(chainBatchId, productItemIds, op)))
    await linkChainItems(current.productionBatchId, { txHash: receipt.hash, blockNumber: receipt.blockNumber })
  }
}

async function confirmed(response: Promise<ContractTransactionResponse>): Promise<ContractTransactionReceipt> {
  const tx = await response
  const receipt = await tx.wait()
  if (!receipt || receipt.status !== 1) throw new Error('블록체인 기록이 확정되지 않았습니다. 잠시 후 다시 시도해 주세요.')
  return receipt
}
