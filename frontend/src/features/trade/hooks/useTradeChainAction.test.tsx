// 온체인 발송·수령 확인 뒤의 캐시 무효화.
//
// **`confirmReceipt` 가 ERC1155 잔액을 판매자에서 구매자로 옮긴다.** 그러면 보유 소재
// 목록뿐 아니라 소재 상세·보유자별 잔량도 함께 낡는다 — 한쪽만 비우면 소재 상세를 열어 둔
// 사용자는 방금 옮겨진 수량을 반영하지 않은 화면을 계속 본다.
//
// 서명 자체는 지갑의 일이라 트랜잭션 훅 경계에서 자르고, **블록이 확정된 뒤 무엇을
// 비우는지**만 본다 (ARCHITECTURE §6: 무효화는 CONFIRMED 이후).

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { inventoryKeys } from '../../inventory/api/inventoryKeys.ts'
import { materialKeys } from '../../material/api/materialKeys.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import { useTradeChainAction } from './useTradeChainAction.ts'

/**
 * 트랜잭션 훅을 대신한다. `onConfirmed` 를 붙들어 두고 테스트가 직접 부른다 —
 * 실제 서명·블록 확정을 기다릴 방법이 없고, 여기서 볼 것은 그 뒤에 일어나는 일이다.
 */
const tx = vi.hoisted(() => ({ onConfirmed: undefined as (() => void) | undefined }))
vi.mock('../../../hooks/useBlockchainTransaction.ts', () => ({
  useBlockchainTransaction: (options: { onConfirmed?: () => void }) => {
    tx.onConfirmed = options.onConfirmed
    return {
      status: 'IDLE',
      hash: null,
      receipt: null,
      error: null,
      isBusy: false,
      isRejected: false,
      execute: vi.fn(),
      reset: vi.fn(),
    }
  },
}))

// 컨트랙트 호출은 부르지 않지만 import 를 타고 지갑까지 끌려오므로 잘라 둔다.
vi.mock('../../marketplace/services/marketplaceContractService.ts', () => ({
  markShipped: vi.fn(),
  confirmReceipt: vi.fn(),
  cancelOrder: vi.fn(),
}))

afterEach(() => {
  tx.onConfirmed = undefined
  vi.restoreAllMocks()
})

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }

  renderHook(() => useTradeChainAction(7), { wrapper })
  return {
    invalidate,
    /** 지금까지 무효화 요청에 실린 필터들. */
    calls: () => invalidate.mock.calls.map(([filters]) => filters ?? {}),
  }
}

/**
 * 무효화된 키 중에 이 키가 있는가.
 *
 * 호출 인자를 그대로 받는다 — 스파이 타입을 인자로 받으면 TanStack 의 제네릭 시그니처와
 * 어긋나 컴파일이 막힌다.
 */
function invalidated(
  calls: readonly { queryKey?: readonly unknown[] }[],
  key: readonly unknown[],
): boolean {
  return calls.some((filters) => JSON.stringify(filters.queryKey) === JSON.stringify(key))
}

describe('useTradeChainAction', () => {
  // 이 테스트가 이 파일의 존재 이유다.
  it('블록이 확정되면 소재 캐시까지 비운다', () => {
    const { calls } = setup()

    tx.onConfirmed?.()

    // 보유 목록만 비우면 소재 상세·보유자별 잔량이 옛 값으로 남는다.
    expect(invalidated(calls(), inventoryKeys.all)).toBe(true)
    expect(invalidated(calls(), materialKeys.all)).toBe(true)
  })

  it('거래 상세와 체인 주문 상태도 비운다', () => {
    const { calls } = setup()

    tx.onConfirmed?.()

    expect(invalidated(calls(), tradeKeys.detail(7))).toBe(true)
    // 이걸 비우지 않으면 패널이 "아직 밀렸다" 를 staleTime 동안 계속 보여준다.
    expect(invalidated(calls(), ['chain', 'order'])).toBe(true)
  })

  it('확정되기 전에는 아무것도 비우지 않는다', () => {
    const { invalidate } = setup()

    // 서명만 보낸 상태에서 비우면 아직 바뀌지 않은 값을 다시 읽는다 (ARCHITECTURE §6).
    expect(invalidate).not.toHaveBeenCalled()
  })
})
