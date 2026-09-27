// 새 생산 — 생산 상세와 같은 틀.
//
// 시작 전과 후가 같은 자리에서 같은 모양이어야 한다. 이름은 제목 자리에서, 생산 개수는
// 상세가 숫자를 보여 주는 첫 칸에서 받는다. 투입 합계와 건수는 담은 소재를 따라 바뀐다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InventoryItem } from '../../inventory/types/inventory.types.ts'
import { ProductionBatchForm } from './ProductionBatchForm.tsx'

const api = vi.hoisted(() => ({
  openProductionBatch: vi.fn(),
  addProductionInputs: vi.fn(),
  getMyMaterials: vi.fn(),
  getMyWallets: vi.fn(),
}))
vi.mock('../api/productionBatchApi.ts', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  openProductionBatch: api.openProductionBatch,
}))
vi.mock('../api/addProductionInputs.ts', () => ({ addProductionInputs: api.addProductionInputs }))
vi.mock('../../inventory/api/getMyMaterials.ts', () => ({ getMyMaterials: api.getMyMaterials }))
vi.mock('../../wallet/api/walletRegistrationApi.ts', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  getMyWallets: api.getMyWallets,
}))

function material(overrides: Partial<InventoryItem> = {}): InventoryItem {
  return {
    materialId: 11,
    materialCode: 'MAT-000011',
    materialType: 'DENIM',
    rootOrigin: 'WASTE',
    generationType: 'PROCESSED',
    name: '정제된 폐데님',
    grade: 'A',
    status: 'ACTIVE',
    totalQuantity: 100_000,
    quantity: 30_000,
    lockedQuantity: 0,
    available: 30_000,
    syncedAt: '2026-09-28T09:00:00',
    completedProcessTypes: ['CLEANING', 'DISASSEMBLY', 'SORTING'],
    ...overrides,
  }
}

function renderForm(onOpened = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProductionBatchForm onOpened={onOpened} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return onOpened
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getMyMaterials.mockResolvedValue([material()])
  api.getMyWallets.mockResolvedValue([{ walletId: 1, address: '0xabc', active: true }])
  api.openProductionBatch.mockResolvedValue({
    productionBatchId: 66,
    batchName: '가을 토트백 1차',
    producedQuantity: 20,
    status: 'DRAFT',
    inputs: [],
  })
})

afterEach(cleanup)

describe('새 생산', () => {
  it('생산 상세와 같은 틀 — 이름 칸, 투입할 소재 카드, 생산 시작 버튼', async () => {
    renderForm()

    expect(await screen.findByLabelText('생산 이름')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '투입할 소재' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '생산 시작' })).toBeInTheDocument()
  })

  // 상세가 "생산 개수" 를 보여 주는 바로 그 자리에서 받는다.
  it('생산 개수를 숫자 칸에서 받는다', async () => {
    renderForm()

    const quantity = await screen.findByLabelText('생산 개수')
    expect(quantity).toHaveAttribute('type', 'number')
  })

  // 담은 것의 합계는 담은 칸의 맨 아래에 선다 — 담기 전에는 더할 것이 없어 줄이 없다.
  it('소재를 담으면 소재 카드 맨 아래에 투입 합계가 선다', async () => {
    const user = userEvent.setup()
    renderForm()

    expect(await screen.findByLabelText('투입할 소재')).toBeInTheDocument()
    expect(screen.queryByText('투입 합계')).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('투입할 소재'), '11')
    await user.type(screen.getByLabelText('제품 사용 (g)'), '500')

    expect(screen.getByText('투입 합계')).toBeInTheDocument()
    expect(screen.getByText('0.5 kg')).toBeInTheDocument()
    expect(screen.getByText('소재 1건')).toBeInTheDocument()
  })

  it('이름과 개수로 생산을 열고 상세로 넘긴다', async () => {
    const user = userEvent.setup()
    const onOpened = renderForm()

    await user.type(await screen.findByLabelText('생산 이름'), '가을 토트백 1차')
    await user.type(screen.getByLabelText('생산 개수'), '20')
    await user.click(screen.getByRole('button', { name: '생산 시작' }))

    expect(api.openProductionBatch).toHaveBeenCalledWith(
      { batchName: '가을 토트백 1차', producedQuantity: 20 },
      expect.anything(),
    )
    expect(onOpened).toHaveBeenCalledWith(expect.objectContaining({ productionBatchId: 66 }))
  })

  // 상세의 "투입된 소재 / + 소재 더 담기" 처럼 제목 줄 오른쪽에 선다.
  it('소재를 담으면 투입 추가가 카드 위쪽 오른쪽에 서고, 누르면 선택 칸이 다시 펴진다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.selectOptions(await screen.findByLabelText('투입할 소재'), '11')
    const more = screen.getByRole('button', { name: '+ 투입 추가' })
    expect(more.closest('.batch-new__more')).not.toBeNull()
    expect(screen.queryByLabelText('투입할 소재')).not.toBeInTheDocument()

    await user.click(more)
    expect(screen.getByLabelText('투입할 소재')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '+ 투입 추가' })).not.toBeInTheDocument()
  })

  it('생산 개수가 없으면 열지 않는다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(await screen.findByLabelText('생산 이름'), '가을 토트백 1차')
    await user.click(screen.getByRole('button', { name: '생산 시작' }))

    expect(await screen.findByText('생산 개수를 입력해 주세요.')).toBeInTheDocument()
    expect(api.openProductionBatch).not.toHaveBeenCalled()
  })
})
