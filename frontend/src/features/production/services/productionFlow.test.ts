import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProductionBatch } from '../types/production.types.ts'

const api = vi.hoisted(() => ({
  confirmProductionBatch: vi.fn(),
  getProductionItems: vi.fn(),
  linkChainBatch: vi.fn(),
  getProductionManifest: vi.fn(),
  linkChainPlan: vi.fn(),
  linkChainConsume: vi.fn(),
  linkChainItems: vi.fn(),
}))
const chain = vi.hoisted(() => ({
  createBatch: vi.fn(),
  submitPlanAndLock: vi.fn(),
  confirmProduction: vi.fn(),
  linkProductItems: vi.fn(),
  readState: vi.fn(),
  readBatchId: vi.fn(),
}))

vi.mock('../api/productionBatchApi.ts', () => ({
  confirmProductionBatch: api.confirmProductionBatch,
  getProductionItems: api.getProductionItems,
}))
vi.mock('../api/productionChainApi.ts', () => ({
  linkChainBatch: api.linkChainBatch,
  getProductionManifest: api.getProductionManifest,
  linkChainPlan: api.linkChainPlan,
  linkChainConsume: api.linkChainConsume,
  linkChainItems: api.linkChainItems,
}))
vi.mock('./productionContractService.ts', () => ({
  createBatch: chain.createBatch,
  submitPlanAndLock: chain.submitPlanAndLock,
  confirmProduction: chain.confirmProduction,
  linkProductItems: chain.linkProductItems,
}))
const wallet = vi.hoisted(() => ({ getWalletAddress: vi.fn() }))
vi.mock('../../wallet/services/walletService.ts', () => ({ getWalletAddress: wallet.getWalletAddress }))
vi.mock('./productionChainState.ts', () => ({
  readProductionChainState: chain.readState,
  readCreatedBatchId: chain.readBatchId,
  lockedMaterialsFrom: () => [{ productionInputId: 1, chainLockId: 9 }],
}))

import { runProductionFlow } from './productionFlow.ts'

function batch(overrides: Partial<ProductionBatch> = {}): ProductionBatch {
  return {
    productionBatchId: 3, chainBatchId: null, status: 'DRAFT', producedQuantity: 2,
    producerWallet: '0xbrand', inputs: [],
    ...overrides,
  } as ProductionBatch
}

function tx(hash: string) {
  return { hash, wait: () => Promise.resolve({ status: 1, hash, blockNumber: 10, logs: [] }) }
}

