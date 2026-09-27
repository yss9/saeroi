import { isContractConfigured } from '../../../config/contracts.ts'
import { getReadContract, readEventArg, readEvents } from '../../../services/blockchain/contractFactory.ts'
import type { LockedMaterialPayload } from '../api/productionChainApi.ts'
import type { ProductionBatch } from '../types/production.types.ts'

/**
 * 컨트랙트가 말하는 배치 상태. `ProductionRegistry.Status` 와 같은 순서다.
 *
 *   NONE      아직 만들어지지 않음
 *   DRAFT     createBatch 까지 (1단계)
 *   SIGNING   submitPlanAndLock 까지 (2단계)
 *   CONFIRMED confirmProduction 까지 (3단계)
 */
export type ChainBatchStatus = 'NONE' | 'DRAFT' | 'SIGNING' | 'CONFIRMED' | 'FAILED' | 'CANCELED'

const STATUS: readonly ChainBatchStatus[] = ['NONE', 'DRAFT', 'SIGNING', 'CONFIRMED', 'FAILED', 'CANCELED']

export interface ProductionChainState {
  status: ChainBatchStatus
  /** 연결된 제품 개체 수. 0 이면 4단계가 아직이다 */
  linkedItemCount: number
}

/**
 * 온체인 진행 상태를 체인에 직접 묻는다.
 *
 * 서버 응답으로는 3·4단계가 끝났는지 알 수 없고, 서명은 됐는데 서버 보고가 실패하면 서버 기준으로는
 * 영원히 "아직" 이다. 그 상태에서 다시 서명하면 컨트랙트가 거절한다 — 다음에 할 일은 체인이 정한다.
 */
export async function readProductionChainState(chainBatchId: number): Promise<ProductionChainState> {
  if (!isContractConfigured('productionRegistry')) throw new Error('생산 이력 기록 기능을 사용할 수 없습니다.')
  const contract = getReadContract('productionRegistry')
  const [batch, items] = await Promise.all([
    contract.getBatch(chainBatchId) as Promise<{ status: bigint }>,
    contract.getProductItems(chainBatchId) as Promise<unknown[]>,
  ])
  return { status: STATUS[Number(batch.status)] ?? 'NONE', linkedItemCount: items.length }
}

/** createBatch 영수증에서 채번된 배치 번호를 꺼낸다. 반환값은 영수증에 남지 않고 이벤트만 남는다. */
export function readCreatedBatchId(receipt: Parameters<typeof readEventArg>[0]): number | null {
  const id = readEventArg<bigint>(receipt, 'productionRegistry', 'ProductionBatchCreated', 'batchId')
  return id === null ? null : Number(id)
}

/**
 * 영수증의 잠금 번호를 투입에 짝지운다.
 *
 * **토큰 번호로 맞춘다.** 이벤트 순서가 투입 순서와 같을 것 같지만 그것에 기대지 않는다 — 순서가
 * 어긋나면 엉뚱한 투입에 남의 잠금 번호가 붙고, 그 상태로 3단계를 부르면 태우려던 소재가 아닌 것이
 * 타거나 트랜잭션이 통째로 실패한다. manifest 는 소재 코드를, 배치 투입은 productionInputId 를 갖고
 * 있어 소재 코드로 잇는다.
 */
export function lockedMaterialsFrom(
  receipt: { logs: readonly { topics: readonly string[]; data: string }[] },
  batch: ProductionBatch,
  inputs: readonly { materialCode: string; materialTokenId: number }[],
): LockedMaterialPayload[] {
  const lockByToken = new Map<string, number>()
  for (const event of readEvents(receipt, 'materialToken', 'MaterialLockCreated')) {
    lockByToken.set(String(event.tokenId), Number(event.lockId))
  }
  const inputIdByCode = new Map(batch.inputs.map((input) => [input.materialCode, input.productionInputId]))

  const locked: LockedMaterialPayload[] = []
  for (const input of inputs) {
    const chainLockId = lockByToken.get(String(input.materialTokenId))
    const productionInputId = inputIdByCode.get(input.materialCode)
    // 짝을 못 찾은 투입은 보내지 않는다. 잘못된 짝을 보내면 엉뚱한 소재가 타고 그건 되돌릴 수 없다.
    if (chainLockId === undefined || productionInputId === undefined) continue
    locked.push({ productionInputId, chainLockId })
  }
  return locked
}
