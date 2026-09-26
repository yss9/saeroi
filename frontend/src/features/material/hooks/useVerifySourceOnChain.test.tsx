import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ContractTransactionReceipt, ContractTransactionResponse } from 'ethers'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { materialKeys } from '../api/materialKeys.ts'
import { useVerifySourceOnChain } from './useVerifySourceOnChain.ts'

// useOnChainLink.test.tsx 와 같은 방식이다 — useBlockchainTransaction 은 실제로 돌리고,
// 그 아래(컨트랙트 호출·서버 알림)만 모킹한다. 그래야 상태 전이(SIGNING→PENDING→CONFIRMED)가
// 실제 훅 조합으로 검증된다.
const mocks = vi.hoisted(() => ({
  verifySource: vi.fn(),
  confirmSourceVerification: vi.fn(),
}))

vi.mock('../services/materialContractService.ts', () => ({
  verifySource: mocks.verifySource,
}))

vi.mock('../../../services/blockchain/chainLinkApi.ts', () => ({
  confirmSourceVerification: mocks.confirmSourceVerification,
}))

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

function respond(receipt: Partial<ContractTransactionReceipt>) {
  const response = {
    hash: (receipt.hash as string) ?? '0xsubmitted',
    wait: vi.fn().mockResolvedValue(receipt as ContractTransactionReceipt),
  } as unknown as ContractTransactionResponse
  return response
}

describe('useVerifySourceOnChain', () => {
  it('확정되면 materialId 로 서버에 확정 결과를 알린다 (tokenId 가 아니다)', async () => {
    mocks.verifySource.mockReset().mockResolvedValue(
      respond({ hash: '0xconfirmed', blockNumber: 999, status: 1, logs: [] }),
    )
    mocks.confirmSourceVerification.mockReset().mockResolvedValue(undefined)

    const { result } = renderHook(() => useVerifySourceOnChain(), { wrapper })

    await act(async () => {
      await result.current.verify({ materialId: 12, tokenId: 7, evidenceHash: '0xhash' })
    })

    // 컨트랙트 호출은 tokenId 를 쓴다 — verifySource(tokenId, evidenceHash).
    expect(mocks.verifySource).toHaveBeenCalledWith(7, '0xhash')
    // 서버 알림은 materialId 로 붙는다. 훅이 이 둘을 헷갈리면 엉뚱한 소재가 검증 처리된다.
    expect(mocks.confirmSourceVerification).toHaveBeenCalledWith(12, {
      txHash: '0xconfirmed',
      blockNumber: 999,
    })
    expect(result.current.status).toBe('CONFIRMED')
  })

  it('서버 알림(confirmSourceVerification)이 실패하면 트랜잭션은 FAILED 로 끝난다', async () => {
    mocks.verifySource.mockReset().mockResolvedValue(
      respond({ hash: '0xconfirmed2', blockNumber: 111, status: 1, logs: [] }),
    )
    mocks.confirmSourceVerification.mockReset().mockRejectedValue(new Error('server down'))

    const { result } = renderHook(() => useVerifySourceOnChain(), { wrapper })

    await act(async () => {
      await result.current.verify({ materialId: 3, tokenId: 1, evidenceHash: '0xhash' })
    })

    // onConfirmed 는 execute() 의 try 블록 안에서 await 된다 — 서버 알림이 던지면
    // 체인 확정 자체는 성공했더라도 훅은 FAILED 로 떨어진다. 화면은 이 상태를 보고
    // "체인에는 남았지만 서버 기록에 실패했다" 는 사용자 안내로 이어간다.
    expect(result.current.status).toBe('FAILED')
  })

  it('연달아 다른 소재를 검증하면 각각의 materialId 로 정확히 알린다', async () => {
    mocks.confirmSourceVerification.mockReset().mockResolvedValue(undefined)
    mocks.verifySource
      .mockReset()
      .mockResolvedValueOnce(respond({ hash: '0xfirst', blockNumber: 1, status: 1, logs: [] }))
      .mockResolvedValueOnce(respond({ hash: '0xsecond', blockNumber: 2, status: 1, logs: [] }))

    const { result } = renderHook(() => useVerifySourceOnChain(), { wrapper })

    await act(async () => {
      await result.current.verify({ materialId: 100, tokenId: 10, evidenceHash: '0xa' })
    })
    await act(async () => {
      await result.current.verify({ materialId: 200, tokenId: 20, evidenceHash: '0xb' })
    })

    // materialId 를 ref 하나에 담아 두는 구현이라, 두 번째 verify() 호출이 첫 번째 확정
    // 콜백에 쓰일 값을 덮어써도 되는지가 이 테스트의 핵심이다 — 순차 호출이라면 안전하다.
    expect(mocks.confirmSourceVerification).toHaveBeenNthCalledWith(1, 100, {
      txHash: '0xfirst',
      blockNumber: 1,
    })
    expect(mocks.confirmSourceVerification).toHaveBeenNthCalledWith(2, 200, {
      txHash: '0xsecond',
      blockNumber: 2,
    })
  })

  it('materialKeys.all 캐시를 무효화한다', async () => {
    mocks.verifySource.mockReset().mockResolvedValue(
      respond({ hash: '0xconfirmed3', blockNumber: 5, status: 1, logs: [] }),
    )
    mocks.confirmSourceVerification.mockReset().mockResolvedValue(undefined)

    const client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const invalidateSpy = vi.spyOn(client, 'invalidateQueries')

    const { result } = renderHook(() => useVerifySourceOnChain(), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    })

    await act(async () => {
      await result.current.verify({ materialId: 55, tokenId: 6, evidenceHash: '0xc' })
    })

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: materialKeys.all }),
    )
  })
})
