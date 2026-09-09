import { act, renderHook } from '@testing-library/react'
import type { ContractTransactionReceipt, ContractTransactionResponse } from 'ethers'
import { describe, expect, it, vi } from 'vitest'
import { useOnChainLink } from './useOnChainLink.ts'

vi.mock('../services/blockchain/contractFactory.ts', () => ({
  readEventArg: vi.fn(() => 77n),
}))

describe('useOnChainLink', () => {
  it('체인은 성공하고 서버 연결만 실패하면 새 트랜잭션 없이 연결만 재시도한다', async () => {
    const receipt = {
      hash: '0xconfirmed',
      blockNumber: 123,
      status: 1,
      logs: [],
    } as unknown as ContractTransactionReceipt
    const response = {
      hash: '0xsubmitted',
      wait: vi.fn().mockResolvedValue(receipt),
    } as unknown as ContractTransactionResponse
    const send = vi.fn().mockResolvedValue(response)
    const link = vi
      .fn<(targetId: number, payload: { onChainId: number; txHash: string }) => Promise<void>>()
      .mockRejectedValueOnce(new Error('temporary API failure'))
      .mockResolvedValueOnce(undefined)

    const { result } = renderHook(() =>
      useOnChainLink({
        contract: 'materialToken',
        eventName: 'MaterialMinted',
        argName: 'tokenId',
        link,
      }),
    )

    let first = true
    await act(async () => {
      first = await result.current.run(5, send)
    })

    expect(first).toBe(false)
    expect(send).toHaveBeenCalledTimes(1)
    expect(link).toHaveBeenCalledWith(5, {
      onChainId: 77,
      txHash: '0xconfirmed',
      blockNumber: 123,
    })

    let second = false
    await act(async () => {
      second = await result.current.run(5, send)
    })

    expect(second).toBe(true)
    expect(send).toHaveBeenCalledTimes(1)
    expect(link).toHaveBeenCalledTimes(2)
    expect(result.current.status).toBe('LINKED')
  })
})
