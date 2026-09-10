// 배치에 저장된 투입과 사용량.
//
// 틀리면 안 되는 지점 넷.
//
//   1) **합계를 배치 필드에서 읽지 않는다.** totalUsedQuantity 는 확정 전까지 0 이다
//      (confirm() 이 채운다). 그대로 보여주면 "투입했는데 합계 0" 이라는 거짓이 된다.
//   2) **취소 버튼은 DRAFT 에서만.** 확정된 배치는 소재를 이미 소비했고 서버가
//      PRODUCTION_BATCH_NOT_EDITABLE 로 거절한다.
//   3) **제품 사용량이 전부 0 이면 미리 경고한다.** 확정이
//      PRODUCTION_NO_PRODUCT_MATERIAL 로 거부되는데, 눌러 보고 알면 이유를 찾기 어렵다.
//   4) **소재 이름을 보여준다.** 투입 응답에는 materialId 만 있어 보유 목록에서 찾아 붙인다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InventoryItem } from '../../inventory/types/inventory.types.ts'
import type { ProductionBatch, ProductionInput } from '../types/production.types.ts'
import { ProductionInputList } from './ProductionInputList.tsx'

const api = vi.hoisted(() => ({ getMyMaterials: vi.fn(), removeProductionInput: vi.fn() }))
vi.mock('../../inventory/api/getMyMaterials.ts', () => ({ getMyMaterials: api.getMyMaterials }))
vi.mock('../api/removeProductionInput.ts', () => ({
  removeProductionInput: api.removeProductionInput,
}))

function input(overrides: Partial<ProductionInput> = {}): ProductionInput {
  return {
    productionInputId: 51,
    materialId: 11,
    inputQuantity: 10_000,
    productUsedQuantity: 8_000,
    reusableScrapQuantity: 1_500,
    wasteQuantity: 500,
    wasteReason: null,
    materialLockId: 71,
    ...overrides,
  }
}

function batch(overrides: Partial<ProductionBatch> = {}): ProductionBatch {
  return {
    productionBatchId: 3,
    batchCode: 'PB-000003',
    batchName: '2026 가을 토트백',
    productModelId: 31,
    producedQuantity: 10,
    producerId: 5,
    producerWallet: '0x2222222222222222222222222222222222222222',
    // 온체인 서명 전이다 — createBatch 를 알리기 전까지 null 이다.
    chainBatchId: null,
    status: 'DRAFT',
    // 확정 전에는 서버가 전부 0 으로 준다. 화면이 이 값을 쓰면 거짓을 말한다.
    totalUsedQuantity: 0,
    virginUsedQuantity: 0,
    upcycledUsedQuantity: 0,
    upcycleRatio: 0,
    evidenceHash: null,
    manifestUrl: null,
    createdAt: '2026-09-08T10:00:00',
    confirmedAt: null,
    inputs: [input()],
    ...overrides,
  }
}

function material(overrides: Partial<InventoryItem> = {}): InventoryItem {
  return {
    materialId: 11,
    materialCode: 'MAT-000011',
    materialType: 'DENIM',
    rootOrigin: 'WASTE',
    generationType: 'PROCESSED',
    name: '세척된 폐데님',
    grade: 'A',
    status: 'ACTIVE',
    totalQuantity: 100_000,
    quantity: 30_000,
    lockedQuantity: 10_000,
    available: 20_000,
    syncedAt: '2026-09-08T09:00:00',
    ...overrides,
  }
}

