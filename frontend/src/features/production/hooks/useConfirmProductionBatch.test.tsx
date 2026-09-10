// 배치 확정의 캐시 처리.
//
// **확정 응답은 투입을 담지 않는다** — 서버가 `ProductionBatchResponse.of(batch, List.of())`
// 로 만든다. 투입이 지워진 것이 아니라 확정 서비스가 목록을 다시 만들지 않을 뿐이다.
//
// 그 응답을 상세 캐시에 그대로 넣으면 방금 확정한 화면에서 투입이 사라진다. 화면 테스트로는
// 잡히지 않는다 — 이어지는 재조회가 서버 값을 다시 채워 넣어 흔적이 지워지기 때문이다.
// 그래서 캐시를 직접 본다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { productionKeys } from '../api/productionKeys.ts'
import type { ProductionBatch } from '../types/production.types.ts'
import { useConfirmProductionBatch } from './useConfirmProductionBatch.ts'

const api = vi.hoisted(() => ({ confirmProductionBatch: vi.fn() }))
vi.mock('../api/productionBatchApi.ts', () => ({
  confirmProductionBatch: api.confirmProductionBatch,
}))

const INPUT = {
  productionInputId: 51,
  materialId: 11,
  inputQuantity: 10_000,
  productUsedQuantity: 8_000,
  reusableScrapQuantity: 1_500,
  wasteQuantity: 500,
  wasteReason: null,
  materialLockId: 71,
}

function batch(overrides: Partial<ProductionBatch> = {}): ProductionBatch {
  return {
    productionBatchId: 3,
    batchCode: 'PB-000003',
    batchName: '2026 가을 토트백 1차',
    productModelId: 31,
    producedQuantity: 10,
    producerId: 5,
    producerWallet: '0x2222222222222222222222222222222222222222',
    // 온체인 서명 전이다 — createBatch 를 알리기 전까지 null 이다.
    chainBatchId: null,
    status: 'DRAFT',
    totalUsedQuantity: 0,
    virginUsedQuantity: 0,
    upcycledUsedQuantity: 0,
    upcycleRatio: 0,
    evidenceHash: null,
    manifestUrl: null,
    createdAt: '2026-09-09T10:00:00',
    confirmedAt: null,
    inputs: [INPUT],
    ...overrides,
  }
}

afterEach(() => vi.resetAllMocks())

describe('useConfirmProductionBatch', () => {
  it('확정 응답이 투입을 담지 않아도 캐시의 투입을 지우지 않는다', async () => {
    // 서버가 실제로 이렇게 준다 — 상태는 CONFIRMED, 투입은 빈 배열.
    api.confirmProductionBatch.mockResolvedValue(
      batch({
        status: 'CONFIRMED',
        confirmedAt: '2026-09-09T12:00:00',
        totalUsedQuantity: 8_000,
        upcycledUsedQuantity: 8_000,
        upcycleRatio: 100,
        inputs: [],
      }),
    )

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    // 확정 직전의 화면 상태 — 투입이 담긴 DRAFT 배치.
    queryClient.setQueryData(productionKeys.detail(3), batch())

    function wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    }

    const { result } = renderHook(() => useConfirmProductionBatch(3), { wrapper })
    result.current.mutate()

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    const cached = queryClient.getQueryData<ProductionBatch>(productionKeys.detail(3))
    // 배치 값은 응답으로 갈아 끼운다.
    expect(cached?.status).toBe('CONFIRMED')
    expect(cached?.upcycleRatio).toBe(100)
    // 투입은 유지한다. 그러지 않으면 "투입한 소재가 없습니다" 가 잠깐 보인다.
    expect(cached?.inputs).toEqual([INPUT])
  })

  it('캐시가 비어 있으면 응답을 그대로 심는다', async () => {
    api.confirmProductionBatch.mockResolvedValue(batch({ status: 'CONFIRMED', inputs: [] }))

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })

    function wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    }

    const { result } = renderHook(() => useConfirmProductionBatch(3), { wrapper })
    result.current.mutate()

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    // 병합할 대상이 없는 경우다. 이때 빈 배열은 사실이므로 그대로 둔다 — 이어지는
    // 재조회가 서버 값으로 채운다.
    expect(queryClient.getQueryData<ProductionBatch>(productionKeys.detail(3))?.status).toBe(
      'CONFIRMED',
    )
  })
})
