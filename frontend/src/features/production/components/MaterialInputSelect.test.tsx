// 생산 투입 소재 선택.
//
// 이 화면에서 틀리면 안 되는 지점은 넷이다.
//
//   1) **가용량(available)으로 다룬다.** 보유량(quantity)에는 다른 판매글·가공 요청에 잠긴
//      몫이 섞여 있다. 그걸 기준으로 보여주면 사용자가 쓸 수 없는 수량을 입력한다.
//   2) **폐기물에서 바로 온 소재(WASTE+INITIAL)는 투입 불가.** 가공을 한 번 거쳐야 한다 —
//      막지 않으면 가공 이력이 없는 제품이 업사이클로 팔린다.
//   3) **투입량 = 제품 + 자투리 + 폐기.** 서버가 행마다 검사하고, 저장은 전부-또는-전무다.
//      한 줄이 틀리면 나머지도 저장되지 않으므로 보내기 전에 잡아야 한다.
//   4) **같은 소재를 두 번 넣지 않는다.** 요청 안 중복과 이미 투입된 소재 모두 서버가 거절한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InventoryItem } from '../../inventory/types/inventory.types.ts'
import { MaterialInputSelect } from './MaterialInputSelect.tsx'

const api = vi.hoisted(() => ({ getMyMaterials: vi.fn(), addProductionInputs: vi.fn() }))
vi.mock('../../inventory/api/getMyMaterials.ts', () => ({ getMyMaterials: api.getMyMaterials }))
vi.mock('../api/addProductionInputs.ts', () => ({
  addProductionInputs: api.addProductionInputs,
}))

