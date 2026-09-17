import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getWriteContract } from '../../../services/blockchain/contractFactory.ts'
import type { PredictionMarket } from '../types/prediction.types.ts'
import { approveAndPlacePredictionBet, claimPrediction } from './predictionContractService.ts'

vi.mock('../../../config/contracts.ts', () => ({
  contracts: { predictionMarket: '0x79Db90DE712AD9b647f20CA55aE99f59BE5B5C68' },
}))
vi.mock('../../../services/blockchain/contractFactory.ts', () => ({
  getReadContract: vi.fn(), getWriteContract: vi.fn(),
}))

const approveWait = vi.fn().mockResolvedValue({})
const betWait = vi.fn().mockResolvedValue({})
const claimWait = vi.fn().mockResolvedValue({})
const point = { approve: vi.fn().mockResolvedValue({ hash: '0xapprove', wait: approveWait }) }
const prediction = {
  placeBet: vi.fn().mockResolvedValue({ hash: '0xbet', wait: betWait }),
  claim: vi.fn().mockResolvedValue({ hash: '0xclaim', wait: claimWait }),
}
const market = { id: 1, chainMarketId: 9 } as PredictionMarket

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getWriteContract).mockImplementation(async (name) =>
    (name === 'predictionPoint' ? point : prediction) as never)
})

describe('predictionContractService', () => {
  it('approve가 확정된 뒤 같은 금액으로 placeBet을 전송한다', async () => {
    await expect(approveAndPlacePredictionBet(market, 2, '12.5')).resolves.toEqual({
      transactionHash: '0xbet', stakeAmount: '12500000000000000000',
    })
    expect(point.approve).toHaveBeenCalledWith('0x79Db90DE712AD9b647f20CA55aE99f59BE5B5C68', 12500000000000000000n)
    expect(approveWait).toHaveBeenCalledBefore(prediction.placeBet)
    expect(prediction.placeBet).toHaveBeenCalledWith(9, 2, 12500000000000000000n)
    expect(betWait).toHaveBeenCalled()
  })

  it('claim 영수증이 확정된 뒤 해시를 반환한다', async () => {
    await expect(claimPrediction(market)).resolves.toBe('0xclaim')
    expect(prediction.claim).toHaveBeenCalledWith(9)
    expect(claimWait).toHaveBeenCalled()
  })
})
