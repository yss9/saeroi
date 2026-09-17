// 제품 등록.
//
// 이 화면이 잇는 것은 두 가지다 — 제품을 만들고, 그 제품을 생산 배치에 붙인다. 붙이지
// 않으면 배치를 확정할 수 없고(서버가 PRODUCTION_PRODUCT_MODEL_REQUIRED 로 거절한다),
// 확정하지 못하면 개체도 QR 도 재고도 없다.
//
// 순서가 뒤집혀 있다는 점이 핵심이다. 배치에 적어 넣을 제품 번호는 **등록 응답에서** 나오므로
// 배치를 먼저 고르더라도 연결은 등록 직후에 한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProductModel } from '../../features/product/types/product.types.ts'
import type { ProductionBatch } from '../../features/production/types/production.types.ts'
import { ProductRegisterPage } from './ProductRegisterPage.tsx'

const api = vi.hoisted(() => ({
  getMyProductionBatches: vi.fn(),
  assignProductModel: vi.fn(),
  changeProductModelStatus: vi.fn(),
}))
vi.mock('../../features/production/api/productionBatchApi.ts', () => ({
  getMyProductionBatches: api.getMyProductionBatches,
  assignProductModel: api.assignProductModel,
}))
vi.mock('../../features/product/api/changeProductModelStatus.ts', () => ({
  changeProductModelStatus: api.changeProductModelStatus,
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

/** 목록 안의 "제품 등록" 버튼. 창 안의 제출 버튼과 이름이 같아 목록으로 좁힌다. */
function openButton() {
  return within(screen.getByRole('list')).getByRole('button', { name: '제품 등록' })
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
  // 배치를 누르기 전에는 폼이 없다. 목록과 폼을 한 화면에 쌓으면 어느 배치에 적는지가
  // 위로 밀려 올라간다.
  it('오른쪽 버튼을 눌러야 등록 창이 열린다', async () => {
    const user = renderPage()

    // 칸은 상세로 가는 링크고, 등록 창은 오른쪽 버튼이 연다.
    const link = await screen.findByRole('link', { name: /2026 가을 1차/ })
    expect(link).toHaveAttribute('href', '/brand/production/3')

    await user.click(openButton())

    // 창 제목이 어느 생산에 적는지 말한다.
    const dialog = within(screen.getByRole('dialog'))
    expect(dialog.getByRole('heading', { name: /2026 가을 1차/ })).toBeInTheDocument()
    expect(dialog.getByRole('button', { name: '제품 등록' })).toBeVisible()
  })

  it('등록을 마치면 그 생산에 잇는다', async () => {
    api.assignProductModel.mockResolvedValue(batch({ productModelId: 31 }))
    const user = renderPage()

    await screen.findByRole('list')
    await user.click(openButton())
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '제품 등록' }))

    await waitFor(() =>
      expect(api.assignProductModel).toHaveBeenCalledWith(3, { productModelId: 31 }),
    )
    expect(await screen.findByText('토트백 등록됨')).toBeInTheDocument()
  })

  // 확정된 생산의 개체가 곧 이 제품의 재고다 — 등록하자마자 파는 것이 하려던 일이다.
  it('확정된 생산에 붙이면 곧바로 판매를 시작한다', async () => {
    api.getMyProductionBatches.mockResolvedValue([batch({ status: 'CONFIRMED' })])
    api.assignProductModel.mockResolvedValue(batch({ status: 'CONFIRMED', productModelId: 31 }))
    api.changeProductModelStatus.mockResolvedValue({ productModelId: 31, name: '토트백' })
    const user = renderPage()

    await screen.findByRole('list')
    await user.click(openButton())
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '제품 등록' }))

    await waitFor(() => expect(api.changeProductModelStatus).toHaveBeenCalledWith(31, 'ON_SALE'))
  })

  // 작성 중인 생산은 개체가 없어 재고가 0 이다 — 서버가 PRODUCT_MODEL_HAS_NO_STOCK 로
  // 거절할 요청을 보내지 않는다.
  it('작성 중인 생산이면 판매 시작을 보내지 않는다', async () => {
    api.assignProductModel.mockResolvedValue(batch({ productModelId: 31 }))
    const user = renderPage()

    await screen.findByRole('list')
    await user.click(openButton())
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '제품 등록' }))

    await waitFor(() => expect(api.assignProductModel).toHaveBeenCalled())
    expect(api.changeProductModelStatus).not.toHaveBeenCalled()
  })

  // 생산 없이 올린 제품은 재고가 0 이라 팔 수 없고, 결국 생산으로 돌아와 이어야 한다.
  it('생산을 고르지 않고 등록하는 길은 없다', async () => {
    renderPage()

    await screen.findByRole('list')
    expect(screen.queryByRole('button', { name: '생산 연결 없이 등록' })).not.toBeInTheDocument()
  })

  // 서버가 순서를 정해 주지 않는다 — 그대로 그리면 방금 확정한 생산이 목록 맨 아래에 있고,
  // 제품을 붙이러 온 사람이 매번 끝까지 내려가야 한다.
  it('고를 수 있는 생산을 최신순으로 앞에 두고 잠긴 것은 번호순으로 뒤에 둔다', async () => {
    api.getMyProductionBatches.mockResolvedValue([
      batch({ productionBatchId: 1, batchName: '오래된 생산', createdAt: '2026-09-01T10:00:00' }),
      batch({ productionBatchId: 9, batchName: '잠긴 생산 B', productModelId: 99 }),
      batch({ productionBatchId: 7, batchName: '방금 생산', createdAt: '2026-09-17T10:00:00' }),
      batch({ productionBatchId: 4, batchName: '잠긴 생산 A', productModelId: 98 }),
    ])
    renderPage()

    await screen.findByText('방금 생산')
    const rows = within(screen.getByRole('list')).getAllByRole('listitem')
    expect(rows.map((row) => row.querySelector('strong')?.textContent)).toEqual([
      '방금 생산',
      '오래된 생산',
      '잠긴 생산 A',
      '잠긴 생산 B',
    ])
  })

  // 확정된 생산이 오히려 정상 경로다 — 생산을 끝내고 그 결과물에 제품을 붙인다.
  // 서버가 그때 이미 만들어진 개체의 제품까지 함께 채운다.
  it('확정된 생산에도 제품을 붙일 수 있다', async () => {
    api.getMyProductionBatches.mockResolvedValue([
      batch({ productionBatchId: 4, batchName: '확정된 생산', status: 'CONFIRMED' }),
    ])
    renderPage()

    expect(
      await within(await screen.findByRole('list')).findByRole('button', { name: '제품 등록' }),
    ).toBeEnabled()
  })

  // 고를 수 없는 생산도 지우지 않는다. 빼버리면 "내 생산이 사라졌다" 로 읽힌다.
  it('이을 수 없는 생산은 잠그고 이유를 적는다', async () => {
    api.getMyProductionBatches.mockResolvedValue([
      batch({ productionBatchId: 4, batchName: '서명 중 생산', status: 'SIGNING' }),
      batch({ productionBatchId: 5, batchName: '붙은 생산', productModelId: 99 }),
    ])
    renderPage()

    expect(await screen.findByText('서명이 끝난 뒤에 연결할 수 있습니다')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '제품 등록' })).not.toBeInTheDocument()
    expect(screen.getByText('이미 제품이 연결된 생산입니다')).toBeInTheDocument()
  })
})