function material(overrides: Partial<InventoryItem> = {}): InventoryItem {
  return {
    materialId: 11,
    materialCode: 'MAT-000011',
    materialType: 'DENIM',
    // 가공을 거친 소재라 투입 가능하다. WASTE + INITIAL 이면 막힌다.
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

function renderSelect(props: Partial<Parameters<typeof MaterialInputSelect>[0]> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <MaterialInputSelect batchId={3} {...props} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

/** 소재를 골라 초안 한 줄을 만든다. */
async function addDraft(user: ReturnType<typeof userEvent.setup>, materialId = 11) {
  await user.selectOptions(await screen.findByLabelText('투입할 소재'), String(materialId))
  await user.click(screen.getByRole('button', { name: '투입 추가' }))
}

beforeEach(() => {
  api.getMyMaterials.mockResolvedValue([material()])
  api.addProductionInputs.mockResolvedValue({ productionBatchId: 3, inputs: [] })
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('MaterialInputSelect', () => {
  it('보유 소재를 가용량과 함께 보여준다', async () => {
    renderSelect()

    // 보유량 30kg 이 아니라 가용량 20kg 이어야 한다.
    expect(await screen.findByRole('option', { name: /세척된 폐데님 · 가용 20 kg/ })).toBeInTheDocument()
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('폐기물에서 바로 등록한 소재는 고를 수 없고 이유를 적는다', async () => {
    api.getMyMaterials.mockResolvedValue([
      material({ rootOrigin: 'WASTE', generationType: 'INITIAL', name: '수거한 폐데님' }),
    ])
    renderSelect()

    // 이유는 항목 줄이 아니라 묶음 이름이 말한다 — 한 줄에 붙여 적으면 어디까지가 소재
    // 이름인지 읽히지 않는다.
    const option = await screen.findByRole('option', { name: '수거한 폐데님' })
    expect(option).toBeDisabled()
    expect(option.closest('optgroup')).toHaveAttribute('label', '가공을 거쳐야 투입 가능')
  })

  it('가용 수량이 없는 소재는 고를 수 없다', async () => {
    api.getMyMaterials.mockResolvedValue([material({ available: 0, lockedQuantity: 30_000 })])
    renderSelect()

    const option = await screen.findByRole('option', { name: '세척된 폐데님' })
    expect(option).toBeDisabled()
    expect(option.closest('optgroup')).toHaveAttribute('label', '가용 수량 없음')
  })

  it('이미 투입된 소재는 고를 수 없다', async () => {
    renderSelect({ inputMaterialIds: [11] })

    const option = await screen.findByRole('option', { name: '세척된 폐데님' })
    expect(option).toBeDisabled()
    expect(option.closest('optgroup')).toHaveAttribute('label', '이미 투입한 소재')
  })

  // 막되 나무라지 않는다 — 소재를 고르자마자 붉은 글씨가 뜨면 방금 연 칸을 탓하는 꼴이다.
  // 채워야 한다는 것은 빈 칸과 잠긴 저장 버튼이 이미 말한다.
  it('투입량을 입력하기 전에는 저장을 잠그되 경고하지 않는다', async () => {
    const user = renderSelect()
    await addDraft(user)

    expect(screen.getByRole('button', { name: '투입 저장' })).toBeDisabled()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('제품·자투리·폐기의 합을 투입량으로 보낸다', async () => {
    const user = renderSelect()
    await addDraft(user)

    await user.clear(screen.getByLabelText('제품 사용 (g)'))
    await user.type(screen.getByLabelText('제품 사용 (g)'), '8000')
    await user.clear(screen.getByLabelText('자투리 (g)'))
    await user.type(screen.getByLabelText('자투리 (g)'), '1500')
    await user.clear(screen.getByLabelText('폐기 (g)'))
    await user.type(screen.getByLabelText('폐기 (g)'), '500')
    await user.type(screen.getByLabelText('폐기 사유'), '오염으로 사용 불가')

    await user.click(screen.getByRole('button', { name: '투입 저장' }))

    await waitFor(() => expect(api.addProductionInputs).toHaveBeenCalledTimes(1))
    expect(api.addProductionInputs.mock.calls[0]).toEqual([
      3,
      [
        {
          materialId: 11,
          // 8000 + 1500 + 500. 사용자가 따로 입력하지 않는다.
          inputQuantity: 10_000,
          productUsedQuantity: 8_000,
          reusableScrapQuantity: 1_500,
          wasteQuantity: 500,
          wasteReason: '오염으로 사용 불가',
        },
      ],
    ])
  })

  it('가용 수량을 넘으면 저장을 막는다', async () => {
    const user = renderSelect()
    await addDraft(user)

    await user.clear(screen.getByLabelText('제품 사용 (g)'))
    await user.type(screen.getByLabelText('제품 사용 (g)'), '20001') // 가용 20,000g

    expect(screen.getByRole('alert')).toHaveTextContent('가용 수량(20 kg)을 넘을 수 없습니다')
    expect(screen.getByRole('button', { name: '투입 저장' })).toBeDisabled()
    expect(api.addProductionInputs).not.toHaveBeenCalled()
  })

  // 폐기가 0 인데 사유가 붙으면 뜻이 없다.
  it('폐기가 없으면 사유 칸을 만들지 않는다', async () => {
    const user = renderSelect()
    await addDraft(user)

    await user.clear(screen.getByLabelText('제품 사용 (g)'))
    await user.type(screen.getByLabelText('제품 사용 (g)'), '5000')

    expect(screen.queryByLabelText('폐기 사유')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '투입 저장' }))
    await waitFor(() => expect(api.addProductionInputs).toHaveBeenCalledTimes(1))
    expect(api.addProductionInputs.mock.calls[0][1][0]).not.toHaveProperty('wasteReason')
  })

  // 같은 소재를 두 번 넣으면 서버가 PRODUCTION_DUPLICATE_MATERIAL_IN_REQUEST 로 거절한다.
  it('초안에 추가한 소재는 다시 고를 수 없다', async () => {
    const user = renderSelect()
    await addDraft(user)

    const option = screen.getByRole('option', { name: '세척된 폐데님' })
    expect(option).toBeDisabled()
    expect(option.closest('optgroup')).toHaveAttribute('label', '아래에 추가한 소재')
  })

  it('저장에 성공하면 초안을 비우고 결과를 넘긴다', async () => {
    const onSaved = vi.fn()
    const user = renderSelect({ onSaved })
    await addDraft(user)
    await user.clear(screen.getByLabelText('제품 사용 (g)'))
    await user.type(screen.getByLabelText('제품 사용 (g)'), '5000')

    await user.click(screen.getByRole('button', { name: '투입 저장' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    // 저장된 투입이 초안에 남아 있으면 사용자가 같은 소재를 또 보낸다.
    expect(screen.queryByLabelText('제품 사용 (g)')).not.toBeInTheDocument()
  })

  it('보유 소재가 없으면 무엇을 해야 하는지 알린다', async () => {
    api.getMyMaterials.mockResolvedValue([])
    renderSelect()

    expect(await screen.findByText('보유한 소재가 없습니다')).toBeInTheDocument()
  })

  it('투입할 수 있는 소재가 하나도 없으면 이유를 알린다', async () => {
    api.getMyMaterials.mockResolvedValue([
      material({ rootOrigin: 'WASTE', generationType: 'INITIAL' }),
    ])
    renderSelect()

    expect(await screen.findByText('지금 투입할 수 있는 소재가 없습니다.')).toBeInTheDocument()
  })
})
