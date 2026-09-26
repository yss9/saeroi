import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  chainPreparation: vi.fn(),
  linkChain: vi.fn(),
}))
const chain = vi.hoisted(() => ({
  createProcessingJob: vi.fn(),
  getProcessingBrandLockId: vi.fn(),
  readProcessingEventValues: vi.fn(),
}))
const wallet = vi.hoisted(() => ({
  waitForTransaction: vi.fn(),
}))

vi.mock('../../processing/api/processingJobApi.ts', () => ({ processingJobApi: api }))
vi.mock('../../processing/services/processingContractService.ts', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  ...chain,
}))
vi.mock('../../wallet/services/walletService.ts', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  getBrowserProvider: () => ({ waitForTransaction: wallet.waitForTransaction }),
}))

import { registerProcessingChainJob } from './registerProcessingChainJob.ts'

const JOB_ID = 12
const KEY = `processing-chain-create:${JOB_ID}`

/**
 * 가공 작업을 체인에 **두 번 만들지 않는다.**
 *
 * 등록 서명이 체인에 들어간 뒤 서버 연결(linkChain)이 실패하는 창이 있다 — 네트워크가
 * 끊기거나 로그인이 만료된 경우다. 그때 다시 누르면 서버는 아직 체인 작업을 모르므로 한 번
 * 더 만들게 되고, 앞의 작업은 소재를 잠근 채 버려진다. 그래서 보낸 거래를 기억해 두었다가
 * 다시 누르면 그 거래에서 이어 간다.
 */
describe('registerProcessingChainJob', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    api.chainPreparation.mockResolvedValue({
      chainJobId: null,
      processorWallet: '0xprocessor',
      inputTokenId: 7,
      inputQuantity: 12_500,
      processTypes: ['CLEANING'],
    })
    api.linkChain.mockResolvedValue({})
    chain.readProcessingEventValues.mockReturnValue([31n])
    chain.getProcessingBrandLockId.mockResolvedValue(4)
  })

  it('처음이면 만들고, 확정을 기다리기 전에 거래를 기억했다가 연결 뒤 지운다', async () => {
    let rememberedWhileWaiting: string | null = null
    chain.createProcessingJob.mockResolvedValue({
      hash: '0xnew',
      wait: async () => {
        rememberedWhileWaiting = window.localStorage.getItem(KEY)
        return { status: 1, hash: '0xnew', blockNumber: 88, logs: [] }
      },
    })

    await expect(registerProcessingChainJob(JOB_ID)).resolves.toBe('0xnew')

    expect(rememberedWhileWaiting).toBe('0xnew')
    expect(api.linkChain).toHaveBeenCalledWith(JOB_ID, {
      onChainId: 31,
      chainLockId: 4,
      txHash: '0xnew',
      blockNumber: 88,
    })
    expect(window.localStorage.getItem(KEY)).toBeNull()
  })

  it('앞서 보낸 거래가 체인에 들어갔으면 새로 만들지 않고 그 작업을 연결한다', async () => {
    window.localStorage.setItem(KEY, '0xearlier')
    wallet.waitForTransaction.mockResolvedValue({ status: 1, hash: '0xearlier', blockNumber: 77, logs: [] })

    await expect(registerProcessingChainJob(JOB_ID)).resolves.toBe('0xearlier')

    expect(chain.createProcessingJob).not.toHaveBeenCalled()
    expect(api.linkChain).toHaveBeenCalledWith(JOB_ID, expect.objectContaining({ onChainId: 31, txHash: '0xearlier' }))
    expect(window.localStorage.getItem(KEY)).toBeNull()
  })

  it('앞서 보낸 거래가 아직 확정되지 않았으면 멈춘다 — 기다리지 않고 만들면 작업이 두 개가 된다', async () => {
    window.localStorage.setItem(KEY, '0xpending')
    wallet.waitForTransaction.mockResolvedValue(null)

    await expect(registerProcessingChainJob(JOB_ID)).rejects.toThrow('아직 확인되지 않았습니다')

    expect(chain.createProcessingJob).not.toHaveBeenCalled()
    expect(api.linkChain).not.toHaveBeenCalled()
    expect(window.localStorage.getItem(KEY)).toBe('0xpending')
  })

  it('앞서 보낸 거래가 체인에서 실패했으면 잊고 새로 만든다', async () => {
    window.localStorage.setItem(KEY, '0xreverted')
    wallet.waitForTransaction.mockResolvedValue({ status: 0, hash: '0xreverted', blockNumber: 70, logs: [] })
    chain.createProcessingJob.mockResolvedValue({
      hash: '0xretry',
      wait: async () => ({ status: 1, hash: '0xretry', blockNumber: 90, logs: [] }),
    })

    await expect(registerProcessingChainJob(JOB_ID)).resolves.toBe('0xretry')

    expect(chain.createProcessingJob).toHaveBeenCalledTimes(1)
    expect(window.localStorage.getItem(KEY)).toBeNull()
  })

  it('서버가 이미 체인 작업을 알면 서명하지 않고, 남은 기억도 지운다', async () => {
    window.localStorage.setItem(KEY, '0xearlier')
    api.chainPreparation.mockResolvedValue({ chainJobId: 31 })

    await expect(registerProcessingChainJob(JOB_ID)).rejects.toMatchObject({ code: 'CHAIN_ALREADY_DONE' })

    expect(chain.createProcessingJob).not.toHaveBeenCalled()
    expect(window.localStorage.getItem(KEY)).toBeNull()
  })
})
