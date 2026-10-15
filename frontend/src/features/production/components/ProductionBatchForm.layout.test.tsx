// 생산 화면의 모양 — 생산 상세와 같은 틀.
//
// 흐름(한 번 눌러 확정·기록까지)은 ProductionBatchForm.test.tsx 가 지킨다. 그 파일은 소재
// 고르기를 비워 두고 초안만 넘기므로, 고르기와 어우러지는 배치는 여기서 진짜 부품으로 본다.
//
//   - 이름과 개수는 머리 카드에서, 개수는 숫자 칸으로 받는다.
//   - "+ 투입 추가" 는 소재 카드 밖, 카드 바로 위 오른쪽에 선다. 칸이 열린 뒤에만.
//   - 투입 합계는 소재 카드 맨 아래 줄이다. 담기 전에는 더할 것이 없어 줄이 없다.

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

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProductionBatchForm />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getMyMaterials.mockResolvedValue([material()])
  api.getMyWallets.mockResolvedValue([{ walletId: 1, walletAddress: '0xabc', active: true }])
})

afterEach(cleanup)

describe('생산 화면의 모양', () => {
  it('이름 칸, 숫자로 받는 생산 개수, 투입할 소재 구획, 생산 확정 버튼', async () => {
    renderForm()

    expect(await screen.findByLabelText('생산 이름')).toBeInTheDocument()
    expect(screen.getByLabelText('생산 개수')).toHaveAttribute('type', 'number')
    expect(screen.getByRole('heading', { name: '투입할 소재' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '생산 확정' })).toBeInTheDocument()
  })

  // 상세의 "투입된 소재 / + 소재 더 담기" 처럼 카드에 붙어 서되, 카드 안의 줄로 읽히지 않게 밖에 둔다.
  it('소재를 담으면 투입 추가가 카드 밖 위쪽에 서고, 누르면 선택 칸이 다시 펴진다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.selectOptions(await screen.findByLabelText('투입할 소재'), '11')
    const more = screen.getByRole('button', { name: '+ 투입 추가' })
    expect(more.closest('.batch-new__more')).not.toBeNull()
    expect(more.closest('.batch-detail__section')).toBeNull()
    expect(screen.queryByLabelText('투입할 소재')).not.toBeInTheDocument()

    await user.click(more)
    expect(screen.getByLabelText('투입할 소재')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '+ 투입 추가' })).not.toBeInTheDocument()
  })

  it('소재를 담으면 소재 카드 맨 아래에 투입 합계가 선다', async () => {
    const user = userEvent.setup()
    renderForm()

    expect(await screen.findByLabelText('투입할 소재')).toBeInTheDocument()
    expect(screen.queryByText('투입 합계')).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('투입할 소재'), '11')
    await user.type(screen.getByLabelText('제품 사용 (g)'), '500')

    expect(screen.getByText('투입 합계').closest('.batch-detail__section')).not.toBeNull()
    expect(screen.getByText('0.5 kg')).toBeInTheDocument()
    expect(screen.getByText('소재 1건')).toBeInTheDocument()
  })

  it('생산 개수가 없으면 확정하지 않는다', async () => {
    const user = userEvent.setup()
    renderForm()

    await user.type(await screen.findByLabelText('생산 이름'), '가을 토트백 1차')
    await user.selectOptions(screen.getByLabelText('투입할 소재'), '11')
    await user.type(screen.getByLabelText('제품 사용 (g)'), '500')
    await user.click(screen.getByRole('button', { name: '생산 확정' }))

    expect(await screen.findByText('생산 개수를 입력해 주세요.')).toBeInTheDocument()
    expect(api.openProductionBatch).not.toHaveBeenCalled()
  })
})
