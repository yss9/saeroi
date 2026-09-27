// 생산 시작 — 한 화면에서 끝까지.
//
// 이름·개수·소재를 적고 **한 번 누르면** 배치를 열고, 소재를 담고, 확정과 체인 기록까지
// 진행한다. 예전에는 여기서 배치만 열고 상세 화면에서 같은 내용을 다시 읽은 뒤 확정했다.
//
// 틀리면 안 되는 지점.
//
//   1) 무엇이 일어나는지(소비·생산 개수·되돌릴 수 없음)를 누르기 전에 적는다.
//   2) 소재가 없거나 어긋나면 누를 수 없다 — 확정은 투입 없이 되지 않는다.
//   3) 배치를 연 뒤에 멈추면 **이 화면에서 다시 누르게 하지 않는다.** 다시 누르면 배치가 하나
//      더 열린다. 상세로 보내 이유를 적고, 거기서 남은 단계부터 잇는다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InventoryItem } from '../../inventory/types/inventory.types.ts'
import type { ProductionBatch } from '../types/production.types.ts'
import type { DraftInput } from '../utils/productionInputDraft.ts'
import { ProductionBatchForm } from './ProductionBatchForm.tsx'

const api = vi.hoisted(() => ({
  openProductionBatch: vi.fn(),
  addProductionInputs: vi.fn(),
  getMyWallets: vi.fn(),
  runProductionFlow: vi.fn(),
}))
vi.mock('../api/productionBatchApi.ts', () => ({ openProductionBatch: api.openProductionBatch }))
vi.mock('../api/addProductionInputs.ts', () => ({ addProductionInputs: api.addProductionInputs }))
vi.mock('../../wallet/api/walletRegistrationApi.ts', () => ({ getMyWallets: api.getMyWallets }))
vi.mock('../services/productionFlow.ts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/productionFlow.ts')>()),
  runProductionFlow: api.runProductionFlow,
}))
// 소재 고르기는 따로 검증된 부품이다. 여기서는 고른 결과(초안)만 넘겨받는다.
vi.mock('./MaterialInputSelect.tsx', () => ({ MaterialInputPicker: () => null }))

function batch(overrides: Partial<ProductionBatch> = {}): ProductionBatch {
  return {
    productionBatchId: 3,
    batchName: '토트백 1차',
    producedQuantity: 10,
    producerWallet: '0x2222222222222222222222222222222222222222',
    chainBatchId: null,
    status: 'DRAFT',
    inputs: [],
    ...overrides,
  } as ProductionBatch
}

const material = { materialId: 11, available: 20_000 } as InventoryItem
const draft: DraftInput = { material, productUsed: 8_000, reusableScrap: 1_500, waste: 500, wasteReason: '' }

function renderForm(drafts: DraftInput[] = [draft], onDone = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ProductionBatchForm initial={{ batchName: '토트백 1차', producedQuantity: 10, drafts }} onDone={onDone} />
    </QueryClientProvider>,
  )
  return onDone
}

describe('ProductionBatchForm', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.getMyWallets.mockResolvedValue([{ active: true, walletAddress: '0x2222222222222222222222222222222222222222' }])
    api.openProductionBatch.mockResolvedValue(batch())
    api.addProductionInputs.mockResolvedValue(batch({ inputs: [{ productionInputId: 51 }] as ProductionBatch['inputs'] }))
    api.runProductionFlow.mockResolvedValue(undefined)
  })
  afterEach(cleanup)

  it('누르기 전에 무엇이 소비되고 몇 개가 만들어지는지 적는다', async () => {
    renderForm()

    expect(await screen.findByText(/투입한 8 kg이 소비되고 제품 10개가 만들어집니다/)).toBeTruthy()
    expect(screen.getByText('되돌릴 수 없습니다.')).toBeTruthy()
  })

  it('한 번 누르면 배치를 열고 소재를 담고 확정·기록까지 진행한다', async () => {
    const onDone = renderForm()

    await userEvent.click(await screen.findByRole('button', { name: '생산 확정' }))

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(api.openProductionBatch).toHaveBeenCalledWith(
      expect.objectContaining({ batchName: '토트백 1차', producedQuantity: 10 }),
      expect.anything(),
    )
    expect(api.addProductionInputs).toHaveBeenCalledWith(3, [expect.objectContaining({ materialId: 11, inputQuantity: 10_000 })])
    // 소재까지 담긴 배치로 진행한다 — 빈 배치로는 서버가 확정을 거절한다.
    expect(api.runProductionFlow).toHaveBeenCalledWith(
      expect.objectContaining({ productionBatchId: 3, inputs: [{ productionInputId: 51 }] }),
      expect.any(Function),
    )
    expect(onDone).toHaveBeenCalledWith(expect.objectContaining({ productionBatchId: 3 }), null)
  })

  it('진행하는 동안 단계를 보여준다', async () => {
    let finish: () => void = () => {}
    api.runProductionFlow.mockImplementation((_batch, onStep: (step: string) => void) => {
      onStep('CREATE_BATCH')
      return new Promise<void>((resolve) => { finish = resolve })
    })
    renderForm()

    await userEvent.click(await screen.findByRole('button', { name: '생산 확정' }))

    const progress = await screen.findByRole('list', { name: '생산 기록 진행 상황' })
    expect(progress.textContent).toContain('진행 중')
    finish()
  })

  it('배치를 연 뒤 멈추면 이유와 함께 상세로 넘긴다 — 여기서 다시 누르면 배치가 또 열린다', async () => {
    api.runProductionFlow.mockRejectedValue(Object.assign(new Error('user rejected action'), { code: 'ACTION_REJECTED' }))
    const onDone = renderForm()

    await userEvent.click(await screen.findByRole('button', { name: '생산 확정' }))

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(onDone).toHaveBeenCalledWith(
      expect.objectContaining({ productionBatchId: 3 }),
      expect.stringContaining('서명을 취소했어요'),
    )
  })

  it('소재를 담지 못하면 확정하지 않고 이유와 함께 상세로 넘긴다', async () => {
    api.addProductionInputs.mockRejectedValue(new Error('가용 수량이 부족합니다.'))
    const onDone = renderForm()

    await userEvent.click(await screen.findByRole('button', { name: '생산 확정' }))

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(api.runProductionFlow).not.toHaveBeenCalled()
    expect(onDone).toHaveBeenCalledWith(expect.objectContaining({ productionBatchId: 3 }), '가용 수량이 부족합니다.')
  })

  it('소재를 고르지 않으면 누를 수 없다', async () => {
    renderForm([])

    const button = await screen.findByRole('button', { name: '생산 확정' })
    expect((button as HTMLButtonElement).disabled).toBe(true)
  })
})
