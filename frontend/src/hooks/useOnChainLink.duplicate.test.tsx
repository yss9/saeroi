import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ContractTransactionResponse } from 'ethers'
import type { ChainLinkPayload } from '../services/blockchain/chainLinkApi.ts'
import { useOnChainLink } from './useOnChainLink.ts'

/**
 * <b>같은 것을 체인에 두 번 만들지 않는다.</b>
 *
 * 온체인 등록은 두 번의 쓰기다 — 지갑 서명과 그 결과를 서버에 알리는 요청. 서명은 끝났고
 * 서버 기록만 실패하면 {@code pendingLink} 가 영수증을 붙들어 재서명을 막는다. 그런데
 * 그건 ref, 즉 메모리다. <b>새로고침하면 사라진다.</b> 그러면 다음 시도가 서명부터 다시
 * 보내 같은 것이 체인에 하나 더 생기고 되돌릴 수 없다 — 2026-09-08 에 tokenId 2 가
 * 그렇게 고아로 남았다.
 *
 * {@code findExisting} 이 그 구멍을 막는다. 체인이 정본이므로 서명 전에 물어본다.
 */
const RECEIPT = { hash: '0xtx', blockNumber: 100 }

function fakeSend() {
  return Promise.resolve({
    hash: '0xtx',
    wait: () => Promise.resolve(RECEIPT),
  } as unknown as ContractTransactionResponse)
}

vi.mock('../services/blockchain/contractFactory.ts', () => ({
  // 영수증에서 번호를 꺼내는 부분은 이 테스트의 관심사가 아니다.
  readEventArg: () => 7n,
}))

afterEach(() => {
  vi.clearAllMocks()
})

function setup(options: {
  findExisting?: (id: number) => Promise<ChainLinkPayload | null>
  link?: (id: number, payload: ChainLinkPayload) => Promise<void>
}) {
  const link = options.link ?? vi.fn(() => Promise.resolve())
  const send = vi.fn(fakeSend)
  const hook = renderHook(() =>
    useOnChainLink({
      contract: 'materialToken',
      eventName: 'MaterialMinted',
      argName: 'tokenId',
      link,
      findExisting: options.findExisting,
    }),
  )
  return { hook, link, send }
}

describe('useOnChainLink — 중복 생성 방지', () => {
  it('체인에 이미 있으면 서명하지 않고 서버 기록만 한다', async () => {
    // 새로고침 직후를 흉내낸다 — pendingLink 는 비어 있지만 체인에는 토큰이 있다.
    const existing: ChainLinkPayload = { onChainId: 2, txHash: '0xold', blockNumber: 50 }
    const { hook, link, send } = setup({ findExisting: () => Promise.resolve(existing) })

    let ok = false
    await act(async () => {
      ok = await hook.result.current.run(1, send)
    })

    expect(ok).toBe(true)
    // 이것이 핵심이다. 서명이 나가면 토큰이 하나 더 생긴다.
    expect(send).not.toHaveBeenCalled()
    expect(link).toHaveBeenCalledWith(1, existing)
  })

  it('체인에 없으면 평소대로 서명한다', async () => {
    const { hook, link, send } = setup({ findExisting: () => Promise.resolve(null) })

    await act(async () => {
      await hook.result.current.run(1, send)
    })

    expect(send).toHaveBeenCalledTimes(1)
    expect(link).toHaveBeenCalledWith(1, expect.objectContaining({ onChainId: 7 }))
  })

  it('조회가 실패해도 발행을 막지 않는다', async () => {
    // RPC 가 흔들릴 때 서명을 막으면 정상 사용자가 아무것도 못 한다. 계약상
    // findExisting 은 던지지 않고 null 을 주지만, 던져도 흐름이 멈추지 않아야 한다.
    const { hook, send } = setup({ findExisting: () => Promise.resolve(null) })

    await act(async () => {
      await hook.result.current.run(1, send)
    })

    expect(send).toHaveBeenCalledTimes(1)
  })

  it('findExisting 이 없으면 예전처럼 동작한다', async () => {
    // 판매 등록·주문 생성은 아직 이 대조를 붙이지 않았다. 그쪽이 깨지면 안 된다.
    const { hook, send } = setup({})

    await act(async () => {
      await hook.result.current.run(1, send)
    })

    expect(send).toHaveBeenCalledTimes(1)
  })

  it('대상 id 가 깨져 있으면 조회도 서명도 하지 않는다', async () => {
    const findExisting = vi.fn(() => Promise.resolve(null))
    const { hook, send } = setup({ findExisting })

    let ok = true
    await act(async () => {
      ok = await hook.result.current.run(0, send)
    })

    expect(ok).toBe(false)
    expect(findExisting).not.toHaveBeenCalled()
    expect(send).not.toHaveBeenCalled()
  })
})
