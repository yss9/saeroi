// 제품 ↔ 생산 배치 연결.
//
// 이 연결이 없으면 배치를 확정해도 만들어진 개체가 아무 상품에도 속하지 않는다 — QR 을
// 찍어도 나올 제품이 없고 쇼핑몰 재고도 오르지 않는다. 그래서 고를 수 있는 배치를 정확히
// 추리는 것이 이 컴포넌트의 전부다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProductionBatch } from '../types/production.types.ts'
import { ProductionBatchLinkPanel } from './ProductionBatchLinkPanel.tsx'

const api = vi.hoisted(() => ({
  getMyProductionBatches: vi.fn(),
  assignProductModel: vi.fn(),
}))
vi.mock('../api/productionBatchApi.ts', () => ({
  getMyProductionBatches: api.getMyProductionBatches,
  assignProductModel: api.assignProductModel,
}))

function batch(overrides: Partial<ProductionBatch> = {}): ProductionBatch {
  return {
    productionBatchId: 3,
    batchCode: 'PB-000003',
    batchName: '2026 가을 1차',
    productModelId: null,
    producedQuantity: 10,
    producerId: 5,
    producerWallet: '0x2222222222222222222222222222222222222222',
    chainBatchId: null,
    status: 'DRAFT',
    totalUsedQuantity: 0,
    virginUsedQuantity: 0,
    upcycledUsedQuantity: 0,
    upcycleRatio: 0,
    evidenceHash: null,
    manifestUrl: null,
    createdAt: '2026-09-15T10:00:00',
    confirmedAt: null,
    inputs: [],
    ...overrides,
  }
}

function renderPanel() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProductionBatchLinkPanel productModelId={31} productName="업사이클 데님 토트백" />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  api.getMyProductionBatches.mockResolvedValue([batch()])
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('ProductionBatchLinkPanel', () => {
  it('고른 배치에 방금 등록한 제품을 연결한다', async () => {
    api.assignProductModel.mockResolvedValue(batch({ productModelId: 31 }))
    const user = renderPanel()

    await user.selectOptions(await screen.findByLabelText('생산 배치'), '3')
    await user.click(screen.getByRole('button', { name: '배치에 연결' }))

    await waitFor(() =>
      expect(api.assignProductModel).toHaveBeenCalledWith(3, { productModelId: 31 }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent('2026 가을 1차')
  })

  // 확정된 배치는 이미 개체를 만들어 버려 바꿀 수 없고(서버도 DRAFT 만 받는다), 이미
  // 제품이 붙은 배치를 고르면 남의 제품 재고를 빼앗는 꼴이 된다.
  it('확정됐거나 이미 제품이 붙은 배치는 후보에서 뺀다', async () => {
    api.getMyProductionBatches.mockResolvedValue([
      batch({ productionBatchId: 4, batchName: '확정된 배치', status: 'CONFIRMED' }),
      batch({ productionBatchId: 5, batchName: '이미 붙은 배치', productModelId: 99 }),
    ])
    renderPanel()

    expect(await screen.findByText(/연결할 배치가 없습니다/)).toBeInTheDocument()
    expect(screen.queryByLabelText('생산 배치')).not.toBeInTheDocument()
  })

  // 고르기 전에 누르면 어느 배치에 붙일지 서버가 알 수 없다.
  it('배치를 고르기 전에는 연결 버튼을 잠근다', async () => {
    renderPanel()

    expect(await screen.findByRole('button', { name: '배치에 연결' })).toBeDisabled()
  })
})