function renderList(value: ProductionBatch = batch()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <ProductionInputList batch={value} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

/** 합계 줄(tfoot)을 찾는다. */
function footer(): HTMLElement {
  const row = screen.getByRole('row', { name: /합계/ })
  return row
}

beforeEach(() => {
  api.getMyMaterials.mockResolvedValue([material()])
  api.removeProductionInput.mockResolvedValue(batch({ inputs: [] }))
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('ProductionInputList', () => {
  it('투입 한 건의 사용량 분해를 보여준다', async () => {
    renderList()

    const row = screen.getByRole('row', { name: /세척된 폐데님|소재 #11/ })
    // 투입 10kg = 제품 8kg + 자투리 1.5kg + 폐기 0.5kg
    expect(within(row).getByText('10 kg')).toBeInTheDocument()
    expect(within(row).getByText('8 kg')).toBeInTheDocument()
    expect(within(row).getByText('1.5 kg')).toBeInTheDocument()
    expect(within(row).getByText('0.5 kg')).toBeInTheDocument()
    // 소재 이름은 보유 목록에서 찾아 붙인다.
    expect(await screen.findByText('세척된 폐데님')).toBeInTheDocument()
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('합계를 배치 필드가 아니라 투입에서 계산한다', () => {
    renderList(
      batch({
        // 서버가 주는 값은 0 이다. 이걸 쓰면 화면이 "합계 0" 이라고 거짓을 말한다.
        totalUsedQuantity: 0,
        inputs: [
          input({ productionInputId: 51, inputQuantity: 10_000, productUsedQuantity: 8_000 }),
          input({
            productionInputId: 52,
            materialId: 12,
            inputQuantity: 4_000,
            productUsedQuantity: 4_000,
            reusableScrapQuantity: 0,
            wasteQuantity: 0,
          }),
        ],
      }),
    )

    // 투입 합계 14kg, 제품 사용 12kg
    expect(within(footer()).getByText('14 kg')).toBeInTheDocument()
    expect(within(footer()).getByText('12 kg')).toBeInTheDocument()
  })

  it('투입을 취소하면 그 건을 서버에 보낸다', async () => {
    const user = renderList()

    await user.click(screen.getByRole('button', { name: '취소' }))

    await waitFor(() => expect(api.removeProductionInput).toHaveBeenCalledWith(3, 51))
  })

  it('취소하면 잠긴 수량이 돌아온다는 것을 알린다', () => {
    renderList()

    expect(screen.getByText(/잠긴 수량이 보유 소재로 돌아옵니다/)).toBeInTheDocument()
  })

  it('확정된 배치는 취소할 수 없다', () => {
    renderList(
      batch({
        status: 'CONFIRMED',
        confirmedAt: '2026-09-09T11:00:00',
        totalUsedQuantity: 8_000,
        upcycledUsedQuantity: 8_000,
      }),
    )

    expect(screen.queryByRole('button', { name: '취소' })).not.toBeInTheDocument()
    expect(screen.getByText(/확정된 배치입니다/)).toBeInTheDocument()
  })

  // 확정이 PRODUCTION_NO_PRODUCT_MATERIAL 로 거부되는 조건이다.
  it('제품에 쓰인 수량이 없으면 확정할 수 없다고 미리 알린다', () => {
    renderList(
      batch({
        inputs: [
          input({ productUsedQuantity: 0, reusableScrapQuantity: 9_000, wasteQuantity: 1_000 }),
        ],
      }),
    )

    expect(screen.getByRole('alert')).toHaveTextContent('제품에 쓰인 수량이 없습니다')
  })

  it('제품 사용량이 있으면 경고하지 않는다', () => {
    renderList()

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('폐기 사유를 그 소재 옆에 보여준다', () => {
    renderList(batch({ inputs: [input({ wasteReason: '오염으로 사용 불가' })] }))

    expect(screen.getByText('폐기 사유: 오염으로 사용 불가')).toBeInTheDocument()
  })

  // 확정된 배치의 소재는 소비되어 보유 목록에서 사라진다. 그때도 무엇을 가리키는지는
  // 분명해야 한다.
  it('보유 목록에 없는 소재는 id 로 보여준다', async () => {
    api.getMyMaterials.mockResolvedValue([])
    renderList()

    expect(await screen.findByText('소재 #11')).toBeInTheDocument()
  })

  it('투입이 없으면 무엇을 해야 하는지 알린다', () => {
    renderList(batch({ inputs: [] }))

    expect(screen.getByText('투입한 소재가 없습니다')).toBeInTheDocument()
  })
})