/*
  생산 확정은 서버 확정 한 번과 체인 서명 네 번이다. 컨트랙트가 네 함수로 받으므로 서명 수는
  줄일 수 없지만, 사용자가 다섯 번 누를 이유는 없다 — 한 번 누르면 차례로 진행한다.
  중간에 끊겨도 다시 누르면 남은 단계부터 이어간다: 각 단계는 체인에게 먼저 묻는다.
*/
describe('runProductionFlow', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.confirmProductionBatch.mockResolvedValue(batch({ status: 'CONFIRMED' }))
    api.getProductionItems.mockResolvedValue([
      { productItemId: 21, serialNumber: 2 }, { productItemId: 20, serialNumber: 1 },
    ])
    api.getProductionManifest.mockResolvedValue({
      manifest: { inputs: [{ materialCode: 'M1', materialTokenId: 5, inputQuantity: 100, productUsedQuantity: 100, reusableScrapQuantity: 0, wasteQuantity: 0, scrapMetadataUri: '', scrapEvidenceHash: '0x0' }] },
      evidenceHash: '0xplan',
    })
    chain.createBatch.mockResolvedValue(tx('0xcreate'))
    chain.submitPlanAndLock.mockResolvedValue(tx('0xplan'))
    chain.confirmProduction.mockResolvedValue(tx('0xconsume'))
    chain.linkProductItems.mockResolvedValue(tx('0xlink'))
    chain.readBatchId.mockReturnValue(77)
    wallet.getWalletAddress.mockResolvedValue('0xBRAND')
  })

  it('처음부터 끝까지 한 번에 진행한다', async () => {
    chain.readState.mockResolvedValue({ status: 'DRAFT', linkedItemCount: 0 })
    const steps: string[] = []

    await runProductionFlow(batch(), step => steps.push(step))

    expect(steps).toEqual(['CONFIRM', 'CREATE_BATCH', 'PLAN', 'CONSUME', 'LINK_ITEMS'])
    expect(api.linkChainBatch).toHaveBeenCalledWith(3, expect.objectContaining({ chainBatchId: 77, txHash: '0xcreate' }))
    expect(chain.submitPlanAndLock).toHaveBeenCalledWith(77, '0xplan', expect.anything())
    expect(api.linkChainPlan).toHaveBeenCalledWith(3, expect.objectContaining({ txHash: '0xplan', lockedMaterials: [{ productionInputId: 1, chainLockId: 9 }] }))
    expect(chain.confirmProduction).toHaveBeenCalledWith(77)
    expect(api.linkChainConsume).toHaveBeenCalledWith(3, expect.objectContaining({ txHash: '0xconsume' }))
    // 일련번호 순으로 연결한다.
    expect(chain.linkProductItems).toHaveBeenCalledWith(77, [20, 21])
    expect(api.linkChainItems).toHaveBeenCalledWith(3, expect.objectContaining({ txHash: '0xlink' }))
  })

  it('확정과 배치 등록이 끝난 배치는 체인이 멈춘 곳부터 이어간다', async () => {
    chain.readState.mockResolvedValue({ status: 'SIGNING', linkedItemCount: 0 })
    const steps: string[] = []

    await runProductionFlow(batch({ status: 'CONFIRMED', chainBatchId: 77 }), step => steps.push(step))

    expect(steps).toEqual(['CONSUME', 'LINK_ITEMS'])
    expect(api.confirmProductionBatch).not.toHaveBeenCalled()
    expect(chain.createBatch).not.toHaveBeenCalled()
    expect(chain.submitPlanAndLock).not.toHaveBeenCalled()
  })

  it('모두 끝난 배치는 아무것도 서명하지 않는다', async () => {
    chain.readState.mockResolvedValue({ status: 'CONFIRMED', linkedItemCount: 2 })

    await runProductionFlow(batch({ status: 'CONFIRMED', chainBatchId: 77 }), () => {})

    expect(chain.confirmProduction).not.toHaveBeenCalled()
    expect(chain.linkProductItems).not.toHaveBeenCalled()
  })

  // 지갑에서 취소하면 그 자리에서 멈춘다 — 다음 단계로 넘어가 엉뚱한 서명을 요청하지 않는다.
  it('서명을 취소하면 그 단계에서 멈춘다', async () => {
    chain.readState.mockResolvedValue({ status: 'DRAFT', linkedItemCount: 0 })
    chain.submitPlanAndLock.mockRejectedValue(Object.assign(new Error('user rejected action'), { code: 'ACTION_REJECTED' }))

    await expect(runProductionFlow(batch({ status: 'CONFIRMED', chainBatchId: 77 }), () => {})).rejects.toThrow('user rejected action')

    expect(api.linkChainPlan).not.toHaveBeenCalled()
    expect(chain.confirmProduction).not.toHaveBeenCalled()
  })

  // 개수가 어긋나면 컨트랙트가 되돌린다 — 서명을 요청하기 전에 멈춘다.
  it('제품 개체 수가 생산 수량과 다르면 연결 서명을 요청하지 않는다', async () => {
    chain.readState.mockResolvedValue({ status: 'CONFIRMED', linkedItemCount: 0 })
    api.getProductionItems.mockResolvedValue([{ productItemId: 20, serialNumber: 1 }])

    await expect(runProductionFlow(batch({ status: 'CONFIRMED', chainBatchId: 77 }), () => {}))
      .rejects.toThrow('등록할 생산 제품 수가 맞지 않습니다 (1 / 2개).')
    expect(chain.linkProductItems).not.toHaveBeenCalled()
  })

  // 배치를 연 지갑이 아니면 서명하지 않는다 — 다른 지갑으로 남긴 기록은 배치와 어긋나고 되돌릴 수 없다.
  it('배치를 연 지갑이 아니면 서버 확정까지만 하고 서명 전에 멈춘다', async () => {
    wallet.getWalletAddress.mockResolvedValue('0x9999999999999999999999999999999999999999')

    await expect(runProductionFlow(batch(), () => {})).rejects.toThrow('이 생산을 시작한 지갑(0xbrand)으로 연결한 뒤')

    expect(api.confirmProductionBatch).toHaveBeenCalled()
    expect(chain.createBatch).not.toHaveBeenCalled()
  })
})
