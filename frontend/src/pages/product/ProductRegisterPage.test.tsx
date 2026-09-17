// 제품 등록.
//
// 이 화면이 잇는 것은 두 가지다 — 제품을 만들고, 그 제품을 생산 배치에 붙인다. 붙이지
// 않으면 배치를 확정할 수 없고(서버가 PRODUCTION_PRODUCT_MODEL_REQUIRED 로 거절한다),
// 확정하지 못하면 개체도 QR 도 재고도 없다.
//
// 순서가 뒤집혀 있다는 점이 핵심이다. 배치에 적어 넣을 제품 번호는 **등록 응답에서** 나오므로
// 폼에서는 고르기만 하고 연결은 등록 직후에 한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProductModel } from '../../features/product/types/product.types.ts'
import type { ProductionBatch } from '../../features/production/types/production.types.ts'
import { ProductRegisterPage } from './ProductRegisterPage.tsx'

const api = vi.hoisted(() => ({
  getMyProductionBatches: vi.fn(),
  assignProductModel: vi.fn(),
}))
vi.mock('../../features/production/api/productionBatchApi.ts', () => ({
  getMyProductionBatches: api.getMyProductionBatches,
  assignProductModel: api.assignProductModel,
}))

// 폼 자체(이미지 업로드·검증)는 ProductForm 테스트가 지킨다. 여기서는 등록이 끝났을 때
// 무슨 일이 일어나는지만 본다.
const form = vi.hoisted(() => ({ created: null as ((product: ProductModel) => void) | null }))
vi.mock('../../features/product/components/ProductForm.tsx', () => ({
  ProductCreatedSummary: ({ product }: { product: ProductModel }) => <p>{product.name} 등록됨</p>,
  ProductForm: ({
    extraFields,
    onCreated,
  }: {
    extraFields?: React.ReactNode
    onCreated?: (product: ProductModel) => void
  }) => {
    form.created = onCreated ?? null
    return (
      <form>
        {extraFields}
        <button
          type="button"
          onClick={() => onCreated?.({ productModelId: 31, name: '토트백' } as ProductModel)}
        >
          제품 등록
        </button>
      </form>
    )
  },
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

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProductRegisterPage />
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

describe('ProductRegisterPage', () => {
  it('고른 배치에 방금 등록한 제품을 잇는다', async () => {
    api.assignProductModel.mockResolvedValue(batch({ productModelId: 31 }))
    const user = renderPage()

    await user.selectOptions(await screen.findByLabelText('생산 배치'), '3')
    await user.click(screen.getByRole('button', { name: '제품 등록' }))

    await waitFor(() =>
      expect(api.assignProductModel).toHaveBeenCalledWith(3, { productModelId: 31 }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent('2026 가을 1차')
  })

  // 배치는 나중에 이어도 된다. 고르지 않았다고 등록을 막지 않는다.
  it('배치를 고르지 않으면 연결하지 않는다', async () => {
    const user = renderPage()

    await screen.findByLabelText('생산 배치')
    await user.click(screen.getByRole('button', { name: '제품 등록' }))

    expect(await screen.findByText('토트백 등록됨')).toBeInTheDocument()
    expect(api.assignProductModel).not.toHaveBeenCalled()
  })

  // 확정된 배치는 이미 개체를 만들었고, 제품이 붙은 배치를 고르면 그 제품의 재고를 빼앗는다.
  // 그래도 칸은 남긴다 — 칸이 사라지면 "제품은 배치에 붙는다" 는 사실까지 화면에서 없어진다.
  it('이을 배치가 없으면 칸을 잠그고 이유를 적는다', async () => {
    api.getMyProductionBatches.mockResolvedValue([
      batch({ productionBatchId: 4, status: 'CONFIRMED' }),
      batch({ productionBatchId: 5, productModelId: 99 }),
    ])
    renderPage()

    expect(await screen.findByLabelText('생산 배치')).toBeDisabled()
    expect(screen.getByText(/배치가 없습니다/)).toBeInTheDocument()
  })
})
